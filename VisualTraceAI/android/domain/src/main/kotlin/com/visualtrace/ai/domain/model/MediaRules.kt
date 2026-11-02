package com.visualtrace.ai.domain.model

/** Client-side validation that mirrors the backend so users get instant feedback. */
object MediaRules {
    val IMAGE_MIME_TYPES = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
    val VIDEO_MIME_TYPES = setOf("video/mp4", "video/quicktime", "video/webm")
    val IMAGE_EXTENSIONS = setOf("jpg", "jpeg", "png", "webp")
    val VIDEO_EXTENSIONS = setOf("mp4", "mov", "webm")
    const val MAX_IMAGE_BYTES = 15L * 1024 * 1024
    const val MAX_VIDEO_BYTES = 200L * 1024 * 1024

    fun extensionOf(name: String?): String? =
        name?.substringAfterLast('.', missingDelimiterValue = "")?.lowercase()?.takeIf { it.isNotBlank() }

    fun kindFor(mime: String?, name: String?): MediaKind? {
        val m = mime?.lowercase()
        val ext = extensionOf(name)
        return when {
            m in IMAGE_MIME_TYPES -> MediaKind.IMAGE
            m in VIDEO_MIME_TYPES -> MediaKind.VIDEO
            (m == null || m == "application/octet-stream") && ext in IMAGE_EXTENSIONS -> MediaKind.IMAGE
            (m == null || m == "application/octet-stream") && ext in VIDEO_EXTENSIONS -> MediaKind.VIDEO
            else -> null
        }
    }

    fun validate(mime: String?, name: String?, sizeBytes: Long?): Outcome<MediaKind> {
        val kind = kindFor(mime, name)
            ?: return Outcome.Failure(AppError.UnsupportedFormat(mime ?: extensionOf(name)))
        val limit = if (kind == MediaKind.IMAGE) MAX_IMAGE_BYTES else MAX_VIDEO_BYTES
        if (sizeBytes != null && sizeBytes > limit) {
            return Outcome.Failure(AppError.FileTooLarge((limit / (1024 * 1024)).toInt()))
        }
        if (sizeBytes == 0L) return Outcome.Failure(AppError.UnsupportedFormat("empty file"))
        return Outcome.Success(kind)
    }

    fun isValidImageUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (!(trimmed.startsWith("https://") || trimmed.startsWith("http://"))) return false
        val host = trimmed.substringAfter("://").substringBefore('/').substringBefore(':').substringBefore('?')
        return host.contains('.') && host.length in 4..253 && ' ' !in trimmed
    }

    /** Timestamps (ms) of representative frames to sample. */
    fun frameTimestamps(durationMs: Long, intervalSeconds: Float, maxDurationSeconds: Int, maxFrames: Int): List<Long> {
        if (durationMs <= 0) return listOf(0L)
        val interval = (intervalSeconds.coerceIn(AppSettings.MIN_FRAME_INTERVAL, AppSettings.MAX_FRAME_INTERVAL) * 1000).toLong()
        val limit = minOf(durationMs, maxDurationSeconds * 1000L)
        return generateSequence(0L) { it + interval }.takeWhile { it <= limit }.take(maxFrames.coerceAtLeast(1)).toList()
    }
}
