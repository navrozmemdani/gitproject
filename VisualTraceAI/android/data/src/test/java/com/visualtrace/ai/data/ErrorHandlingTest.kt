package com.visualtrace.ai.data

import com.visualtrace.ai.data.remote.ErrorMapper
import com.visualtrace.ai.data.work.ErrorCodec
import com.visualtrace.ai.domain.model.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class ErrorHandlingTest {
    private fun body(code: String, msg: String = "m", retryable: Boolean = false) =
        """{"error":{"code":"$code","message":"$msg","retryable":$retryable}}"""

    @Test fun `backend error codes map to user facing errors`() {
        assertTrue(ErrorMapper.fromHttp(415, body("unsupported_media_type"), null) is AppError.UnsupportedFormat)
        assertTrue(ErrorMapper.fromHttp(413, body("file_too_large"), null) is AppError.FileTooLarge)
        assertTrue(ErrorMapper.fromHttp(422, body("suspicious_file"), null) is AppError.SuspiciousFile)
        assertEquals(AppError.RateLimited(30), ErrorMapper.fromHttp(429, body("rate_limited"), "30"))
        assertTrue(ErrorMapper.fromHttp(401, body("authentication_failed"), null) is AppError.Authentication)
        assertEquals(AppError.AccountRequired, ErrorMapper.fromHttp(403, body("account_required"), null))
        assertEquals(AppError.AuthNotConfigured, ErrorMapper.fromHttp(503, body("auth_not_configured"), null))
        assertTrue(ErrorMapper.fromHttp(428, body("consent_required"), null) is AppError.ConsentRequired)
        assertEquals(AppError.Validation("bad"), ErrorMapper.fromHttp(422, body("unknown_provider", "bad"), null))
        val server = ErrorMapper.fromHttp(500, body("internal_error", "boom", true), null) as AppError.Server
        assertTrue(server.retryable)
    }

    @Test fun `non json and transport failures`() {
        assertEquals(AppError.Server("http_502", null, true), ErrorMapper.fromHttp(502, "<html>bad gateway</html>", null))
        assertEquals(AppError.RateLimited(null), ErrorMapper.fromHttp(429, null, null))
        assertEquals(AppError.NoInternet, ErrorMapper.fromThrowable(UnknownHostException("x")))
        assertEquals(AppError.Timeout, ErrorMapper.fromThrowable(SocketTimeoutException()))
    }

    @Test fun `error codec round trips through work data`() {
        listOf(
            AppError.NoInternet, AppError.UnsupportedFormat("gif"), AppError.FileTooLarge(15), AppError.RateLimited(10),
            AppError.Timeout, AppError.ConsentRequired("full_video"), AppError.Validation("x|y"),
            AppError.Server("internal", "msg", true), AppError.AuthNotConfigured, AppError.AccountRequired,
        ).forEach { e ->
            val decoded = ErrorCodec.decode(ErrorCodec.encode(e))
            if (e is AppError.Validation) assertEquals(AppError.Validation("x|y"), decoded) else assertEquals(e, decoded)
        }
    }
}
