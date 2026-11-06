package com.example.tempnumber.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds Retrofit ApiService instances bound to a base URL. */
object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    private fun retrofit(baseUrl: String, client: OkHttpClient): ApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }

    /** Client for register/login — no auth header. */
    fun createAuth(baseUrl: String): ApiService =
        retrofit(baseUrl, OkHttpClient.Builder().build())

    /** Client for authenticated calls — sends Authorization: Bearer <token>. */
    fun create(baseUrl: String, token: String): ApiService {
        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("Authorization", "Bearer $token")
                    .build()
                chain.proceed(req)
            }
            .build()
        return retrofit(baseUrl, client)
    }
}
