package com.visualtrace.ai.features.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualtrace.ai.data.work.WorkScheduler
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.AppSettings
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.SessionState
import com.visualtrace.ai.domain.repository.AuthRepository
import com.visualtrace.ai.domain.repository.FavoritesRepository
import com.visualtrace.ai.domain.repository.HistoryRepository
import com.visualtrace.ai.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface ProfileMessage {
    data class Synced(val count: Int) : ProfileMessage
    data object Deleted : ProfileMessage
    data class Error(val error: AppError) : ProfileMessage
}

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val auth: AuthRepository,
    private val settingsRepo: SettingsRepository,
    private val favorites: FavoritesRepository,
    private val history: HistoryRepository,
    private val scheduler: WorkScheduler,
) : ViewModel() {
    val session: StateFlow<SessionState> = auth.session.stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)
    val settings: StateFlow<AppSettings> = settingsRepo.settings.stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
    val message = MutableStateFlow<ProfileMessage?>(null)
    val busy = MutableStateFlow(false)

    fun setCloudSync(enabled: Boolean) = viewModelScope.launch {
        settingsRepo.setCloudSync(enabled)
        scheduler.setCloudSync(enabled)
    }

    fun syncNow() = viewModelScope.launch {
        busy.value = true
        message.value = when (val r = favorites.syncWithCloud()) {
            is Outcome.Success -> ProfileMessage.Synced(r.value)
            is Outcome.Failure -> ProfileMessage.Error(r.error)
        }
        busy.value = false
    }

    fun deleteServerData() = viewModelScope.launch {
        busy.value = true
        message.value = when (val r = auth.deleteServerData()) {
            is Outcome.Success -> { history.clear(); ProfileMessage.Deleted }
            is Outcome.Failure -> ProfileMessage.Error(r.error)
        }
        busy.value = false
    }

    fun signOut() = viewModelScope.launch {
        scheduler.setCloudSync(false)
        auth.signOut()
    }

    fun signIn() = viewModelScope.launch { settingsRepo.setGuestModeChosen(false) }
    fun clearMessage() { message.value = null }
}
