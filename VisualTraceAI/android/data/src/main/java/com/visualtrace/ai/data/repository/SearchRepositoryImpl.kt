package com.visualtrace.ai.data.repository

import android.content.Context
import android.net.Uri
import com.visualtrace.ai.data.media.MediaRepositoryImpl
import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.remote.apiCall
import com.visualtrace.ai.data.remote.apiUnit
import com.visualtrace.ai.data.remote.dto.ReportDto
import com.visualtrace.ai.data.remote.dto.TextSearchDto
import com.visualtrace.ai.data.remote.toDomain
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.ExtractedFrame
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ReportReason
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultSort
import com.visualtrace.ai.domain.model.ResultsPage
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.SearchRepository
import com.visualtrace.ai.domain.repository.SettingsRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

private val TEXT = "text/plain".toMediaType()
private val JPEG = "image/jpeg".toMediaType()

private fun String.part(): RequestBody = toRequestBody(TEXT)
private fun Boolean.part(): RequestBody = toString().toRequestBody(TEXT)

@Singleton
class SearchRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: VisualTraceApi,
    private val media: MediaRepositoryImpl,
    private val history: HistoryRepository,
    private val settings: SettingsRepository,
) : SearchRepository {

    override suspend fun submit(request: SearchRequest, frames: List<ExtractedFrame>): Outcome<String> {
        val providers = request.providers?.takeIf { it.isNotEmpty() }?.joinToString(",")?.part()
        val query = request.queryText?.trim()?.takeIf { it.isNotEmpty() }?.part()
        val selection = request.media
        val result = when {
            selection == null -> apiCall {
                api.searchText(TextSearchDto(request.queryText!!.trim(), request.providers?.takeIf { it.isNotEmpty() }))
            }
            selection.kind == MediaKind.IMAGE && selection.isRemoteUrl -> apiCall {
                api.searchImage(null, selection.uri.part(), providers, request.thirdPartyConsent.part(), query,
                    request.savePreview.part())
            }
            selection.kind == MediaKind.IMAGE -> {
                val bytes = when (val prepared = media.prepareImageForUpload(selection.uri)) {
                    is Outcome.Success -> prepared.value
                    is Outcome.Failure -> return prepared
                }
                val name = (selection.displayName?.substringBeforeLast('.') ?: "image") + ".jpg"
                val part = MultipartBody.Part.createFormData("file", name, bytes.toRequestBody(JPEG))
                apiCall {
                    api.searchImage(part, null, providers, request.thirdPartyConsent.part(), query, request.savePreview.part())
                }
            }
            else -> {
                val options = request.video ?: return Outcome.Failure(AppError.Validation("Missing video options."))
                val frameParts = frames.map { f ->
                    MultipartBody.Part.createFormData("frames", File(f.filePath).name, File(f.filePath).asRequestBody(JPEG))
                }
                val videoPart = if (options.uploadFullVideo) {
                    val mime = (selection.mimeType ?: "video/mp4").toMediaType()
                    MultipartBody.Part.createFormData("video", selection.displayName ?: "video.mp4",
                        ContentUriRequestBody(context, Uri.parse(selection.uri), mime, selection.sizeBytes ?: -1))
                } else null
                apiCall {
                    api.searchVideo(
                        frames = frameParts, video = videoPart,
                        frameTimestamps = if (frames.isEmpty()) null else frames.joinToString(",") { it.timestampMs.toString() }.part(),
                        uploadConsent = options.uploadFullVideo.part(),
                        frameInterval = options.frameIntervalSeconds.toString().part(),
                        maxDuration = options.maxDurationSeconds.toString().part(),
                        durationMs = selection.durationMs?.toString()?.part(),
                        originalFilename = selection.displayName?.take(255)?.part(),
                        queryText = query, providers = providers,
                        thirdPartyConsent = request.thirdPartyConsent.part(), savePreview = request.savePreview.part(),
                    )
                }
            }
        }
        return when (result) {
            is Outcome.Success -> Outcome.Success(result.value.searchId)
            is Outcome.Failure -> result
        }
    }

    override suspend fun getSession(searchId: String): Outcome<SearchSession> =
        when (val r = apiCall { api.getSearch(searchId) }) {
            is Outcome.Success -> Outcome.Success(r.value.toDomain())
            is Outcome.Failure -> r
        }

    override fun observe(searchId: String): Flow<Outcome<SearchSession>> = flow {
        var delayMs = 800L
        var consecutiveErrors = 0
        while (true) {
            when (val r = getSession(searchId)) {
                is Outcome.Success -> {
                    consecutiveErrors = 0
                    emit(r)
                    if (r.value.status.isTerminal) {
                        if (settings.settings.first().historyEnabled) history.record(r.value)
                        return@flow
                    }
                }
                is Outcome.Failure -> {
                    emit(r)
                    consecutiveErrors++
                    if (!r.error.retryable || consecutiveErrors >= 5) return@flow
                }
            }
            delay(delayMs)
            delayMs = (delayMs * 1.5).toLong().coerceAtMost(4_000L)
        }
    }

    override suspend fun getResults(searchId: String, filter: ResultFilter, sort: ResultSort, page: Int, pageSize: Int): Outcome<ResultsPage> =
        when (val r = apiCall { api.getResults(searchId, filter.apiValue, sort.apiValue, page, pageSize) }) {
            is Outcome.Success -> Outcome.Success(r.value.toDomain())
            is Outcome.Failure -> r
        }

    override suspend fun report(resultId: String, reason: ReportReason, comment: String?): Outcome<Unit> =
        apiUnit { api.report(resultId, ReportDto(reason.apiValue, comment?.take(1000))) }
}

/** Streams a content:// video without loading it into memory (explicit-consent uploads only). */
class ContentUriRequestBody(
    private val context: Context,
    private val uri: Uri,
    private val type: MediaType,
    private val length: Long,
) : RequestBody() {
    override fun contentType(): MediaType = type
    override fun contentLength(): Long = length
    override fun writeTo(sink: BufferedSink) {
        context.contentResolver.openInputStream(uri)?.source()?.use { sink.writeAll(it) }
            ?: throw java.io.IOException("Cannot open video")
    }
}
