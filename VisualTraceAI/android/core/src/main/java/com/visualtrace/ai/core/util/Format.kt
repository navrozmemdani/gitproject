package com.visualtrace.ai.core.util

import android.util.Base64
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

object Format {
    fun dateTime(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, locale).format(Date(epochMs))

    fun date(epochMs: Long, locale: Locale = Locale.getDefault()): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, locale).format(Date(epochMs))

    /** 83500 -> "1:23"; 3723000 -> "1:02:03". */
    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun percent(score: Float): String = "${(score.coerceIn(0f, 1f) * 100).roundToInt()}%"

    fun fileSize(bytes: Long): String = when {
        bytes >= 1024L * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024))
        bytes >= 1024 -> String.format(Locale.US, "%.0f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun host(url: String): String = url.substringAfter("://").substringBefore('/').removePrefix("www.")
}

/** Coil-friendly model: data: URIs become ByteArrays, everything else is passed through. */
fun imageModel(url: String?): Any? {
    if (url == null) return null
    if (url.startsWith("data:")) {
        val b64 = url.substringAfter("base64,", missingDelimiterValue = "")
        return runCatching { Base64.decode(b64, Base64.DEFAULT) }.getOrNull()
    }
    return url
}

fun base64Image(b64: String?): ByteArray? = b64?.let { runCatching { Base64.decode(it, Base64.DEFAULT) }.getOrNull() }
