package com.visualtrace.ai.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.visualtrace.ai.data.local.FavoritesDao
import com.visualtrace.ai.data.local.HistoryDao
import com.visualtrace.ai.data.local.PendingDeletionDao
import com.visualtrace.ai.data.local.VisualTraceDatabase
import com.visualtrace.ai.data.media.MediaRepositoryImpl
import com.visualtrace.ai.data.remote.ApiConfig
import com.visualtrace.ai.data.remote.ApiJson
import com.visualtrace.ai.data.remote.FirebaseAuthHolder
import com.visualtrace.ai.data.remote.IdentityInterceptor
import com.visualtrace.ai.data.remote.TokenRefreshAuthenticator
import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.repository.AuthRepositoryImpl
import com.visualtrace.ai.data.repository.FavoritesRepositoryImpl
import com.visualtrace.ai.data.repository.HistoryRepositoryImpl
import com.visualtrace.ai.data.repository.ProviderRepositoryImpl
import com.visualtrace.ai.data.repository.SearchRepositoryImpl
import com.visualtrace.ai.data.settings.SettingsRepositoryImpl
import com.visualtrace.ai.data.system.ConnectivityMonitorImpl
import com.visualtrace.ai.data.work.WorkScheduler
import com.visualtrace.ai.domain.repository.AuthRepository
import com.visualtrace.ai.domain.repository.ConnectivityMonitor
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.MediaRepository
import com.visualtrace.ai.domain.repository.ProviderRepository
import com.visualtrace.ai.domain.repository.SearchRepository
import com.visualtrace.ai.domain.repository.SearchSubmissionQueue
import com.visualtrace.ai.domain.repository.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidesModule {

    @Provides @Singleton
    fun okHttp(config: ApiConfig, identity: IdentityInterceptor, authenticator: TokenRefreshAuthenticator): OkHttpClient =
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .addInterceptor(identity)
            .authenticator(authenticator)
            .apply {
                if (config.debugLogging) {
                    addInterceptor(HttpLoggingInterceptor().apply {
                        level = HttpLoggingInterceptor.Level.BASIC
                        redactHeader("Authorization")
                        redactHeader("X-API-Key")
                    })
                }
            }
            .build()

    @Provides @Singleton
    fun retrofit(config: ApiConfig, client: OkHttpClient): Retrofit = Retrofit.Builder()
        .baseUrl(config.baseUrl)
        .client(client)
        .addConverterFactory(ApiJson.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun api(retrofit: Retrofit): VisualTraceApi = retrofit.create(VisualTraceApi::class.java)

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): VisualTraceDatabase =
        Room.databaseBuilder(context, VisualTraceDatabase::class.java, "visualtrace.db").build()

    @Provides fun historyDao(db: VisualTraceDatabase): HistoryDao = db.historyDao()
    @Provides fun favoritesDao(db: VisualTraceDatabase): FavoritesDao = db.favoritesDao()
    @Provides fun pendingDao(db: VisualTraceDatabase): PendingDeletionDao = db.pendingDeletionDao()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("settings") }

    @Provides @Singleton
    fun firebase(@ApplicationContext context: Context): FirebaseAuthHolder {
        val configured = FirebaseApp.getApps(context).isNotEmpty() || runCatching { FirebaseApp.initializeApp(context) != null }.getOrDefault(false)
        return FirebaseAuthHolder(if (configured) runCatching { FirebaseAuth.getInstance() }.getOrNull() else null)
    }
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds abstract fun search(impl: SearchRepositoryImpl): SearchRepository
    @Binds abstract fun history(impl: HistoryRepositoryImpl): HistoryRepository
    @Binds abstract fun favorites(impl: FavoritesRepositoryImpl): FavoritesRepository
    @Binds abstract fun providers(impl: ProviderRepositoryImpl): ProviderRepository
    @Binds abstract fun settings(impl: SettingsRepositoryImpl): SettingsRepository
    @Binds abstract fun auth(impl: AuthRepositoryImpl): AuthRepository
    @Binds abstract fun media(impl: MediaRepositoryImpl): MediaRepository
    @Binds abstract fun connectivity(impl: ConnectivityMonitorImpl): ConnectivityMonitor
    @Binds abstract fun submissions(impl: WorkScheduler): SearchSubmissionQueue
}
