package com.visualtrace.ai.features.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.HistoryEntry
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaRules
import com.visualtrace.ai.domain.model.MediaSelection
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ProviderCatalog
import com.visualtrace.ai.domain.model.SavedResult
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.VideoSearchOptions
import com.visualtrace.ai.domain.repository.ConnectivityMonitor
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.MediaRepository
import com.visualtrace.ai.domain.repository.ProviderRepository
import com.visualtrace.ai.domain.repository.SearchSubmissionQueue
import com.visualtrace.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val selection: MediaSelection? = null,
    val inspecting: Boolean = false,
    val queryText: String = "",
    val error: AppError? = null,
    val catalog: ProviderCatalog? = null,
    val catalogError: AppError? = null,
    val uploadFullVideo: Boolean = false,
    val showConsentDialog: Boolean = false,
    val showFullVideoDialog: Boolean = false,
    val submitting: Boolean = false,
) {
    val canSearch: Boolean get() = !inspecting && !submitting && (selection != null || queryText.trim().length >= 2)
}

sealed interface HomeEvent {
    data class OpenResults(val ticket: String) : HomeEvent
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val media: MediaRepository,
    private val providers: ProviderRepository,
    private val settingsRepo: SettingsRepository,
    private val queue: SearchSubmissionQueue,
    private val connectivity: ConnectivityMonitor,
    history: HistoryRepository,
    favorites: FavoritesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val recent: StateFlow<List<HistoryEntry>> = history.observe().map { it.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val saved: StateFlow<List<SavedResult>> = favorites.observeSaved().map { it.take(8) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val online: StateFlow<Boolean> = connectivity.isOnline.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    /** Keys of providers that will actually be queried (user preference or server defaults). */
    val enabledProviderKeys: StateFlow<Set<String>> = combine(settings, _state) { s, st ->
        val catalog = st.catalog ?: return@combine emptySet()
        s.enabledProviders ?: catalog.providers.filter { it.enabledByDefault }.map { it.key }.toSet()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    init { refreshProviders() }

    fun refreshProviders() = viewModelScope.launch {
        when (val r = providers.catalog()) {
            is Outcome.Success -> _state.update { it.copy(catalog = r.value, catalogError = null) }
            is Outcome.Failure -> _state.update { it.copy(catalogError = r.error) }
        }
    }

    fun onMediaPicked(uri: String?) {
        if (uri == null) return
        _state.update { it.copy(inspecting = true, error = null) }
        viewModelScope.launch {
            when (val r = media.inspect(uri)) {
                is Outcome.Success -> _state.update { it.copy(selection = r.value, inspecting = false, uploadFullVideo = false) }
                is Outcome.Failure -> _state.update { it.copy(inspecting = false, error = r.error) }
            }
        }
    }

    /** Camera result: [success] is false when the user backed out of the camera. */
    fun onCameraResult(uri: String?, success: Boolean) {
        if (success && uri != null) onMediaPicked(uri)
    }

    suspend fun cameraUri(): String = media.createCameraCaptureUri()

    fun onImageUrl(url: String): Boolean {
        val clean = url.trim()
        if (!MediaRules.isValidImageUrl(clean)) {
            _state.update { it.copy(error = AppError.Validation("Enter a valid http(s) image link.")) }
            return false
        }
        _state.update {
            it.copy(selection = MediaSelection(clean, MediaKind.IMAGE, null, clean.substringAfterLast('/').take(80), null, isRemoteUrl = true), error = null)
        }
        return true
    }

    fun clearSelection() = _state.update { it.copy(selection = null, uploadFullVideo = false) }
    fun onQueryChange(text: String) = _state.update { it.copy(queryText = text.take(300)) }
    fun dismissError() = _state.update { it.copy(error = null) }

    fun onFullVideoToggle(checked: Boolean) {
        if (checked && !settings.value.fullVideoUploadConsent) _state.update { it.copy(showFullVideoDialog = true) }
        else _state.update { it.copy(uploadFullVideo = checked) }
    }

    fun onFullVideoConsent(granted: Boolean) = viewModelScope.launch {
        if (granted) settingsRepo.setFullVideoUploadConsent(true)
        _state.update { it.copy(showFullVideoDialog = false, uploadFullVideo = granted) }
    }

    fun onSearch() {
        val st = _state.value
        if (!st.canSearch) return
        if (!connectivity.currentlyOnline()) {
            _state.update { it.copy(error = AppError.NoInternet) }
            return
        }
        val needsUploadConsent = st.selection != null && st.catalog?.providers.orEmpty()
            .any { it.uploadsMedia && it.configured && it.key in enabledProviderKeys.value }
        if (needsUploadConsent && !settings.value.consentPromptAnswered) {
            _state.update { it.copy(showConsentDialog = true) }
            return
        }
        submit()
    }

    fun onConsentAnswer(granted: Boolean) = viewModelScope.launch {
        settingsRepo.setThirdPartyConsent(granted)
        _state.update { it.copy(showConsentDialog = false) }
        settingsRepo.settings.first { it.consentPromptAnswered }
        submit()
    }

    fun dismissConsent() = _state.update { it.copy(showConsentDialog = false) }

    private fun submit() {
        val st = _state.value
        val s = settings.value
        val selection = st.selection
        val request = SearchRequest(
            media = selection,
            queryText = st.queryText.trim().takeIf { it.isNotEmpty() },
            providers = enabledProviderKeys.value.takeIf { it.isNotEmpty() }?.toList()?.sorted(),
            thirdPartyConsent = s.thirdPartyConsent,
            video = if (selection?.kind == MediaKind.VIDEO) VideoSearchOptions(s.frameIntervalSeconds, s.maxVideoDurationSeconds, st.uploadFullVideo) else null,
            savePreview = s.historyEnabled,
        )
        _state.update { it.copy(submitting = true) }
        val ticket = queue.enqueue(request)
        _state.update { it.copy(submitting = false) }
        events.trySend(HomeEvent.OpenResults(ticket))
    }

    fun searchByCharacteristics(text: String) {
        _state.update { it.copy(selection = null, queryText = text.take(300)) }
    }
}
