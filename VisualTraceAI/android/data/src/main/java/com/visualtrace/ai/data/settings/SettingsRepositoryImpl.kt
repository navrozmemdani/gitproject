package com.visualtrace.ai.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.SearchQuality
import com.visualtrace.ai.domain.model.ThemeMode
import com.visualtrace.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsRepositoryImpl @Inject constructor(
    private val store: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val THEME = stringPreferencesKey("theme")
        val DYNAMIC = booleanPreferencesKey("dynamic_color")
        val LANGUAGE = stringPreferencesKey("language")
        val PROVIDERS = stringSetPreferencesKey("enabled_providers")
        val QUALITY = stringPreferencesKey("search_quality")
        val FRAME_INTERVAL = floatPreferencesKey("frame_interval")
        val MAX_DURATION = intPreferencesKey("max_video_duration")
        val HISTORY = booleanPreferencesKey("history_enabled")
        val CLOUD_SYNC = booleanPreferencesKey("cloud_sync")
        val CONSENT = booleanPreferencesKey("third_party_consent")
        val CONSENT_ANSWERED = booleanPreferencesKey("consent_answered")
        val FULL_VIDEO = booleanPreferencesKey("full_video_upload_consent")
        val GUEST = booleanPreferencesKey("guest_mode")
        val SERVER_URL = stringPreferencesKey("server_url")
        val GUEST_ID = stringPreferencesKey("guest_id")
    }

    override val settings: Flow<AppSettings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { p ->
            AppSettings(
                theme = p[Keys.THEME]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                dynamicColor = p[Keys.DYNAMIC] ?: false,
                languageTag = p[Keys.LANGUAGE] ?: "",
                enabledProviders = p[Keys.PROVIDERS],
                searchQuality = p[Keys.QUALITY]?.let { runCatching { SearchQuality.valueOf(it) }.getOrNull() } ?: SearchQuality.BALANCED,
                frameIntervalSeconds = p[Keys.FRAME_INTERVAL] ?: 3f,
                maxVideoDurationSeconds = p[Keys.MAX_DURATION] ?: 120,
                historyEnabled = p[Keys.HISTORY] ?: true,
                cloudSyncEnabled = p[Keys.CLOUD_SYNC] ?: false,
                thirdPartyConsent = p[Keys.CONSENT] ?: false,
                consentPromptAnswered = p[Keys.CONSENT_ANSWERED] ?: false,
                fullVideoUploadConsent = p[Keys.FULL_VIDEO] ?: false,
                guestModeChosen = p[Keys.GUEST] ?: false,
                serverUrl = p[Keys.SERVER_URL] ?: "",
            )
        }

    override suspend fun setTheme(mode: ThemeMode) { store.edit { it[Keys.THEME] = mode.name } }
    override suspend fun setDynamicColor(enabled: Boolean) { store.edit { it[Keys.DYNAMIC] = enabled } }
    override suspend fun setLanguage(tag: String) { store.edit { it[Keys.LANGUAGE] = tag } }

    override suspend fun setProviderEnabled(key: String, enabled: Boolean, defaults: Set<String>) {
        store.edit {
            val current = it[Keys.PROVIDERS] ?: defaults
            it[Keys.PROVIDERS] = if (enabled) current + key else current - key
        }
    }

    override suspend fun setSearchQuality(quality: SearchQuality) { store.edit { it[Keys.QUALITY] = quality.name } }

    override suspend fun setFrameInterval(seconds: Float) {
        store.edit { it[Keys.FRAME_INTERVAL] = seconds.coerceIn(AppSettings.MIN_FRAME_INTERVAL, AppSettings.MAX_FRAME_INTERVAL) }
    }

    override suspend fun setMaxVideoDuration(seconds: Int) { store.edit { it[Keys.MAX_DURATION] = seconds.coerceIn(5, 600) } }
    override suspend fun setHistoryEnabled(enabled: Boolean) { store.edit { it[Keys.HISTORY] = enabled } }
    override suspend fun setCloudSync(enabled: Boolean) { store.edit { it[Keys.CLOUD_SYNC] = enabled } }

    override suspend fun setThirdPartyConsent(granted: Boolean) {
        store.edit {
            it[Keys.CONSENT] = granted
            it[Keys.CONSENT_ANSWERED] = true
        }
    }

    override suspend fun setFullVideoUploadConsent(granted: Boolean) { store.edit { it[Keys.FULL_VIDEO] = granted } }
    override suspend fun setGuestModeChosen(chosen: Boolean) { store.edit { it[Keys.GUEST] = chosen } }
    override suspend fun setServerUrl(url: String) { store.edit { it[Keys.SERVER_URL] = url.trim() } }

    override suspend fun guestId(): String {
        store.data.first()[Keys.GUEST_ID]?.let { return it }
        var id = ""
        store.edit { prefs ->
            id = prefs[Keys.GUEST_ID] ?: UUID.randomUUID().toString().also { prefs[Keys.GUEST_ID] = it }
        }
        return id
    }
}
