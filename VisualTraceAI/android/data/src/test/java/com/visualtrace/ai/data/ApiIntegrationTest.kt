package com.visualtrace.ai.data

import com.visualtrace.ai.data.remote.ApiConfig
import com.visualtrace.ai.data.remote.ApiJson
import com.visualtrace.ai.data.remote.FirebaseAuthHolder
import com.visualtrace.ai.data.remote.IdentityInterceptor
import com.visualtrace.ai.data.remote.TokenProvider
import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.remote.apiCall
import com.visualtrace.ai.data.remote.apiUnit
import com.visualtrace.ai.data.remote.toDomain
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.repository.SettingsRepository
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Exercises the real Retrofit interface, identity headers and error envelope against MockWebServer. */
class ApiIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var api: VisualTraceApi
    private val guestId = "3f1c2a8e-1d4b-4c6e-9f2a-7b8c9d0e1f2a"

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        val settings = mockk<SettingsRepository> { coEvery { guestId() } returns guestId }
        val config = ApiConfig(server.url("/").toString(), "client-key", false, "VT-Test")
        val client = OkHttpClient.Builder()
            .addInterceptor(IdentityInterceptor(config, TokenProvider(FirebaseAuthHolder(null)), settings))
            .build()
        api = Retrofit.Builder().baseUrl(config.baseUrl).client(client)
            .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType())).build()
            .create(VisualTraceApi::class.java)
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `image upload sends multipart, guest identity and client key`() = runTest {
        server.enqueue(MockResponse().setResponseCode(202).setBody("""{"search_id":"abc","status":"queued","status_url":"x","results_url":"y"}"""))
        val bytes = requireNotNull(javaClass.classLoader).getResource("sample.jpg")!!.readBytes()
        val part = MultipartBody.Part.createFormData("file", "sample.jpg", bytes.toRequestBody("image/jpeg".toMediaType()))
        val text = "text/plain".toMediaType()
        val r = apiCall { api.searchImage(part, null, "google_cse".toRequestBody(text), "false".toRequestBody(text), null, "true".toRequestBody(text)) }
        assertEquals("abc", (r as Outcome.Success).value.searchId)
        val req = server.takeRequest()
        assertEquals("/api/v1/search/image", req.path)
        assertEquals(guestId, req.getHeader("X-Guest-Id"))
        assertEquals("client-key", req.getHeader("X-API-Key"))
        assertNull(req.getHeader("Authorization"))
        val body = req.body.readUtf8()
        assertTrue(body.contains("name=\"file\"; filename=\"sample.jpg\""))
        assertTrue(body.contains("name=\"providers\""))
        assertTrue(!body.contains("name=\"image_url\"")) // null parts are omitted
    }

    @Test fun `results are fetched with filter and sort query`() = runTest {
        server.enqueue(MockResponse().setBody(resource("results_page.json")))
        val r = apiCall { api.getResults("sid", "social", "recent", 1, 50) }
        assertEquals(2, (r as Outcome.Success).value.toDomain().items.size)
        assertEquals("/api/v1/search/sid/results?filter=social&sort=recent&page=1&page_size=50", server.takeRequest().path)
    }

    @Test fun `error envelope becomes typed error`() = runTest {
        server.enqueue(MockResponse().setResponseCode(415).setBody("""{"error":{"code":"unsupported_media_type","message":"Use JPG","retryable":false}}"""))
        val r = apiCall { api.getSearch("x") }
        assertTrue((r as Outcome.Failure).error is AppError.UnsupportedFormat)
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "12").setBody("""{"error":{"code":"rate_limited","message":"slow","retryable":true}}"""))
        assertEquals(AppError.RateLimited(12), (apiUnit { api.deleteHistory("x") } as Outcome.Failure).error)
        server.enqueue(MockResponse().setResponseCode(204))
        assertTrue(apiUnit { api.clearHistory() } is Outcome.Success)
    }

    @Test fun `provider catalog parses and keeps honest modes`() = runTest {
        server.enqueue(MockResponse().setBody("""{"mock_mode":false,"embedding_model":"opencv","providers":[
            {"key":"google_cse","name":"Google","platform":"Google","category":"web","supports_reverse_image":false,"uploads_media":false,
             "configured":false,"mode":"unavailable","credential_env":["GOOGLE_CSE_API_KEY"],"limitations":"x","alternatives":"","enabled_by_default":true,"homepage":""}]}"""))
        val c = (apiCall { api.providers() } as Outcome.Success).value.toDomain()
        assertEquals(com.visualtrace.ai.domain.model.ProviderMode.UNAVAILABLE, c.providers.single().mode)
    }

    @Test fun `server down maps to no internet`() = runTest {
        server.shutdown()
        assertEquals(AppError.NoInternet, (apiCall { api.health() } as Outcome.Failure).error)
    }
}
