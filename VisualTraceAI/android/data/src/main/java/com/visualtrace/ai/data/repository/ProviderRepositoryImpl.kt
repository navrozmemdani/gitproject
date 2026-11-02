package com.visualtrace.ai.data.repository

import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.remote.apiCall
import com.visualtrace.ai.data.remote.toDomain
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ProviderCatalog
import com.visualtrace.ai.domain.repository.ProviderRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProviderRepositoryImpl @Inject constructor(private val api: VisualTraceApi) : ProviderRepository {
    private val mutex = Mutex()
    private var cached: Pair<Long, ProviderCatalog>? = null

    override suspend fun catalog(forceRefresh: Boolean): Outcome<ProviderCatalog> = mutex.withLock {
        val now = System.currentTimeMillis()
        cached?.let { (at, value) -> if (!forceRefresh && now - at < 5 * 60_000) return Outcome.Success(value) }
        when (val r = apiCall { api.providers() }) {
            is Outcome.Success -> r.value.toDomain().also { cached = now to it }.let { Outcome.Success(it) }
            is Outcome.Failure -> cached?.let { Outcome.Success(it.second) } ?: r
        }
    }
}
