package com.visualtrace.ai.data

import com.visualtrace.ai.data.remote.ServerUrlInterceptor
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.repository.SettingsRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServerUrlInterceptorTest {
    @Test fun `parses addresses with and without scheme`() {
        assertEquals("http://192.168.1.20:8000/", ServerUrlInterceptor.parseServerUrl("192.168.1.20:8000").toString())
        assertEquals("https://api.example.com/", ServerUrlInterceptor.parseServerUrl(" https://api.example.com ").toString())
        assertNull(ServerUrlInterceptor.parseServerUrl(""))
        assertNull(ServerUrlInterceptor.parseServerUrl("http://exa mple"))
    }

    @Test fun `requests are redirected to the configured server`() {
        val server = MockWebServer().apply { start() }
        server.enqueue(MockResponse().setBody("ok"))
        val settings = mockk<SettingsRepository> {
            every { this@mockk.settings } returns flowOf(AppSettings(serverUrl = server.url("/").toString()))
        }
        val client = OkHttpClient.Builder().addInterceptor(ServerUrlInterceptor(settings)).build()
        client.newCall(Request.Builder().url("https://api.visualtrace.ai/api/v1/health?x=1").build()).execute().use {
            assertEquals("ok", it.body!!.string())
        }
        assertEquals("/api/v1/health?x=1", server.takeRequest().path)
        server.shutdown()
    }
}
