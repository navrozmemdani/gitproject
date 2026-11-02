package com.visualtrace.ai.domain.usecase

import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.ExtractedFrame
import com.visualtrace.ai.domain.model.MatchType
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaRules
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ResultCategory
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultSort
import com.visualtrace.ai.domain.model.ResultsPage
import com.visualtrace.ai.domain.model.SearchQuality
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.repository.ConnectivityMonitor
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.MediaRepository
import com.visualtrace.ai.domain.repository.SearchRepository
import javax.inject.Inject

/**
 * Validates and submits a search. For videos the representative frames are extracted on
 * the device, so the full video never leaves the phone unless the user explicitly opted in.
 */
class StartSearchUseCase @Inject constructor(
    private val search: SearchRepository,
    private val media: MediaRepository,
    private val connectivity: ConnectivityMonitor,
) {
    suspend operator fun invoke(request: SearchRequest, settings: AppSettings): Outcome<String> {
        if (!connectivity.currentlyOnline()) return Outcome.Failure(AppError.NoInternet)
        val selection = request.media
        if (selection == null) {
            val text = request.queryText?.trim().orEmpty()
            if (text.length < 2) return Outcome.Failure(AppError.Validation("Add an image, a video or a description."))
            return search.submit(request)
        }
        if (!selection.isRemoteUrl) {
            val check = MediaRules.validate(selection.mimeType, selection.displayName, selection.sizeBytes)
            if (check is Outcome.Failure) return check
        }
        if (selection.kind == MediaKind.VIDEO) {
            val options = request.video ?: return Outcome.Failure(AppError.Validation("Missing video options."))
            if (options.uploadFullVideo) {
                if (!settings.fullVideoUploadConsent) return Outcome.Failure(AppError.ConsentRequired("full_video"))
                return search.submit(request)
            }
            val maxFrames = framesFor(settings)
            val frames = when (val r = media.extractFrames(selection, options.frameIntervalSeconds, options.maxDurationSeconds, maxFrames)) {
                is Outcome.Success -> r.value
                is Outcome.Failure -> return r
            }
            if (frames.isEmpty()) return Outcome.Failure(AppError.UnsupportedFormat("No frames could be read from this video."))
            return try {
                search.submit(request, frames)
            } finally {
                media.cleanupTemporaryFiles()
            }
        }
        return search.submit(request, emptyList<ExtractedFrame>())
    }

    private fun framesFor(settings: AppSettings): Int = when (settings.searchQuality) {
        SearchQuality.FAST -> minOf(settings.maxFramesPerVideo, 8)
        SearchQuality.BALANCED -> settings.maxFramesPerVideo
        SearchQuality.THOROUGH -> maxOf(settings.maxFramesPerVideo, 40)
    }
}

/** Loads results from the server, falling back to the local cache when offline. */
class LoadResultsUseCase @Inject constructor(
    private val search: SearchRepository,
    private val history: HistoryRepository,
) {
    suspend operator fun invoke(searchId: String, filter: ResultFilter, sort: ResultSort): Outcome<ResultsPage> {
        return when (val remote = search.getResults(searchId, filter, sort)) {
            is Outcome.Success -> {
                if (filter == ResultFilter.ALL && remote.value.status.isTerminal) history.cacheResults(remote.value)
                remote
            }
            is Outcome.Failure -> {
                val cached = if (remote.error is AppError.NoInternet) history.cachedResults(searchId) else null
                if (cached != null) Outcome.Success(applyLocally(cached, filter, sort)) else remote
            }
        }
    }

    companion object {
        fun applyLocally(page: ResultsPage, filter: ResultFilter, sort: ResultSort): ResultsPage {
            val filtered = page.items.filter { item ->
                when (filter) {
                    ResultFilter.ALL -> true
                    ResultFilter.EXACT -> item.matchType == MatchType.EXACT
                    ResultFilter.SIMILAR -> item.matchType == MatchType.SIMILAR ||
                        item.matchType == MatchType.CROPPED
                    ResultFilter.SOCIAL -> item.category == ResultCategory.SOCIAL
                    ResultFilter.WEBSITES -> item.category == ResultCategory.WEBSITE
                    ResultFilter.NEWS -> item.category == ResultCategory.NEWS
                }
            }
            val sorted = when (sort) {
                ResultSort.SIMILARITY -> filtered.sortedWith(compareByDescending<SearchResultItem> { it.similarityScore ?: -1f })
                ResultSort.RECENT -> filtered.sortedByDescending { it.publishedAtEpochMs ?: Long.MIN_VALUE }
                ResultSort.RELEVANCE -> filtered
            }
            return page.copy(items = sorted, total = sorted.size)
        }
    }
}
