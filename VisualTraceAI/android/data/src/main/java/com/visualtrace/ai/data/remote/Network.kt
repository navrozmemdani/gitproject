package com.visualtrace.ai.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.visualtrace.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import okhttp3.Authenticator
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import javax.inject.Inject
import javax.inject.Singleton

/** Build-time client configuration supplied by the app module (BuildConfig). */
data class ApiConfig(
    val baseUrl: String,
    val clientApiKey: String,
    val debugLogging: Boolean,
    val userAgent: String,
)

/** Firebase is optional: without google-services.json the app runs in guest mode only. */
class FirebaseAuthHolder(val auth: FirebaseAuth?)

@Singleton
class TokenProvider @Inject constructor(private val holder: FirebaseAuthHolder) {
    suspend fun idToken(forceRefresh: Boolean = false): String? {
        val user = holder.auth?.currentUser ?: return null
        return runCatching { user.getIdToken(forceRefresh).await().token }.getOrNull()
    }

    val isSignedIn: Boolean get() = holder.auth?.currentUser != null
}

/**
 * Adds client identification and the caller identity to every request:
 * Firebase ID token for signed-in users, otherwise the device-scoped guest id.
 * Provider API keys are never present in the app; they live on the backend.
 */
class IdentityInterceptor @Inject constructor(
    private val config: ApiConfig,
    private val tokens: TokenProvider,
    private val settings: SettingsRepository,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val builder = chain.request().newBuilder()
            .header("User-Agent", config.userAgent)
            .header("Accept", "application/json")
        if (config.clientApiKey.isNotBlank()) builder.header("X-API-Key", config.clientApiKey)
        val token = if (tokens.isSignedIn) runBlocking { tokens.idToken() } else null
        if (token != null) builder.header("Authorization", "Bearer $token")
        else builder.header("X-Guest-Id", runBlocking { settings.guestId() })
        return chain.proceed(builder.build())
    }
}

/** Retries once with a force-refreshed Firebase token after a 401. */
class TokenRefreshAuthenticator @Inject constructor(private val tokens: TokenProvider) : Authenticator {
    override fun authenticate(route: Route?, response: Response): Request? {
        if (!tokens.isSignedIn || response.request.header("X-Token-Refreshed") != null) return null
        val fresh = runBlocking { tokens.idToken(forceRefresh = true) } ?: return null
        return response.request.newBuilder()
            .header("Authorization", "Bearer $fresh")
            .header("X-Token-Refreshed", "1")
            .build()
    }
}

/**
 * Redirects API calls to the server address chosen in Settings, so one APK can talk to a
 * backend on a laptop (same Wi-Fi), a test server or production without rebuilding.
 */
class ServerUrlInterceptor @Inject constructor(private val settings: SettingsRepository) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val override = runBlocking { settings.settings.first().serverUrl }
        val target = parseServerUrl(override) ?: return chain.proceed(chain.request())
        val original = chain.request().url
        val prefix = target.encodedPath.trimEnd('/')
        val url = original.newBuilder()
            .scheme(target.scheme).host(target.host).port(target.port)
            .encodedPath(prefix + original.encodedPath)
            .build()
        return chain.proceed(chain.request().newBuilder().url(url).build())
    }

    companion object {
        fun parseServerUrl(raw: String?): okhttp3.HttpUrl? {
            val trimmed = raw?.trim().orEmpty()
            if (trimmed.isEmpty()) return null
            val withScheme = if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "http://$trimmed"
            return withScheme.toHttpUrlOrNull()
        }
    }
}
