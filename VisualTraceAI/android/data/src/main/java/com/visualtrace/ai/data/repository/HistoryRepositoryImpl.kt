package com.visualtrace.ai.data.repository

import android.content.Context
import android.util.Base64
import com.visualtrace.ai.data.local.CachedResultsEntity
import com.visualtrace.ai.data.local.HistoryDao
import com.visualtrace.ai.data.local.HistoryEntity
import com.visualtrace.ai.data.local.PendingDeletionDao
import com.visualtrace.ai.data.local.PendingDeletionEntity
import com.visualtrace.ai.data.remote.ApiJson
import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.remote.apiUnit
import com.visualtrace.ai.data.remote.dto.ResultsPageDto
import com.visualtrace.ai.data.remote.toDomain
import com.visualtrace.ai.data.remote.toDto
import com.visualtrace.ai.data.work.WorkScheduler
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.HistoryEntry
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ResultsPage
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.model.SearchStatus
import com.visualtrace.ai.domain.model.SearchType
import com.visualtrace.ai.domain.repository.HistoryRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

/** Search history is stored locally by default; the server copy is deleted alongside. */
@Singleton
class HistoryRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dao: HistoryDao,
    private val pending: PendingDeletionDao,
    private val api: VisualTraceApi,
    private val scheduler: Provider<WorkScheduler>,
) : HistoryRepository {

    private val thumbsDir get() = File(context.filesDir, "thumbnails").apply { mkdirs() }

    override fun observe(type: SearchType?): Flow<List<HistoryEntry>> =
        dao.observe(type?.name).map { list -> list.map { it.toDomain() } }

    override suspend fun record(session: SearchSession) = withContext(Dispatchers.IO) {
        val thumbPath = session.previewThumbnailBase64?.let { b64 ->
            runCatching {
                val file = File(thumbsDir, "${session.id}.jpg")
                file.writeBytes(Base64.decode(b64, Base64.DEFAULT))
                file.absolutePath
            }.getOrNull()
        }
        val title = session.queryText ?: session.originalFilename
        dao.upsert(HistoryEntity(session.id, session.type.name, session.status.name, session.createdAtEpochMs,
            session.resultCount, title?.take(200), thumbPath))
    }

    override suspend fun delete(searchId: String): Outcome<Unit> {
        withContext(Dispatchers.IO) {
            dao.get(searchId)?.thumbnailPath?.let { File(it).delete() }
            dao.delete(searchId)
            dao.deleteCachedResults(searchId)
        }
        return when (val r = apiUnit { api.deleteHistory(searchId) }) {
            is Outcome.Success -> r
            is Outcome.Failure -> if (r.error is AppError.NotFound) Outcome.Success(Unit) else {
                pending.add(PendingDeletionEntity("history:$searchId", System.currentTimeMillis()))
                scheduler.get().schedulePendingDeletions()
                Outcome.Success(Unit) // deleted locally; the server copy is removed when back online
            }
        }
    }

    override suspend fun clear(): Outcome<Unit> {
        withContext(Dispatchers.IO) {
            dao.thumbnailPaths().forEach { File(it).delete() }
            dao.clear()
            dao.clearCachedResults()
        }
        val r = apiUnit { api.clearHistory() }
        if (r is Outcome.Failure) {
            pending.add(PendingDeletionEntity("history:*", System.currentTimeMillis()))
            scheduler.get().schedulePendingDeletions()
        }
        return Outcome.Success(Unit)
    }

    override suspend fun cachedResults(searchId: String): ResultsPage? = withContext(Dispatchers.IO) {
        dao.cachedResults(searchId)?.let { runCatching { ApiJson.decodeFromString(ResultsPageDto.serializer(), it.json).toDomain() }.getOrNull() }
    }

    override suspend fun cacheResults(page: ResultsPage) = withContext(Dispatchers.IO) {
        if (dao.get(page.searchId) == null) return@withContext // history disabled or entry deleted
        dao.cacheResults(CachedResultsEntity(page.searchId, ApiJson.encodeToString(ResultsPageDto.serializer(), page.toDto()),
            System.currentTimeMillis()))
    }

    /** Called by the pending deletion worker. Returns true when everything was processed. */
    suspend fun flushPendingDeletions(): Boolean {
        var ok = true
        for (item in pending.all()) {
            val (kind, id) = item.key.split(":", limit = 2).let { it[0] to it[1] }
            val r = when {
                kind == "history" && id == "*" -> apiUnit { api.clearHistory() }
                kind == "history" -> apiUnit { api.deleteHistory(id) }
                kind == "favorite" -> apiUnit { api.deleteFavorite(id) }
                else -> Outcome.Success(Unit)
            }
            if (r is Outcome.Success || (r as Outcome.Failure).error is AppError.NotFound) pending.remove(item.key) else ok = false
        }
        return ok
    }
}

private fun HistoryEntity.toDomain() = HistoryEntry(
    searchId = searchId,
    type = runCatching { SearchType.valueOf(type) }.getOrDefault(SearchType.IMAGE),
    status = SearchStatus.from(status),
    createdAtEpochMs = createdAt,
    resultCount = resultCount,
    title = title,
    thumbnailPath = thumbnailPath,
)
