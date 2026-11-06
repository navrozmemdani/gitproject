package com.example.tempnumber.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

/** A persisted session: backend URL plus the bearer token issued at login. */
data class Session(
    val baseUrl: String = "",
    val token: String = "",
    val email: String = "",
)

/**
 * Note: Preferences DataStore is not encrypted at rest. The token grants access
 * only to the signed-in user's own numbers. For stronger protection, back this
 * with EncryptedSharedPreferences or store the token in the Keystore.
 */
class SettingsStore(private val context: Context) {
    private val baseUrlKey = stringPreferencesKey("base_url")
    private val tokenKey = stringPreferencesKey("token")
    private val emailKey = stringPreferencesKey("email")

    val session: Flow<Session> = context.dataStore.data.map { prefs ->
        Session(
            baseUrl = prefs[baseUrlKey] ?: "",
            token = prefs[tokenKey] ?: "",
            email = prefs[emailKey] ?: "",
        )
    }

    suspend fun saveSession(baseUrl: String, token: String, email: String) {
        context.dataStore.edit { prefs ->
            prefs[baseUrlKey] = baseUrl.trim()
            prefs[tokenKey] = token
            prefs[emailKey] = email.trim()
        }
    }

    /** Remembers the backend URL but drops the token (logout). */
    suspend fun clearToken() {
        context.dataStore.edit { prefs -> prefs.remove(tokenKey) }
    }
}
