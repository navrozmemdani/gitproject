package com.visualtrace.ai.domain.model

enum class SearchType { IMAGE, VIDEO, TEXT }

enum class SearchStatus {
    QUEUED, RUNNING, COMPLETED, PARTIAL, FAILED;

    val isTerminal: Boolean get() = this == COMPLETED || this == PARTIAL || this == FAILED

    companion object {
        fun from(raw: String?): SearchStatus = entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: QUEUED
    }
}

enum class MediaKind { IMAGE, VIDEO }

/** Media chosen by the user (photo picker, SAF, camera or pasted URL). */
data class MediaSelection(
    val uri: String,
    val kind: MediaKind,
    val mimeType: String?,
    val displayName: String?,
    val sizeBytes: Long?,
    val width: Int? = null,
    val height: Int? = null,
    val durationMs: Long? = null,
    val isRemoteUrl: Boolean = false,
)

data class ExtractedFrame(val filePath: String, val timestampMs: Long)

data class VideoSearchOptions(
    val frameIntervalSeconds: Float,
    val maxDurationSeconds: Int,
    val uploadFullVideo: Boolean,
)

data class SearchRequest(
    val media: MediaSelection?,
    val queryText: String?,
    val providers: List<String>?,
    val thirdPartyConsent: Boolean,
    val video: VideoSearchOptions? = null,
    val savePreview: Boolean = true,
)

enum class ProviderRunStatus {
    OK, NO_RESULTS, NOT_CONFIGURED, UNSUPPORTED, SKIPPED_NO_CONSENT, SKIPPED_NO_QUERY, DISABLED,
    RATE_LIMITED, TIMEOUT, ERROR, MOCK, UNKNOWN;

    companion object {
        fun from(raw: String?): ProviderRunStatus =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

data class ProviderReport(
    val key: String,
    val name: String,
    val status: ProviderRunStatus,
    val message: String?,
    val resultCount: Int,
    val searched: Boolean,
    val isMock: Boolean,
)

/** Where a displayed value came from (spec 8: verified vs inferred vs unavailable). */
enum class Provenance { PROVIDER, MEASURED, INFERRED, UNAVAILABLE }

data class AnalysisSection(
    val items: List<LabeledValue>,
    val source: String,
    val reason: String? = null,
) {
    val isAvailable: Boolean get() = source != "unavailable"
    val isInferred: Boolean get() = source.startsWith("inferred")
}

data class LabeledValue(val label: String, val confidence: Float? = null, val hex: String? = null)

data class ImageAnalysis(
    val width: Int?,
    val height: Int?,
    val orientation: String?,
    val qualityLabel: String?,
    val qualityIssues: List<String>,
    val colors: AnalysisSection,
    val backgroundType: String?,
    val backgroundColor: String?,
    val objects: AnalysisSection,
    val clothing: AnalysisSection,
    val environment: AnalysisSection,
    val ocrText: String?,
    val ocrSource: String,
    val ocrReason: String?,
    val characteristics: List<String>,
    val keywords: List<String>,
    val embeddingModel: String?,
)

data class SearchSession(
    val id: String,
    val type: SearchType,
    val status: SearchStatus,
    val stage: String?,
    val progress: Float,
    val createdAtEpochMs: Long,
    val completedAtEpochMs: Long?,
    val resultCount: Int,
    val queryText: String?,
    val originalFilename: String?,
    val previewThumbnailBase64: String?,
    val mediaWidth: Int?,
    val mediaHeight: Int?,
    val durationMs: Long?,
    val framesProcessed: Int?,
    val frameIntervalSeconds: Float?,
    val providerReports: List<ProviderReport>,
    val analysis: ImageAnalysis?,
    val errorMessage: String?,
    val disclaimer: String,
) {
    val providersSearched: List<ProviderReport> get() = providerReports.filter { it.searched || it.isMock }
    val usedMockData: Boolean get() = providerReports.any { it.isMock }
}

enum class MatchType { EXACT, CROPPED, SIMILAR, RELATED;
    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name.equals(raw, true) } ?: RELATED
    }
}

enum class ResultCategory { SOCIAL, WEBSITE, NEWS;
    companion object {
        fun from(raw: String?) = entries.firstOrNull { it.name.equals(raw, true) } ?: WEBSITE
    }
}

enum class ResultFilter(val apiValue: String) {
    ALL("all"), EXACT("exact"), SIMILAR("similar"), SOCIAL("social"), WEBSITES("websites"), NEWS("news")
}

enum class ResultSort(val apiValue: String) {
    SIMILARITY("similarity"), RECENT("recent"), RELEVANCE("relevance")
}

data class SearchResultItem(
    val id: String,
    val searchId: String,
    val providerKey: String,
    val alsoFoundBy: List<String>,
    val platform: String,
    val category: ResultCategory,
    val matchType: MatchType,
    val sourceUrl: String,
    val imageUrl: String?,
    val thumbnailUrl: String?,
    val pageTitle: String?,
    val description: String?,
    val similarityScore: Float?,
    val similarityMethod: String?,
    val publishedAtEpochMs: Long?,
    val authorDisplayName: String?,
    val authorUsername: String?,
    val profileUrl: String?,
    val profileBio: String?,
    val websiteUrl: String?,
    val caption: String?,
    val frameTimestampMs: Long?,
    val provenance: Map<String, Provenance>,
    val via: String?,
    val isMock: Boolean,
) {
    fun provenanceOf(field: String): Provenance = provenance[field] ?: Provenance.UNAVAILABLE
}

data class ResultsPage(
    val searchId: String,
    val status: SearchStatus,
    val total: Int,
    val page: Int,
    val pageSize: Int,
    val items: List<SearchResultItem>,
    val counts: Map<ResultFilter, Int>,
)

enum class ReportReason(val apiValue: String) {
    NOT_SIMILAR("not_similar"), WRONG_SOURCE("wrong_source"), WRONG_PROFILE("wrong_profile"),
    BROKEN_LINK("broken_link"), INAPPROPRIATE("inappropriate"), OTHER("other")
}
