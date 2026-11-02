package com.visualtrace.ai.features.common

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ReportGmailerrorred
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.features.R

data class ErrorText(val title: String, val message: String, val icon: ImageVector)

@Composable
fun AppError.toText(): ErrorText = when (this) {
    AppError.NoInternet -> ErrorText(stringResource(R.string.err_no_internet_title), stringResource(R.string.err_no_internet), Icons.Outlined.CloudOff)
    is AppError.UnsupportedFormat -> ErrorText(stringResource(R.string.err_unsupported_title),
        listOfNotNull(stringResource(R.string.err_unsupported), detail?.takeIf { it.length > 12 }).joinToString("\n"), Icons.Outlined.ReportGmailerrorred)
    is AppError.FileTooLarge -> ErrorText(stringResource(R.string.err_too_large_title), stringResource(R.string.err_too_large), Icons.Outlined.ReportGmailerrorred)
    is AppError.SuspiciousFile -> ErrorText(stringResource(R.string.err_suspicious_title), detail ?: stringResource(R.string.err_suspicious), Icons.Outlined.ReportGmailerrorred)
    is AppError.ProviderUnavailable -> ErrorText(stringResource(R.string.err_provider_title), stringResource(R.string.err_provider), Icons.Outlined.ErrorOutline)
    is AppError.RateLimited -> ErrorText(stringResource(R.string.err_rate_title), stringResource(R.string.err_rate), Icons.Outlined.HourglassEmpty)
    AppError.Timeout -> ErrorText(stringResource(R.string.err_timeout_title), stringResource(R.string.err_timeout), Icons.Outlined.HourglassEmpty)
    AppError.NoResults -> ErrorText(stringResource(R.string.no_results_title), stringResource(R.string.no_results_body), Icons.Outlined.SearchOff)
    is AppError.Authentication -> ErrorText(stringResource(R.string.err_auth_title), detail ?: stringResource(R.string.err_auth), Icons.Outlined.Lock)
    AppError.AccountRequired -> ErrorText(stringResource(R.string.err_auth_title), stringResource(R.string.err_account_required), Icons.Outlined.Lock)
    AppError.AuthNotConfigured -> ErrorText(stringResource(R.string.err_auth_title), stringResource(R.string.auth_not_configured), Icons.Outlined.Lock)
    is AppError.ConsentRequired -> ErrorText(stringResource(R.string.err_consent_title), stringResource(R.string.err_consent), Icons.Outlined.Lock)
    is AppError.Validation -> ErrorText(stringResource(R.string.err_invalid_title), detail, Icons.Outlined.ErrorOutline)
    is AppError.NotFound -> ErrorText(stringResource(R.string.err_not_found_title), stringResource(R.string.err_not_found), Icons.Outlined.SearchOff)
    is AppError.Server -> ErrorText(stringResource(R.string.err_server_title), detail ?: stringResource(R.string.err_server), Icons.Outlined.ErrorOutline)
    is AppError.Unknown -> ErrorText(stringResource(R.string.err_server_title), stringResource(R.string.err_server), Icons.Outlined.ErrorOutline)
}
