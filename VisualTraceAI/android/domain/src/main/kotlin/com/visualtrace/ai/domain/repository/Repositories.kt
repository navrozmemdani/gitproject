package com.visualtrace.ai.domain.repository

import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.ExtractedFrame
import com.visualtrace.ai.domain.model.HistoryEntry
import com.visualtrace.ai.domain.model.MediaSelection
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ProviderCatalog
import com.visualtrace.ai.domain.model.ReportReason
import com.visualtrace.ai.domain.model.ResultCollection
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultSort
import com.visualtrace.ai.domain.model.ResultsPage
import com.visualtrace.ai.domain.model.SavedResult
import com.visualtrace.ai.domain.model.SearchQuality
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.model.SearchType
import com.visualtrace.ai.domain.model.SessionState
import com.visualtrace.ai.domain.model.ThemeMode
import kotlinx.coroutines.flow.Flow

interface SearchRepository {
    /** Uploads the query (image, extracted frames or text) and returns the server search id. */
    suspend fun submit(request: SearchRequest, frames: List<ExtractedFrame> = emptyList()): Outcome<String>

    /** Emits the search state, polling until it reaches a terminal status. */
    fun observe(searchId: String): Flow<Outcome<SearchSession>>
    suspend fun getSession(searchId: String): Outcome<SearchSession>
    suspend fun getResults(searchId: String, filter: ResultFilter, sort: ResultSort, page: Int = 1, pageSize: Int = 50): Outcome<ResultsPage>
    suspend fun report(resultId: String, reason: ReportReason, comment: String?): Outcome<Unit>
}

interface HistoryRepository {
    fun observe(type: SearchType? = null): Flow<List<HistoryEntry>>
    suspend fun record(session: SearchSession)
    suspend fun delete(searchId: String): Outcome<Unit>
    suspend fun clear(): Outcome<Unit>
    suspend fun cachedResults(searchId: String): ResultsPage?
    suspend fun cacheResults(page: ResultsPage)
}

interface FavoritesRepository {
    fun observeSaved(collectionId: String? = null): Flow<List<SavedResult>>
    fun observeCollections(): Flow<List<ResultCollection>>
    fun isSaved(resultId: String): Flow<Boolean>
    suspend fun save(result: SearchResultItem, collectionId: String?, note: String?): Outcome<SavedResult>
    suspend fun updateNote(savedId: String, note: String?)
    suspend fun moveToCollection(savedId: String, collectionId: String?)
    suspend fun remove(savedId: String)
    suspend fun createCollection(name: String): Outcome<ResultCollection>
    suspend fun deleteCollection(collectionId: String)
    suspend fun syncWithCloud(): Outcome<Int>
}

interface ProviderRepository {
    suspend fun catalog(forceRefresh: Boolean = false): Outcome<ProviderCatalog>
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setTheme(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setLanguage(tag: String)
    suspend fun setProviderEnabled(key: String, enabled: Boolean, defaults: Set<String>)
    suspend fun setSearchQuality(quality: SearchQuality)
    suspend fun setFrameInterval(seconds: Float)
    suspend fun setMaxVideoDuration(seconds: Int)
    suspend fun setHistoryEnabled(enabled: Boolean)
    suspend fun setCloudSync(enabled: Boolean)
    suspend fun setThirdPartyConsent(granted: Boolean)
    suspend fun setFullVideoUploadConsent(granted: Boolean)
    suspend fun setGuestModeChosen(chosen: Boolean)
    suspend fun guestId(): String
}

interface AuthRepository {
    val session: Flow<SessionState>
    val isConfigured: Boolean
    suspend fun signInWithEmail(email: String, password: String): Outcome<Unit>
    suspend fun register(email: String, password: String, displayName: String?): Outcome<Unit>
    suspend fun signInWithGoogleIdToken(idToken: String): Outcome<Unit>
    suspend fun sendPasswordReset(email: String): Outcome<Unit>
    suspend fun continueAsGuest()
    suspend fun signOut()
    suspend fun deleteServerData(): Outcome<Unit>
    suspend fun idToken(forceRefresh: Boolean = false): String?
}

interface MediaRepository {
    suspend fun inspect(uri: String): Outcome<MediaSelection>
    suspend fun extractFrames(media: MediaSelection, intervalSeconds: Float, maxDurationSeconds: Int, maxFrames: Int): Outcome<List<ExtractedFrame>>
    suspend fun createCameraCaptureUri(): String
    suspend fun cleanupTemporaryFiles(olderThanMs: Long = 0)
}

interface ConnectivityMonitor {
    val isOnline: Flow<Boolean>
    fun currentlyOnline(): Boolean
}

/** Background (WorkManager) submission so long uploads survive leaving the screen. */
interface SearchSubmissionQueue {
    fun enqueue(request: com.visualtrace.ai.domain.model.SearchRequest): String
    fun observe(ticket: String): Flow<SubmissionState>
    fun cancel(ticket: String)
}

sealed interface SubmissionState {
    data object Waiting : SubmissionState
    data object Running : SubmissionState
    data class Submitted(val searchId: String) : SubmissionState
    data class Failed(val error: com.visualtrace.ai.domain.model.AppError) : SubmissionState
}
