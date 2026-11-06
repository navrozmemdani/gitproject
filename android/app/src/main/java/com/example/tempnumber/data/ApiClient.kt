package com.example.tempnumber.data

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/** Builds a Retrofit ApiService bound to a base URL + API key. */
object ApiClient {
    private val json = Json { ignoreUnknownKeys = true }

    fun create(baseUrl: String, apiKey: String): ApiService {
        val normalized = if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"

        val client = OkHttpClient.Builder()
            .addInterceptor { chain ->
                val req = chain.request().newBuilder()
                    .addHeader("X-API-Key", apiKey)
                    .build()
                chain.proceed(req)
            }
            .build()

        return Retrofit.Builder()
            .baseUrl(normalized)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(ApiService::class.java)
    }
}
