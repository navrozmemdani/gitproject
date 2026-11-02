package com.visualtrace.ai.domain

import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaRules
import com.visualtrace.ai.domain.model.Outcome
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRulesTest {
    @Test fun `accepts supported image and video formats`() {
        listOf("image/jpeg" to "a.jpg", "image/png" to "a.png", "image/webp" to "a.webp", null to "a.JPEG").forEach { (m, n) ->
            assertEquals(Outcome.Success(MediaKind.IMAGE), MediaRules.validate(m, n, 1000))
        }
        listOf("video/mp4" to "a.mp4", "video/quicktime" to "a.mov", "video/webm" to "a.webm").forEach { (m, n) ->
            assertEquals(Outcome.Success(MediaKind.VIDEO), MediaRules.validate(m, n, 1000))
        }
    }

    @Test fun `rejects unsupported formats`() {
        val r = MediaRules.validate("image/gif", "a.gif", 100)
        assertTrue(r is Outcome.Failure && r.error is AppError.UnsupportedFormat)
        assertTrue(MediaRules.validate("video/x-msvideo", "a.avi", 100) is Outcome.Failure)
    }

    @Test fun `rejects oversized and empty files`() {
        val big = MediaRules.validate("image/png", "a.png", MediaRules.MAX_IMAGE_BYTES + 1)
        assertTrue(big is Outcome.Failure && (big.error as AppError.FileTooLarge).limitMb == 15)
        assertTrue(MediaRules.validate("video/mp4", "a.mp4", MediaRules.MAX_VIDEO_BYTES + 1) is Outcome.Failure)
        assertTrue(MediaRules.validate("image/png", "a.png", 0) is Outcome.Failure)
    }

    @Test fun `validates pasted urls`() {
        assertTrue(MediaRules.isValidImageUrl("https://example.com/a.jpg"))
        assertFalse(MediaRules.isValidImageUrl("ftp://example.com/a.jpg"))
        assertFalse(MediaRules.isValidImageUrl("javascript:alert(1)"))
        assertFalse(MediaRules.isValidImageUrl("https://localhost/a.jpg"))
        assertFalse(MediaRules.isValidImageUrl("https://exa mple.com/a.jpg"))
    }

    @Test fun `frame timestamps honour interval, duration and frame limits`() {
        assertEquals(listOf(0L, 3000L, 6000L, 9000L), MediaRules.frameTimestamps(10_000, 3f, 600, 40))
        assertEquals(listOf(0L, 3000L), MediaRules.frameTimestamps(60_000, 3f, 5, 40))
        assertEquals(5, MediaRules.frameTimestamps(600_000, 1f, 600, 5).size)
        assertEquals(listOf(0L), MediaRules.frameTimestamps(0, 3f, 60, 10))
        // interval is clamped to the minimum 0.5 s
        assertEquals(listOf(0L, 500L, 1000L), MediaRules.frameTimestamps(1000, 0.1f, 60, 10))
    }
}
