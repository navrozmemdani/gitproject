package com.visualtrace.ai.features.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualtrace.ai.domain.model.HistoryEntry
import com.visualtrace.ai.domain.model.SearchType
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    settings: SettingsRepository,
) : ViewModel() {
    val filter = MutableStateFlow<SearchType?>(null)

    val entries: StateFlow<List<HistoryEntry>?> = filter.flatMapLatest { history.observe(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val historyEnabled: StateFlow<Boolean> = settings.settings.map { it.historyEnabled }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setFilter(type: SearchType?) { filter.value = type }
    fun delete(id: String) = viewModelScope.launch { history.delete(id) }
    fun clearAll() = viewModelScope.launch { history.clear() }
}
