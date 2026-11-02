package com.visualtrace.ai.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class ErrorEnvelope(val error: ErrorBody? = null)

@Serializable
data class ErrorBody(val code: String = "unknown", val message: String? = null, val retryable: Boolean = false)

@Serializable
data class SearchAcceptedDto(
    @SerialName("search_id") val searchId: String,
    val status: String,
)

@Serializable
data class TextSearchDto(val query: String, val providers: List<String>? = null)

@Serializable
data class ProviderReportDto(
    val key: String,
    val name: String,
    val status: String,
    val message: String? = null,
    @SerialName("result_count") val resultCount: Int = 0,
    val searched: Boolean = false,
    @SerialName("is_mock") val isMock: Boolean = false,
)

@Serializable
data class JobStatusDto(val status: String, val stage: String, val progress: Float, val error: String? = null)

@Serializable
data class SearchDetailDto(
    val id: String,
    @SerialName("search_type") val searchType: String,
    val status: String,
    @SerialName("query_text") val queryText: String? = null,
    @SerialName("original_filename") val originalFilename: String? = null,
    @SerialName("preview_thumbnail_b64") val previewThumbnailB64: String? = null,
    @SerialName("result_count") val resultCount: Int = 0,
    @SerialName("created_at") val createdAt: String,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("media_metadata") val mediaMetadata: JsonObject? = null,
    val analysis: JsonObject? = null,
    @SerialName("provider_reports") val providerReports: List<ProviderReportDto>? = null,
    @SerialName("frame_interval_seconds") val frameIntervalSeconds: Float? = null,
    @SerialName("frames_processed") val framesProcessed: Int? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    @SerialName("job_status") val jobStatus: JobStatusDto? = null,
    val disclaimer: String = "",
)

@Serializable
data class SearchResultDto(
    val id: String,
    @SerialName("search_id") val searchId: String,
    @SerialName("provider_key") val providerKey: String,
    @SerialName("also_found_by") val alsoFoundBy: List<String>? = null,
    val platform: String,
    val category: String,
    @SerialName("match_type") val matchType: String,
    @SerialName("source_url") val sourceUrl: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerialName("page_title") val pageTitle: String? = null,
    val description: String? = null,
    @SerialName("similarity_score") val similarityScore: Float? = null,
    @SerialName("similarity_method") val similarityMethod: String? = null,
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("author_display_name") val authorDisplayName: String? = null,
    @SerialName("author_username") val authorUsername: String? = null,
    @SerialName("profile_url") val profileUrl: String? = null,
    @SerialName("profile_bio") val profileBio: String? = null,
    @SerialName("website_url") val websiteUrl: String? = null,
    val caption: String? = null,
    @SerialName("frame_timestamp_ms") val frameTimestampMs: Long? = null,
    @SerialName("field_provenance") val fieldProvenance: Map<String, String>? = null,
    @SerialName("relevance_rank") val relevanceRank: Int = 0,
    @SerialName("is_mock") val isMock: Boolean = false,
)

@Serializable
data class ResultsPageDto(
    @SerialName("search_id") val searchId: String,
    val status: String,
    val total: Int,
    val page: Int,
    @SerialName("page_size") val pageSize: Int,
    val items: List<SearchResultDto>,
    val counts: Map<String, Int> = emptyMap(),
)

@Serializable
data class ReportDto(val reason: String, val comment: String? = null)

@Serializable
data class ProviderInfoDto(
    val key: String,
    val name: String,
    val platform: String,
    val category: String,
    @SerialName("supports_reverse_image") val supportsReverseImage: Boolean,
    @SerialName("uploads_media") val uploadsMedia: Boolean,
    @SerialName("requires_credentials") val requiresCredentials: Boolean = true,
    val configured: Boolean,
    val mode: String,
    val limitations: String = "",
    val alternatives: String = "",
    @SerialName("enabled_by_default") val enabledByDefault: Boolean = true,
    @SerialName("site_search_fallback") val siteSearchFallback: Boolean? = null,
    @SerialName("globally_disabled") val globallyDisabled: Boolean = false,
)

@Serializable
data class ProvidersDto(
    @SerialName("mock_mode") val mockMode: Boolean,
    @SerialName("embedding_model") val embeddingModel: String,
    val providers: List<ProviderInfoDto>,
)

@Serializable
data class FavoriteCreateDto(
    @SerialName("search_result_id") val searchResultId: String? = null,
    @SerialName("search_id") val searchId: String? = null,
    val note: String? = null,
    val snapshot: SearchResultDto? = null,
)

@Serializable
data class FavoriteDto(
    val id: String,
    @SerialName("search_result_id") val searchResultId: String? = null,
    @SerialName("search_id") val searchId: String? = null,
    val snapshot: JsonObject,
    val note: String? = null,
    @SerialName("created_at") val createdAt: String,
)

@Serializable
data class HealthDto(
    val status: String,
    val version: String,
    val database: String,
    @SerialName("embedding_model") val embeddingModel: String,
    @SerialName("mock_mode") val mockMode: Boolean,
)
