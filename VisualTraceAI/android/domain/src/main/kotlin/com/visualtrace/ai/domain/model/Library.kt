package com.visualtrace.ai.domain.model

data class HistoryEntry(
    val searchId: String,
    val type: SearchType,
    val status: SearchStatus,
    val createdAtEpochMs: Long,
    val resultCount: Int,
    val title: String?,
    val thumbnailPath: String?,
)

data class SavedResult(
    val id: String,
    val result: SearchResultItem,
    val collectionId: String?,
    val note: String?,
    val savedAtEpochMs: Long,
    val syncedRemoteId: String?,
)

data class ResultCollection(val id: String, val name: String, val itemCount: Int, val createdAtEpochMs: Long)

enum class ProviderCategory { REVERSE_IMAGE, WEB, SOCIAL, INDEX;
    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name.equals(raw, true) } ?: WEB
    }
}

enum class ProviderMode { LIVE, MOCK, UNAVAILABLE;
    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name.equals(raw, true) } ?: UNAVAILABLE
    }
}

data class ProviderInfo(
    val key: String,
    val name: String,
    val platform: String,
    val category: ProviderCategory,
    val supportsReverseImage: Boolean,
    val uploadsMedia: Boolean,
    val configured: Boolean,
    val mode: ProviderMode,
    val limitations: String,
    val alternatives: String,
    val enabledByDefault: Boolean,
    val siteSearchFallback: Boolean,
    val globallyDisabled: Boolean,
)

data class ProviderCatalog(val mockMode: Boolean, val embeddingModel: String, val providers: List<ProviderInfo>)

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class SearchQuality { FAST, BALANCED, THOROUGH }

data class AppSettings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val languageTag: String = "",
    val enabledProviders: Set<String>? = null,
    val searchQuality: SearchQuality = SearchQuality.BALANCED,
    val frameIntervalSeconds: Float = 3f,
    val maxVideoDurationSeconds: Int = 120,
    val maxFramesPerVideo: Int = 20,
    val historyEnabled: Boolean = true,
    val cloudSyncEnabled: Boolean = false,
    val thirdPartyConsent: Boolean = false,
    val consentPromptAnswered: Boolean = false,
    val fullVideoUploadConsent: Boolean = false,
    val guestModeChosen: Boolean = false,
) {
    companion object {
        const val MIN_FRAME_INTERVAL = 0.5f
        const val MAX_FRAME_INTERVAL = 30f
    }
}

data class UserAccount(
    val uid: String,
    val email: String?,
    val displayName: String?,
    val photoUrl: String?,
    val provider: String,
)

sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data object Guest : SessionState
    data class SignedIn(val account: UserAccount) : SessionState
}
