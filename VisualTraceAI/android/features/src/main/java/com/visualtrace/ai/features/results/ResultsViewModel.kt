package com.visualtrace.ai.features.results

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ReportReason
import com.visualtrace.ai.domain.model.ResultCollection
import com.visualtrace.ai.domain.model.ResultFilter
import com.visualtrace.ai.domain.model.ResultSort
import com.visualtrace.ai.domain.model.SearchRequest
import com.visualtrace.ai.domain.model.SearchResultItem
import com.visualtrace.ai.domain.model.SearchSession
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.SearchRepository
import com.visualtrace.ai.domain.repository.SearchSubmissionQueue
import com.visualtrace.ai.domain.repository.SubmissionState
import com.visualtrace.ai.domain.usecase.LoadResultsUseCase
import com.visualtrace.ai.features.navigation.ResultsRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ResultsPhase {
    data class Submitting(val state: SubmissionState) : ResultsPhase
    data object Tracking : ResultsPhase
    data class SubmitFailed(val error: AppError) : ResultsPhase
}

data class ResultsUiState(
    val phase: ResultsPhase = ResultsPhase.Tracking,
    val searchId: String? = null,
    val session: SearchSession? = null,
    val trackingError: AppError? = null,
    val filter: ResultFilter = ResultFilter.ALL,
    val sort: ResultSort = ResultSort.SIMILARITY,
    val items: List<SearchResultItem> = emptyList(),
    val counts: Map<ResultFilter, Int> = emptyMap(),
    val loadingResults: Boolean = false,
    val resultsError: AppError? = null,
    val resultsLoaded: Boolean = false,
    val fromCache: Boolean = false,
)

sealed interface ResultsEvent {
    data class Message(val text: MessageKey) : ResultsEvent
    data class OpenTicket(val ticket: String) : ResultsEvent
}

enum class MessageKey { SAVED, REMOVED, REPORTED, COPIED, ERROR }

@HiltViewModel
class ResultsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val queue: SearchSubmissionQueue,
    private val search: SearchRepository,
    private val loadResults: LoadResultsUseCase,
    private val favorites: FavoritesRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<ResultsRoute>()
    private val _state = MutableStateFlow(ResultsUiState(searchId = route.searchId))
    val state: StateFlow<ResultsUiState> = _state.asStateFlow()

    /** resultId -> savedId for the save/unsave toggle. */
    val savedIds: StateFlow<Map<String, String>> = favorites.observeSaved()
        .map { list -> list.associate { it.result.id to it.id } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    val collections: StateFlow<List<ResultCollection>> = favorites.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val events = Channel<ResultsEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()
    private var trackJob: Job? = null

    init {
        when {
            route.searchId != null -> track(route.searchId)
            route.ticket != null -> awaitSubmission(route.ticket)
            else -> _state.update { it.copy(phase = ResultsPhase.SubmitFailed(AppError.NotFound())) }
        }
    }

    private fun awaitSubmission(ticket: String) {
        _state.update { it.copy(phase = ResultsPhase.Submitting(SubmissionState.Waiting)) }
        viewModelScope.launch {
            val final = queue.observe(ticket)
                .onEach { s -> _state.update { it.copy(phase = ResultsPhase.Submitting(s)) } }
                .first { it is SubmissionState.Submitted || it is SubmissionState.Failed }
            handle(final)
        }
    }

    private fun handle(s: SubmissionState) {
        when (s) {
            is SubmissionState.Submitted -> track(s.searchId)
            is SubmissionState.Failed -> _state.update { it.copy(phase = ResultsPhase.SubmitFailed(s.error)) }
            else -> Unit
        }
    }

    private fun track(searchId: String) {
        trackJob?.cancel()
        _state.update { it.copy(phase = ResultsPhase.Tracking, searchId = searchId, trackingError = null) }
        trackJob = viewModelScope.launch {
            search.observe(searchId).collect { r ->
                when (r) {
                    is Outcome.Success -> {
                        _state.update { it.copy(session = r.value, trackingError = null) }
                        if (r.value.status.isTerminal) refreshResults()
                    }
                    is Outcome.Failure -> {
                        _state.update { it.copy(trackingError = r.error) }
                        if (r.error is AppError.NoInternet && !_state.value.resultsLoaded) refreshResults()
                    }
                }
            }
        }
    }

    fun retry() {
        val id = _state.value.searchId ?: return
        if (_state.value.session?.status?.isTerminal == true) refreshResults() else track(id)
    }

    fun setFilter(filter: ResultFilter) {
        if (filter == _state.value.filter) return
        _state.update { it.copy(filter = filter) }
        refreshResults()
    }

    fun setSort(sort: ResultSort) {
        if (sort == _state.value.sort) return
        _state.update { it.copy(sort = sort) }
        refreshResults()
    }

    private fun refreshResults() {
        val id = _state.value.searchId ?: return
        _state.update { it.copy(loadingResults = true, resultsError = null) }
        viewModelScope.launch {
            val st = _state.value
            when (val r = loadResults(id, st.filter, st.sort)) {
                is Outcome.Success -> _state.update {
                    it.copy(items = r.value.items, counts = if (r.value.counts.values.any { c -> c > 0 }) r.value.counts else it.counts,
                        loadingResults = false, resultsLoaded = true, fromCache = it.trackingError is AppError.NoInternet)
                }
                is Outcome.Failure -> _state.update { it.copy(loadingResults = false, resultsError = r.error) }
            }
        }
    }

    fun toggleSave(item: SearchResultItem, collectionId: String?, note: String?) = viewModelScope.launch {
        val savedId = savedIds.value[item.id]
        if (savedId != null) {
            favorites.remove(savedId)
            events.send(ResultsEvent.Message(MessageKey.REMOVED))
        } else {
            favorites.save(item, collectionId, note)
            events.send(ResultsEvent.Message(MessageKey.SAVED))
        }
    }

    fun createCollection(name: String) = viewModelScope.launch { favorites.createCollection(name) }

    fun report(item: SearchResultItem, reason: ReportReason, comment: String?) = viewModelScope.launch {
        val r = search.report(item.id, reason, comment)
        events.send(ResultsEvent.Message(if (r is Outcome.Success) MessageKey.REPORTED else MessageKey.ERROR))
    }

    fun copied() = viewModelScope.launch { events.send(ResultsEvent.Message(MessageKey.COPIED)) }

    /** Text search using the AI-derived characteristics of this image (spec 10). */
    fun searchByCharacteristics(text: String) {
        if (text.isBlank()) return
        val ticket = queue.enqueue(SearchRequest(media = null, queryText = text.take(300), providers = null, thirdPartyConsent = false))
        events.trySend(ResultsEvent.OpenTicket(ticket))
    }
}
