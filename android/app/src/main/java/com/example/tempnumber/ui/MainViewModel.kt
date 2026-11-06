package com.example.tempnumber.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tempnumber.data.ApiClient
import com.example.tempnumber.data.ApiService
import com.example.tempnumber.data.AvailableNumber
import com.example.tempnumber.data.RentRequest
import com.example.tempnumber.data.RentedNumber
import com.example.tempnumber.data.Settings
import com.example.tempnumber.data.SettingsStore
import com.example.tempnumber.data.SmsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UiState(
    val settings: Settings = Settings(),
    val configured: Boolean = false,
    val numbers: List<RentedNumber> = emptyList(),
    val available: List<AvailableNumber> = emptyList(),
    val messages: List<SmsMessage> = emptyList(),
    val selectedNumber: String? = null,
    val loading: Boolean = false,
    val error: String? = null,
)

class MainViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SettingsStore(app)
    private var api: ApiService? = null

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val s = store.settings.first()
            applySettings(s)
            if (_state.value.configured) refreshNumbers()
        }
    }

    private fun applySettings(s: Settings) {
        val configured = s.baseUrl.isNotBlank() && s.apiKey.isNotBlank()
        api = if (configured) ApiClient.create(s.baseUrl, s.apiKey) else null
        _state.value = _state.value.copy(settings = s, configured = configured)
    }

    fun saveSettings(baseUrl: String, apiKey: String) {
        viewModelScope.launch {
            store.save(baseUrl, apiKey)
            applySettings(store.settings.first())
            if (_state.value.configured) refreshNumbers()
        }
    }

    private inline fun run(crossinline block: suspend (ApiService) -> Unit) {
        val svc = api ?: run {
            _state.value = _state.value.copy(error = "Set the backend URL and API key first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                block(svc)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = e.message ?: "Request failed")
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun refreshNumbers() = run { svc ->
        _state.value = _state.value.copy(numbers = svc.numbers().numbers)
    }

    fun searchAvailable(areaCode: String?) = run { svc ->
        _state.value = _state.value.copy(
            available = svc.availableNumbers(areaCode = areaCode?.ifBlank { null }).numbers,
        )
    }

    fun rent(phoneNumber: String) = run { svc ->
        svc.rent(RentRequest(phoneNumber))
        _state.value = _state.value.copy(
            available = emptyList(),
            numbers = svc.numbers().numbers,
        )
    }

    fun release(phoneNumber: String) = run { svc ->
        svc.release(phoneNumber)
        _state.value = _state.value.copy(numbers = svc.numbers().numbers)
    }

    fun openMessages(phoneNumber: String) = run { svc ->
        _state.value = _state.value.copy(
            selectedNumber = phoneNumber,
            messages = svc.messages(phoneNumber).messages,
        )
    }

    fun refreshMessages() {
        val n = _state.value.selectedNumber ?: return
        openMessages(n)
    }

    fun clearSelection() {
        _state.value = _state.value.copy(selectedNumber = null, messages = emptyList())
    }

    fun dismissError() {
        _state.value = _state.value.copy(error = null)
    }
}
