package com.visualtrace.ai.di

import com.visualtrace.ai.BuildConfig
import com.visualtrace.ai.data.remote.ApiConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    /** Only non-secret build values are exposed to the app; provider keys stay on the backend. */
    @Provides @Singleton
    fun apiConfig(): ApiConfig = ApiConfig(
        baseUrl = BuildConfig.API_BASE_URL.let { if (it.endsWith("/")) it else "$it/" },
        clientApiKey = BuildConfig.CLIENT_API_KEY,
        debugLogging = BuildConfig.DEBUG,
        userAgent = "VisualTraceAI-Android/${BuildConfig.VERSION_NAME}",
    )
}
