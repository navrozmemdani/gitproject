package com.visualtrace.ai.features.navigation

import kotlinx.serialization.Serializable

@Serializable data object AuthRoute
@Serializable data object HomeRoute
@Serializable data object HistoryRoute
@Serializable data object SavedRoute
@Serializable data object ProfileRoute
@Serializable data object SettingsRoute
@Serializable data object ProviderSettingsRoute

/** Either [searchId] (existing search) or [ticket] (a submission still being uploaded). */
@Serializable data class ResultsRoute(val searchId: String? = null, val ticket: String? = null)

@Serializable data class LegalRoute(val document: String)
