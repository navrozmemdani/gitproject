package com.visualtrace.ai.features

import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.SearchQuality
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.ThemeMode
import com.visualtrace.ai.domain.repository.ConnectivityMonitor
import com.visualtrace.ai.domain.repository.SearchSubmissionQueue
import com.visualtrace.ai.domain.repository.SettingsRepository
import com.visualtrace.ai.domain.repository.SubmissionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update

class FakeSettings(initial: AppSettings = AppSettings()) : SettingsRepository {
    val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state
    override suspend fun setTheme(mode: ThemeMode) = state.update { it.copy(theme = mode) }
    override suspend fun setDynamicColor(enabled: Boolean) = state.update { it.copy(dynamicColor = enabled) }
    override suspend fun setLanguage(tag: String) = state.update { it.copy(languageTag = tag) }
    override suspend fun setProviderEnabled(key: String, enabled: Boolean, defaults: Set<String>) = state.update {
        val cur = it.enabledProviders ?: defaults
        it.copy(enabledProviders = if (enabled) cur + key else cur - key)
    }
    override suspend fun setSearchQuality(quality: SearchQuality) = state.update { it.copy(searchQuality = quality) }
    override suspend fun setFrameInterval(seconds: Float) = state.update { it.copy(frameIntervalSeconds = seconds) }
    override suspend fun setMaxVideoDuration(seconds: Int) = state.update { it.copy(maxVideoDurationSeconds = seconds) }
    override suspend fun setHistoryEnabled(enabled: Boolean) = state.update { it.copy(historyEnabled = enabled) }
    override suspend fun setCloudSync(enabled: Boolean) = state.update { it.copy(cloudSyncEnabled = enabled) }
    override suspend fun setThirdPartyConsent(granted: Boolean) = state.update { it.copy(thirdPartyConsent = granted, consentPromptAnswered = true) }
    override suspend fun setFullVideoUploadConsent(granted: Boolean) = state.update { it.copy(fullVideoUploadConsent = granted) }
    override suspend fun setGuestModeChosen(chosen: Boolean) = state.update { it.copy(guestModeChosen = chosen) }
    override suspend fun setServerUrl(url: String) = state.update { it.copy(serverUrl = url) }
    override suspend fun guestId(): String = "guest"
}

class FakeQueue : SearchSubmissionQueue {
    val requests = mutableListOf<SearchRequest>()
    override fun enqueue(request: SearchRequest): String { requests += request; return "ticket-${requests.size}" }
    override fun observe(ticket: String): Flow<SubmissionState> = flowOf(SubmissionState.Submitted("sid"))
    override fun cancel(ticket: String) = Unit
}

class FakeConnectivity(var online: Boolean = true) : ConnectivityMonitor {
    override val isOnline: Flow<Boolean> get() = flowOf(online)
    override fun currentlyOnline() = online
}
