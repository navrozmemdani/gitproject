package com.visualtrace.ai.domain.model

/** Typed result used across layers so the UI can always render a precise, honest state. */
sealed interface Outcome<out T> {
    data class Success<T>(val value: T) : Outcome<T>
    data class Failure(val error: AppError) : Outcome<Nothing>
}

inline fun <T, R> Outcome<T>.map(transform: (T) -> R): Outcome<R> = when (this) {
    is Outcome.Success -> Outcome.Success(transform(value))
    is Outcome.Failure -> this
}

fun <T> Outcome<T>.getOrNull(): T? = (this as? Outcome.Success)?.value

/** User-facing error categories (spec section 18). */
sealed interface AppError {
    val retryable: Boolean get() = false

    data object NoInternet : AppError { override val retryable = true }
    data class UnsupportedFormat(val detail: String? = null) : AppError
    data class FileTooLarge(val limitMb: Int? = null) : AppError
    data class SuspiciousFile(val detail: String? = null) : AppError
    data class ProviderUnavailable(val provider: String? = null) : AppError { override val retryable = true }
    data class RateLimited(val retryAfterSeconds: Int? = null) : AppError { override val retryable = true }
    data object Timeout : AppError { override val retryable = true }
    data object NoResults : AppError
    data class Authentication(val detail: String? = null) : AppError
    data object AccountRequired : AppError
    data object AuthNotConfigured : AppError
    data class ConsentRequired(val detail: String? = null) : AppError
    data class Validation(val detail: String) : AppError
    data class NotFound(val detail: String? = null) : AppError
    data class Server(val code: String, val detail: String?, override val retryable: Boolean = true) : AppError
    data class Unknown(val detail: String? = null) : AppError { override val retryable = true }
}
