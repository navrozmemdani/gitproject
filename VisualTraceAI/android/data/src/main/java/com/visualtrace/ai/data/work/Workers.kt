package com.visualtrace.ai.data.work

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.visualtrace.ai.data.repository.HistoryRepositoryImpl
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaSelection
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.VideoSearchOptions
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.MediaRepository
import com.visualtrace.ai.domain.repository.SearchSubmissionQueue
import com.visualtrace.ai.domain.repository.SettingsRepository
import com.visualtrace.ai.domain.repository.SubmissionState
import com.visualtrace.ai.domain.usecase.StartSearchUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Serializable
data class SubmissionPayload(
    val uri: String? = null,
    val kind: String? = null,
    val mime: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val remoteUrl: Boolean = false,
    val query: String? = null,
    val providers: List<String>? = null,
    val consent: Boolean = false,
    val frameInterval: Float? = null,
    val maxDuration: Int? = null,
    val uploadFullVideo: Boolean = false,
    val savePreview: Boolean = true,
) {
    fun toRequest(): SearchRequest = SearchRequest(
        media = uri?.let {
            MediaSelection(it, if (kind == "VIDEO") MediaKind.VIDEO else MediaKind.IMAGE, mime, name, size, width, height, durationMs, remoteUrl)
        },
        queryText = query, providers = providers, thirdPartyConsent = consent,
        video = if (kind == "VIDEO") VideoSearchOptions(frameInterval ?: 3f, maxDuration ?: 120, uploadFullVideo) else null,
        savePreview = savePreview,
    )

    companion object {
        fun from(r: SearchRequest) = SubmissionPayload(
            uri = r.media?.uri, kind = r.media?.kind?.name, mime = r.media?.mimeType, name = r.media?.displayName,
            size = r.media?.sizeBytes, width = r.media?.width, height = r.media?.height, durationMs = r.media?.durationMs,
            remoteUrl = r.media?.isRemoteUrl == true, query = r.queryText, providers = r.providers, consent = r.thirdPartyConsent,
            frameInterval = r.video?.frameIntervalSeconds, maxDuration = r.video?.maxDurationSeconds,
            uploadFullVideo = r.video?.uploadFullVideo == true, savePreview = r.savePreview,
        )
    }
}

/** Serialises [AppError] through WorkManager's Data so the UI can show the precise message. */
object ErrorCodec {
    fun encode(e: AppError): String = when (e) {
        AppError.NoInternet -> "no_internet"
        is AppError.UnsupportedFormat -> "unsupported|${e.detail.orEmpty()}"
        is AppError.FileTooLarge -> "too_large|${e.limitMb ?: ""}"
        is AppError.SuspiciousFile -> "suspicious|${e.detail.orEmpty()}"
        is AppError.ProviderUnavailable -> "provider_unavailable|${e.provider.orEmpty()}"
        is AppError.RateLimited -> "rate_limited|${e.retryAfterSeconds ?: ""}"
        AppError.Timeout -> "timeout"
        AppError.NoResults -> "no_results"
        is AppError.Authentication -> "auth|${e.detail.orEmpty()}"
        AppError.AccountRequired -> "account_required"
        AppError.AuthNotConfigured -> "auth_not_configured"
        is AppError.ConsentRequired -> "consent|${e.detail.orEmpty()}"
        is AppError.Validation -> "validation|${e.detail}"
        is AppError.NotFound -> "not_found|${e.detail.orEmpty()}"
        is AppError.Server -> "server|${e.code}|${e.retryable}|${e.detail.orEmpty()}"
        is AppError.Unknown -> "unknown|${e.detail.orEmpty()}"
    }

    fun decode(raw: String?): AppError {
        if (raw == null) return AppError.Unknown(null)
        val kind = raw.substringBefore('|')
        val rest = raw.substringAfter('|', "")
        val arg = rest.takeIf { it.isNotEmpty() }
        return when (kind) {
            "no_internet" -> AppError.NoInternet
            "unsupported" -> AppError.UnsupportedFormat(arg)
            "too_large" -> AppError.FileTooLarge(arg?.toIntOrNull())
            "suspicious" -> AppError.SuspiciousFile(arg)
            "provider_unavailable" -> AppError.ProviderUnavailable(arg)
            "rate_limited" -> AppError.RateLimited(arg?.toIntOrNull())
            "timeout" -> AppError.Timeout
            "no_results" -> AppError.NoResults
            "auth" -> AppError.Authentication(arg)
            "account_required" -> AppError.AccountRequired
            "auth_not_configured" -> AppError.AuthNotConfigured
            "consent" -> AppError.ConsentRequired(arg)
            "validation" -> AppError.Validation(arg ?: "Invalid request")
            "not_found" -> AppError.NotFound(arg)
            "server" -> {
                val parts = rest.split('|', limit = 3)
                AppError.Server(parts.getOrNull(0)?.ifEmpty { null } ?: "server", parts.getOrNull(2)?.ifEmpty { null }, parts.getOrNull(1) == "true")
            }
            else -> AppError.Unknown(arg)
        }
    }
}

@HiltWorker
class SearchSubmitWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val startSearch: StartSearchUseCase,
    private val settings: SettingsRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val payload = inputData.getString(KEY_PAYLOAD)?.let { runCatching { Json.decodeFromString(SubmissionPayload.serializer(), it) }.getOrNull() }
            ?: return Result.failure(workDataOf(KEY_ERROR to ErrorCodec.encode(AppError.Validation("Invalid search request"))))
        return when (val r = startSearch(payload.toRequest(), settings.settings.first())) {
            is Outcome.Success -> Result.success(workDataOf(KEY_SEARCH_ID to r.value))
            is Outcome.Failure -> Result.failure(workDataOf(KEY_ERROR to ErrorCodec.encode(r.error)))
        }
    }

    companion object {
        const val KEY_PAYLOAD = "payload"
        const val KEY_SEARCH_ID = "search_id"
        const val KEY_ERROR = "error"
    }
}

@HiltWorker
class TempCleanupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val media: MediaRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        media.cleanupTemporaryFiles(olderThanMs = TimeUnit.HOURS.toMillis(1))
        return Result.success()
    }
}

@HiltWorker
class PendingDeletionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val history: HistoryRepositoryImpl,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = if (history.flushPendingDeletions()) Result.success() else Result.retry()
}

@HiltWorker
class CloudSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val favorites: FavoritesRepository,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = when (val r = favorites.syncWithCloud()) {
        is Outcome.Success -> Result.success()
        is Outcome.Failure -> if (r.error.retryable) Result.retry() else Result.failure()
    }
}

@Singleton
class WorkScheduler @Inject constructor(@ApplicationContext context: Context) : SearchSubmissionQueue {
    private val wm = WorkManager.getInstance(context)
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    override fun enqueue(request: SearchRequest): String {
        val work = OneTimeWorkRequestBuilder<SearchSubmitWorker>()
            .setInputData(workDataOf(SearchSubmitWorker.KEY_PAYLOAD to Json.encodeToString(SubmissionPayload.serializer(), SubmissionPayload.from(request))))
            .addTag(TAG_SEARCH)
            .build()
        wm.enqueue(work)
        return work.id.toString()
    }

    override fun observe(ticket: String): Flow<SubmissionState> =
        wm.getWorkInfoByIdFlow(UUID.fromString(ticket)).mapNotNull { info ->
            when (info?.state) {
                null -> null
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> SubmissionState.Waiting
                WorkInfo.State.RUNNING -> SubmissionState.Running
                WorkInfo.State.SUCCEEDED -> info.outputData.getString(SearchSubmitWorker.KEY_SEARCH_ID)
                    ?.let { SubmissionState.Submitted(it) } ?: SubmissionState.Failed(AppError.Unknown("missing id"))
                WorkInfo.State.FAILED -> SubmissionState.Failed(ErrorCodec.decode(info.outputData.getString(SearchSubmitWorker.KEY_ERROR)))
                WorkInfo.State.CANCELLED -> SubmissionState.Failed(AppError.Validation("The search was cancelled."))
            }
        }

    override fun cancel(ticket: String) {
        wm.cancelWorkById(UUID.fromString(ticket))
    }

    fun schedulePeriodicMaintenance() {
        wm.enqueueUniquePeriodicWork(
            "temp-cleanup", ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<TempCleanupWorker>(6, TimeUnit.HOURS).build(),
        )
    }

    fun schedulePendingDeletions() {
        wm.enqueueUniqueWork(
            "pending-deletions", ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<PendingDeletionWorker>().setConstraints(online)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS).build(),
        )
    }

    fun setCloudSync(enabled: Boolean) {
        if (enabled) {
            wm.enqueueUniquePeriodicWork(
                "cloud-sync", ExistingPeriodicWorkPolicy.UPDATE,
                PeriodicWorkRequestBuilder<CloudSyncWorker>(12, TimeUnit.HOURS).setConstraints(online).build(),
            )
            wm.enqueueUniqueWork("cloud-sync-now", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<CloudSyncWorker>().setConstraints(online).build())
        } else {
            wm.cancelUniqueWork("cloud-sync")
        }
    }

    companion object {
        const val TAG_SEARCH = "search-submit"
    }
}
