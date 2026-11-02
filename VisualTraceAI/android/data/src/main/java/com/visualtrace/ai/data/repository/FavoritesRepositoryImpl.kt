package com.visualtrace.ai.data.repository

import android.database.sqlite.SQLiteConstraintException
import com.visualtrace.ai.data.local.CollectionEntity
import com.visualtrace.ai.data.local.FavoritesDao
import com.visualtrace.ai.data.local.PendingDeletionDao
import com.visualtrace.ai.data.local.PendingDeletionEntity
import com.visualtrace.ai.data.local.SavedResultEntity
import com.visualtrace.ai.data.remote.ApiJson
import com.visualtrace.ai.data.remote.TokenProvider
import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.remote.apiCall
import com.visualtrace.ai.data.remote.dto.FavoriteCreateDto
import com.visualtrace.ai.data.remote.dto.SearchResultDto
import com.visualtrace.ai.data.remote.parseInstant
import com.visualtrace.ai.data.remote.toDomain
import com.visualtrace.ai.data.remote.toDto
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ResultCollection
import com.visualtrace.ai.domain.model.SavedResult
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Saved results live on the device; with an account and cloud sync enabled they are mirrored. */
@Singleton
class FavoritesRepositoryImpl @Inject constructor(
    private val dao: FavoritesDao,
    private val pending: PendingDeletionDao,
    private val api: VisualTraceApi,
    private val tokens: TokenProvider,
    private val settings: SettingsRepository,
) : FavoritesRepository {

    override fun observeSaved(collectionId: String?): Flow<List<SavedResult>> =
        dao.observe(collectionId).map { list -> list.mapNotNull { it.toDomain() } }

    override fun observeCollections(): Flow<List<ResultCollection>> =
        dao.observeCollections().map { list -> list.map { ResultCollection(it.id, it.name, it.itemCount, it.createdAt) } }

    override fun isSaved(resultId: String): Flow<Boolean> = dao.isSaved(resultId)

    override suspend fun save(result: SearchResultItem, collectionId: String?, note: String?): Outcome<SavedResult> {
        val entity = SavedResultEntity(
            id = UUID.randomUUID().toString(), resultId = result.id, searchId = result.searchId,
            json = ApiJson.encodeToString(SearchResultDto.serializer(), result.toDto()), collectionId = collectionId,
            note = note?.trim()?.takeIf { it.isNotEmpty() }?.take(2000), savedAt = System.currentTimeMillis(), remoteId = null,
        )
        dao.insert(entity)
        if (cloudSyncActive()) pushOne(entity)
        return Outcome.Success(entity.toDomain()!!)
    }

    override suspend fun updateNote(savedId: String, note: String?) =
        dao.updateNote(savedId, note?.trim()?.takeIf { it.isNotEmpty() }?.take(2000))

    override suspend fun moveToCollection(savedId: String, collectionId: String?) = dao.move(savedId, collectionId)

    override suspend fun remove(savedId: String) {
        val entity = dao.get(savedId) ?: return
        dao.delete(savedId)
        entity.remoteId?.let { remote ->
            val r = com.visualtrace.ai.data.remote.apiUnit { api.deleteFavorite(remote) }
            if (r is Outcome.Failure && r.error !is AppError.NotFound) {
                pending.add(PendingDeletionEntity("favorite:$remote", System.currentTimeMillis()))
            }
        }
    }

    override suspend fun createCollection(name: String): Outcome<ResultCollection> {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > 120) return Outcome.Failure(AppError.Validation("Collection names must be 1-120 characters."))
        val entity = CollectionEntity(UUID.randomUUID().toString(), clean, System.currentTimeMillis())
        return try {
            dao.insertCollection(entity)
            Outcome.Success(ResultCollection(entity.id, entity.name, 0, entity.createdAt))
        } catch (e: SQLiteConstraintException) {
            Outcome.Failure(AppError.Validation("A collection with this name already exists."))
        }
    }

    override suspend fun deleteCollection(collectionId: String) {
        dao.detachCollection(collectionId)
        dao.deleteCollection(collectionId)
    }

    private suspend fun cloudSyncActive(): Boolean = tokens.isSignedIn && settings.settings.first().cloudSyncEnabled

    private suspend fun pushOne(entity: SavedResultEntity): Boolean {
        val snapshot = runCatching { ApiJson.decodeFromString(SearchResultDto.serializer(), entity.json) }.getOrNull() ?: return false
        val r = apiCall { api.createFavorite(FavoriteCreateDto(searchResultId = null, searchId = null, note = entity.note, snapshot = snapshot)) }
        if (r is Outcome.Success) dao.setRemoteId(entity.id, r.value.id)
        return r is Outcome.Success
    }

    override suspend fun syncWithCloud(): Outcome<Int> {
        if (!tokens.isSignedIn) return Outcome.Failure(AppError.AccountRequired)
        if (!settings.settings.first().cloudSyncEnabled) return Outcome.Success(0)
        var changed = 0
        for (e in dao.unsynced()) if (pushOne(e)) changed++
        val remote = when (val r = apiCall { api.favorites() }) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return r
        }
        val known = dao.remoteIds().toSet()
        for (fav in remote) {
            if (fav.id in known) continue
            val dto = runCatching { ApiJson.decodeFromJsonElement(SearchResultDto.serializer(), fav.snapshot) }.getOrNull() ?: continue
            dao.insert(SavedResultEntity(UUID.randomUUID().toString(), dto.id, dto.searchId,
                ApiJson.encodeToString(SearchResultDto.serializer(), dto), null, fav.note,
                parseInstant(fav.createdAt) ?: System.currentTimeMillis(), fav.id))
            changed++
        }
        return Outcome.Success(changed)
    }
}

private fun SavedResultEntity.toDomain(): SavedResult? {
    val dto = runCatching { ApiJson.decodeFromString(SearchResultDto.serializer(), json) }.getOrNull() ?: return null
    return SavedResult(id, dto.toDomain(), collectionId, note, savedAt, remoteId)
}
