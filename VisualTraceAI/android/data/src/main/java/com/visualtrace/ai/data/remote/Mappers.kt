package com.visualtrace.ai.data.remote

import com.visualtrace.ai.data.remote.dto.ProvidersDto
import com.visualtrace.ai.data.remote.dto.ResultsPageDto
import com.visualtrace.ai.data.remote.dto.SearchDetailDto
import com.visualtrace.ai.data.remote.dto.SearchResultDto
import com.visualtrace.ai.domain.model.AnalysisSection
import com.visualtrace.ai.domain.model.ImageAnalysis
import com.visualtrace.ai.domain.model.LabeledValue
import com.visualtrace.ai.domain.model.MatchType
import com.visualtrace.ai.domain.model.Provenance
import com.visualtrace.ai.domain.model.ProviderCatalog
import com.visualtrace.ai.domain.model.ProviderCategory
import com.visualtrace.ai.domain.model.ProviderInfo
import com.visualtrace.ai.domain.model.ProviderMode
import com.visualtrace.ai.domain.model.ProviderReport
import com.visualtrace.ai.domain.model.ProviderRunStatus
import com.visualtrace.ai.domain.model.ResultCategory
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultsPage
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.model.SearchStatus
import com.visualtrace.ai.domain.model.SearchType
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import java.time.Instant
import java.time.OffsetDateTime

fun parseInstant(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toInstant().toEpochMilli() }
        .recoverCatching { Instant.parse(value).toEpochMilli() }
        .getOrNull()
}

fun formatInstant(epochMs: Long?): String? = epochMs?.let { Instant.ofEpochMilli(it).toString() }

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject
private fun JsonObject.str(key: String): String? = (this[key] as? JsonPrimitive)?.contentOrNull
private fun JsonObject.int(key: String): Int? = (this[key] as? JsonPrimitive)?.intOrNull
private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
private fun JsonObject.arr(key: String): JsonArray? = this[key] as? JsonArray
private fun JsonElement.asStr(): String? = (this as? JsonPrimitive)?.contentOrNull

private fun section(obj: JsonObject?): AnalysisSection {
    if (obj == null) return AnalysisSection(emptyList(), "unavailable", "Not provided")
    val items = obj.arr("items").orEmpty().mapNotNull { el ->
        val o = el as? JsonObject ?: return@mapNotNull null
        val label = o.str("label") ?: o.str("name") ?: return@mapNotNull null
        LabeledValue(label, (o["confidence"] as? JsonPrimitive)?.floatOrNull ?: (o["share"] as? JsonPrimitive)?.floatOrNull, o.str("hex"))
    }
    return AnalysisSection(items, obj.str("source") ?: "unavailable", obj.str("reason"))
}

fun JsonObject.toAnalysis(): ImageAnalysis {
    val quality = obj("quality")
    val background = obj("background")
    val ocr = obj("ocr")
    return ImageAnalysis(
        width = int("width"), height = int("height"), orientation = str("orientation"),
        qualityLabel = quality?.str("label"),
        qualityIssues = quality?.arr("issues").orEmpty().mapNotNull { it.asStr() },
        colors = section(obj("colors")),
        backgroundType = background?.str("type"), backgroundColor = background?.str("dominant_color"),
        objects = section(obj("objects")), clothing = section(obj("clothing")), environment = section(obj("environment")),
        ocrText = ocr?.str("text"), ocrSource = ocr?.str("source") ?: "unavailable", ocrReason = ocr?.str("reason"),
        characteristics = arr("characteristics").orEmpty().mapNotNull { it.asStr() },
        keywords = arr("keywords").orEmpty().mapNotNull { it.asStr() },
        embeddingModel = str("embedding_model"),
    )
}

fun SearchDetailDto.toDomain(): SearchSession {
    val meta = mediaMetadata
    return SearchSession(
        id = id,
        type = when (searchType) { "video" -> SearchType.VIDEO; "text" -> SearchType.TEXT; else -> SearchType.IMAGE },
        status = SearchStatus.from(status),
        stage = jobStatus?.stage,
        progress = jobStatus?.progress ?: if (SearchStatus.from(status).isTerminal) 1f else 0f,
        createdAtEpochMs = parseInstant(createdAt) ?: System.currentTimeMillis(),
        completedAtEpochMs = parseInstant(completedAt),
        resultCount = resultCount,
        queryText = queryText,
        originalFilename = originalFilename,
        previewThumbnailBase64 = previewThumbnailB64,
        mediaWidth = meta?.int("width"),
        mediaHeight = meta?.int("height"),
        durationMs = meta?.long("duration_ms"),
        framesProcessed = framesProcessed,
        frameIntervalSeconds = frameIntervalSeconds,
        providerReports = providerReports.orEmpty().map {
            ProviderReport(it.key, it.name, ProviderRunStatus.from(it.status), it.message, it.resultCount, it.searched, it.isMock)
        },
        analysis = analysis?.toAnalysis(),
        errorMessage = errorMessage,
        disclaimer = disclaimer,
    )
}

private fun provenanceOf(raw: String?): Provenance = when (raw) {
    "provider" -> Provenance.PROVIDER
    "measured" -> Provenance.MEASURED
    "inferred" -> Provenance.INFERRED
    else -> Provenance.UNAVAILABLE
}

fun SearchResultDto.toDomain(): SearchResultItem = SearchResultItem(
    id = id, searchId = searchId, providerKey = providerKey, alsoFoundBy = alsoFoundBy.orEmpty(), platform = platform,
    category = ResultCategory.from(category), matchType = MatchType.from(matchType), sourceUrl = sourceUrl,
    imageUrl = imageUrl, thumbnailUrl = thumbnailUrl, pageTitle = pageTitle, description = description,
    similarityScore = similarityScore, similarityMethod = similarityMethod, publishedAtEpochMs = parseInstant(publishedAt),
    authorDisplayName = authorDisplayName, authorUsername = authorUsername, profileUrl = profileUrl, profileBio = profileBio,
    websiteUrl = websiteUrl, caption = caption, frameTimestampMs = frameTimestampMs,
    provenance = fieldProvenance.orEmpty().filterKeys { it != "via" }.mapValues { provenanceOf(it.value) },
    via = fieldProvenance?.get("via"), isMock = isMock,
)

fun SearchResultItem.toDto(): SearchResultDto = SearchResultDto(
    id = id, searchId = searchId, providerKey = providerKey, alsoFoundBy = alsoFoundBy, platform = platform,
    category = category.name.lowercase(), matchType = matchType.name.lowercase(), sourceUrl = sourceUrl,
    imageUrl = imageUrl, thumbnailUrl = thumbnailUrl, pageTitle = pageTitle, description = description,
    similarityScore = similarityScore, similarityMethod = similarityMethod, publishedAt = formatInstant(publishedAtEpochMs),
    authorDisplayName = authorDisplayName, authorUsername = authorUsername, profileUrl = profileUrl, profileBio = profileBio,
    websiteUrl = websiteUrl, caption = caption, frameTimestampMs = frameTimestampMs,
    fieldProvenance = provenance.mapValues { it.value.name.lowercase() } + (via?.let { mapOf("via" to it) } ?: emptyMap()),
    isMock = isMock,
)

fun ResultsPageDto.toDomain(): ResultsPage = ResultsPage(
    searchId = searchId, status = SearchStatus.from(status), total = total, page = page, pageSize = pageSize,
    items = items.map { it.toDomain() },
    counts = ResultFilter.entries.associateWith { counts[it.apiValue] ?: 0 },
)

fun ResultsPage.toDto(): ResultsPageDto = ResultsPageDto(
    searchId = searchId, status = status.name.lowercase(), total = total, page = page, pageSize = pageSize,
    items = items.map { it.toDto() }, counts = counts.mapKeys { it.key.apiValue },
)

fun ProvidersDto.toDomain(): ProviderCatalog = ProviderCatalog(
    mockMode = mockMode, embeddingModel = embeddingModel,
    providers = providers.map {
        ProviderInfo(
            key = it.key, name = it.name, platform = it.platform, category = ProviderCategory.from(it.category),
            supportsReverseImage = it.supportsReverseImage, uploadsMedia = it.uploadsMedia, configured = it.configured,
            mode = ProviderMode.from(it.mode), limitations = it.limitations, alternatives = it.alternatives,
            enabledByDefault = it.enabledByDefault, siteSearchFallback = it.siteSearchFallback == true,
            globallyDisabled = it.globallyDisabled,
        )
    },
)
