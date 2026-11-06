package com.example.tempnumber.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class Settings(val baseUrl: String = "", val apiKey: String = "")

/**
 * Stores the backend base URL and app API key.
 *
 * Note: Preferences DataStore is not encrypted at rest. The API key here grants
 * access to numbers the backend tracks, not to anyone else's SMS. For stronger
 * protection, back this with EncryptedSharedPreferences or the Keystore.
 */
class SettingsStore(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val apiKeyKey = stringPreferencesKey("api_key")

    val settings: Flow<Settings> = context.dataStore.data.map { prefs ->
        Settings(
            baseUrl = prefs[baseUrlKey] ?: "",
            apiKey = prefs[apiKeyKey] ?: "",
        )
    }

    suspend fun save(baseUrl: String, apiKey: String) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            prefs[apiKeyKey] = apiKey.trim()
        }
    }
}
