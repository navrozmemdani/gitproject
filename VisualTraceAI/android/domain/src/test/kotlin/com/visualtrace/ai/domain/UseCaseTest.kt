package com.visualtrace.ai.domain

import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.ExtractedFrame
import com.visualtrace.ai.domain.model.MatchType
import com.visualtrace.ai.domain.model.MediaKind
import com.visualtrace.ai.domain.model.MediaSelection
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ReportReason
import com.visualtrace.ai.domain.model.ResultCategory
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultSort
import com.visualtrace.ai.domain.model.ResultsPage
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.model.SearchStatus
import com.visualtrace.ai.domain.model.VideoSearchOptions
import com.visualtrace.ai.domain.repository.ConnectivityMonitor
import com.visualtrace.ai.domain.repository.MediaRepository
import com.visualtrace.ai.domain.repository.SearchRepository
import com.visualtrace.ai.domain.usecase.LoadResultsUseCase
import com.visualtrace.ai.domain.usecase.StartSearchUseCase
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeSearch : SearchRepository {
    var submitted: Pair<SearchRequest, List<ExtractedFrame>>? = null
    override suspend fun submit(request: SearchRequest, frames: List<ExtractedFrame>): Outcome<String> {
        submitted = request to frames
        return Outcome.Success("sid")
    }
    override fun observe(searchId: String): Flow<Outcome<SearchSession>> = emptyFlow()
    override suspend fun getSession(searchId: String): Outcome<SearchSession> = Outcome.Failure(AppError.NotFound())
    override suspend fun getResults(searchId: String, filter: ResultFilter, sort: ResultSort, page: Int, pageSize: Int) =
        Outcome.Failure(AppError.NoInternet)
    override suspend fun report(resultId: String, reason: ReportReason, comment: String?) = Outcome.Success(Unit)
}

class FakeMedia : MediaRepository {
    var cleaned = false
    override suspend fun inspect(uri: String): Outcome<MediaSelection> = Outcome.Failure(AppError.NotFound())
    override suspend fun extractFrames(media: MediaSelection, intervalSeconds: Float, maxDurationSeconds: Int, maxFrames: Int) =
        Outcome.Success(listOf(ExtractedFrame("/f0.jpg", 0), ExtractedFrame("/f1.jpg", 3000)))
    override suspend fun createCameraCaptureUri() = "content://x"
    override suspend fun cleanupTemporaryFiles(olderThanMs: Long) { cleaned = true }
}

class Online(private val online: Boolean) : ConnectivityMonitor {
    override val isOnline = flowOf(online)
    override fun currentlyOnline() = online
}

class UseCaseTest {
    private val video = MediaSelection("content://v", MediaKind.VIDEO, "video/mp4", "v.mp4", 1000, durationMs = 9000)
    private val options = VideoSearchOptions(3f, 60, uploadFullVideo = false)

    @Test fun `offline searches fail fast`() = runTest {
        val r = StartSearchUseCase(FakeSearch(), FakeMedia(), Online(false))(SearchRequest(null, "car", null, false), AppSettings())
        assertEquals(Outcome.Failure(AppError.NoInternet), r)
    }

    @Test fun `video frames are extracted on device and cleaned up`() = runTest {
        val search = FakeSearch(); val media = FakeMedia()
        val r = StartSearchUseCase(search, media, Online(true))(SearchRequest(video, null, null, false, options), AppSettings())
        assertEquals(Outcome.Success("sid"), r)
        assertEquals(2, search.submitted!!.second.size)
        assertTrue(media.cleaned)
    }

    @Test fun `full video upload requires consent`() = runTest {
        val r = StartSearchUseCase(FakeSearch(), FakeMedia(), Online(true))(
            SearchRequest(video, null, null, false, options.copy(uploadFullVideo = true)), AppSettings())
        assertTrue(r is Outcome.Failure && r.error is AppError.ConsentRequired)
    }

    @Test fun `text search needs a description`() = runTest {
        val r = StartSearchUseCase(FakeSearch(), FakeMedia(), Online(true))(SearchRequest(null, " ", null, false), AppSettings())
        assertTrue(r is Outcome.Failure && r.error is AppError.Validation)
    }

    @Test fun `local filtering and sorting of cached results`() {
        fun item(id: String, score: Float?, type: MatchType, cat: ResultCategory, date: Long?) = SearchResultItem(
            id, "s", "p", emptyList(), "Web", cat, type, "https://x/$id", null, null, null, null, score, null, date,
            null, null, null, null, null, null, null, emptyMap(), null, false)
        val page = ResultsPage("s", SearchStatus.COMPLETED, 3, 1, 50, listOf(
            item("a", 0.5f, MatchType.SIMILAR, ResultCategory.SOCIAL, 3),
            item("b", 0.99f, MatchType.EXACT, ResultCategory.WEBSITE, 1),
            item("c", null, MatchType.RELATED, ResultCategory.NEWS, 5)), emptyMap())
        assertEquals(listOf("b", "a", "c"), LoadResultsUseCase.applyLocally(page, ResultFilter.ALL, ResultSort.SIMILARITY).items.map { it.id })
        assertEquals(listOf("c", "a", "b"), LoadResultsUseCase.applyLocally(page, ResultFilter.ALL, ResultSort.RECENT).items.map { it.id })
        assertEquals(listOf("a"), LoadResultsUseCase.applyLocally(page, ResultFilter.SOCIAL, ResultSort.SIMILARITY).items.map { it.id })
        assertEquals(listOf("b"), LoadResultsUseCase.applyLocally(page, ResultFilter.EXACT, ResultSort.SIMILARITY).items.map { it.id })
    }
}
