package com.visualtrace.ai.features.settings

import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ProviderCatalog
import com.visualtrace.ai.domain.model.SearchQuality
import com.visualtrace.ai.domain.model.ThemeMode
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.ProviderRepository
import com.visualtrace.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val providers: ProviderRepository,
    private val history: HistoryRepository,
) : ViewModel() {
    val settings: StateFlow<AppSettings> = repo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val catalog = MutableStateFlow<ProviderCatalog?>(null)
    val catalogError = MutableStateFlow<AppError?>(null)

    init { loadProviders() }

    fun loadProviders(force: Boolean = false) = viewModelScope.launch {
        when (val r = providers.catalog(force)) {
            is Outcome.Success -> { catalog.value = r.value; catalogError.value = null }
            is Outcome.Failure -> catalogError.value = r.error
        }
    }

    fun setTheme(mode: ThemeMode) = viewModelScope.launch { repo.setTheme(mode) }
    fun setDynamic(enabled: Boolean) = viewModelScope.launch { repo.setDynamicColor(enabled) }

    fun setLanguage(tag: String) = viewModelScope.launch {
        repo.setLanguage(tag)
        AppCompatDelegate.setApplicationLocales(if (tag.isBlank()) LocaleListCompat.getEmptyLocaleList() else LocaleListCompat.forLanguageTags(tag))
    }

    fun setQuality(q: SearchQuality) = viewModelScope.launch { repo.setSearchQuality(q) }
    fun setFrameInterval(s: Float) = viewModelScope.launch { repo.setFrameInterval(s) }
    fun setMaxDuration(s: Int) = viewModelScope.launch { repo.setMaxVideoDuration(s) }
    fun setHistoryEnabled(e: Boolean) = viewModelScope.launch { repo.setHistoryEnabled(e) }
    fun clearHistory() = viewModelScope.launch { history.clear() }
    fun setConsent(e: Boolean) = viewModelScope.launch { repo.setThirdPartyConsent(e) }
    fun setFullVideoConsent(e: Boolean) = viewModelScope.launch { repo.setFullVideoUploadConsent(e) }

    /** Returns false when the address is not a valid http(s) URL. Blank resets to the built-in server. */
    fun setServerUrl(raw: String): Boolean {
        val trimmed = raw.trim()
        if (trimmed.isNotEmpty() && com.visualtrace.ai.data.remote.ServerUrlInterceptor.parseServerUrl(trimmed) == null) return false
        viewModelScope.launch {
            repo.setServerUrl(trimmed)
            loadProviders(force = true)
        }
        return true
    }

    fun setProviderEnabled(key: String, enabled: Boolean) = viewModelScope.launch {
        val defaults = catalog.value?.providers?.filter { it.enabledByDefault }?.map { it.key }?.toSet() ?: emptySet()
        repo.setProviderEnabled(key, enabled, defaults)
    }
}
