package com.visualtrace.ai.features.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.ResultCollection
import com.visualtrace.ai.domain.model.SavedResult
import com.visualtrace.ai.domain.repository.FavoritesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class FavoritesViewModel @Inject constructor(private val favorites: FavoritesRepository) : ViewModel() {
    val selectedCollection = MutableStateFlow<String?>(null)
    val error = MutableStateFlow<AppError?>(null)

    val items: StateFlow<List<SavedResult>?> = selectedCollection.flatMapLatest { favorites.observeSaved(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val collections: StateFlow<List<ResultCollection>> = favorites.observeCollections()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun select(id: String?) { selectedCollection.value = id }

    fun createCollection(name: String) = viewModelScope.launch {
        val r = favorites.createCollection(name)
        if (r is Outcome.Failure) error.value = r.error
    }

    fun deleteCollection(id: String) = viewModelScope.launch {
        favorites.deleteCollection(id)
        if (selectedCollection.value == id) selectedCollection.value = null
    }

    fun updateNote(id: String, note: String?) = viewModelScope.launch { favorites.updateNote(id, note) }
    fun move(id: String, collectionId: String?) = viewModelScope.launch { favorites.moveToCollection(id, collectionId) }
    fun remove(id: String) = viewModelScope.launch { favorites.remove(id) }
    fun clearError() { error.value = null }
}
