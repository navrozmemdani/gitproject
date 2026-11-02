package com.visualtrace.ai.data.remote

import com.visualtrace.ai.data.remote.dto.ErrorEnvelope
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

val ApiJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
    encodeDefaults = true
}

/** Maps the backend's `{"error": {"code", "message"}}` envelope to domain errors. */
object ErrorMapper {
    fun fromHttp(status: Int, body: String?, retryAfter: String?): AppError {
        val envelope = body?.let { runCatching { ApiJson.decodeFromString(ErrorEnvelope.serializer(), it) }.getOrNull() }
        val code = envelope?.error?.code
        val message = envelope?.error?.message
        return when (code) {
            "unsupported_media_type", "corrupt_image", "corrupt_video", "empty_file", "no_frames", "not_an_image" ->
                AppError.UnsupportedFormat(message)
            "file_too_large" -> AppError.FileTooLarge()
            "suspicious_file", "scan_unavailable" -> AppError.SuspiciousFile(message)
            "authentication_failed", "invalid_client_key" -> AppError.Authentication(message)
            "auth_not_configured" -> AppError.AuthNotConfigured
            "account_required" -> AppError.AccountRequired
            "rate_limited" -> AppError.RateLimited(retryAfter?.toIntOrNull())
            "consent_required" -> AppError.ConsentRequired(message)
            "not_found" -> AppError.NotFound(message)
            "validation_error", "missing_image", "missing_video", "unknown_provider", "too_many_frames",
            "unsafe_url", "fetch_failed", "duplicate" -> AppError.Validation(message ?: "The request is invalid.")
            null -> when (status) {
                401 -> AppError.Authentication(null)
                404 -> AppError.NotFound(null)
                413 -> AppError.FileTooLarge()
                429 -> AppError.RateLimited(retryAfter?.toIntOrNull())
                in 500..599 -> AppError.Server("http_$status", null, retryable = true)
                else -> AppError.Server("http_$status", null, retryable = false)
            }
            else -> AppError.Server(code, message, retryable = envelope.error.retryable || status >= 500)
        }
    }

    fun fromThrowable(t: Throwable): AppError = when (t) {
        is HttpException -> fromHttp(t.code(), t.response()?.errorBody()?.string(), t.response()?.headers()?.get("Retry-After"))
        is SocketTimeoutException -> AppError.Timeout
        is UnknownHostException, is ConnectException -> AppError.NoInternet
        is SSLException -> AppError.Server("tls_error", "A secure connection could not be established.")
        is IOException -> AppError.NoInternet
        is kotlinx.serialization.SerializationException -> AppError.Server("bad_response", "Unexpected server response.")
        else -> AppError.Unknown(t.message)
    }
}

suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.Failure(ErrorMapper.fromThrowable(t))
}

suspend fun apiUnit(block: suspend () -> Response<Unit>): Outcome<Unit> = try {
    val r = block()
    if (r.isSuccessful) Outcome.Success(Unit)
    else Outcome.Failure(ErrorMapper.fromHttp(r.code(), r.errorBody()?.string(), r.headers()["Retry-After"]))
} catch (c: CancellationException) {
    throw c
} catch (t: Throwable) {
    Outcome.Failure(ErrorMapper.fromThrowable(t))
}
