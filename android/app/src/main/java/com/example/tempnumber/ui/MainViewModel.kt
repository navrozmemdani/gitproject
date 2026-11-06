package com.example.tempnumber.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.tempnumber.data.ApiClient
import com.example.tempnumber.data.ApiService
import com.example.tempnumber.data.AvailableNumber
import com.example.tempnumber.data.LoginRequest
import com.example.tempnumber.data.RegisterRequest
import com.example.tempnumber.data.RentRequest
import com.example.tempnumber.data.RentedNumber
import com.example.tempnumber.data.Session
import com.example.tempnumber.data.SettingsStore
import com.example.tempnumber.data.SmsMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class UiState(
    val session: Session = Session(),
    val loggedIn: Boolean = false,
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
            val s = store.session.first()
            _state.value = _state.value.copy(session = s)
            if (s.baseUrl.isNotBlank() && s.token.isNotBlank()) {
                api = ApiClient.create(s.baseUrl, s.token)
                _state.value = _state.value.copy(loggedIn = true)
                refreshNumbers()
            }
        }
    }

    // ---------- auth ----------
    fun login(baseUrl: String, email: String, password: String) =
        authenticate(baseUrl, email) { auth -> auth.login(LoginRequest(email.trim(), password)) }

    fun register(baseUrl: String, email: String, password: String, signupKey: String) =
        authenticate(baseUrl, email) { auth ->
            auth.register(signupKey.trim(), RegisterRequest(email.trim(), password))
        }

    private inline fun authenticate(
        baseUrl: String,
        email: String,
        crossinline call: suspend (ApiService) -> com.example.tempnumber.data.AuthResponse,
    ) {
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                val auth = ApiClient.createAuth(baseUrl.trim())
                val res = call(auth)
                store.saveSession(baseUrl, res.token, res.user.email)
                api = ApiClient.create(baseUrl.trim(), res.token)
                _state.value = _state.value.copy(
                    session = Session(baseUrl.trim(), res.token, res.user.email),
                    loggedIn = true,
                )
                refreshNumbers()
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = humanize(e))
            } finally {
                _state.value = _state.value.copy(loading = false)
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            try {
                api?.logout()
            } catch (_: Exception) {
                // best effort; clear locally regardless
            }
            store.clearToken()
            api = null
            _state.value = UiState(
                session = _state.value.session.copy(token = ""),
                loggedIn = false,
            )
        }
    }

    // ---------- numbers & messages ----------
    private inline fun run(crossinline block: suspend (ApiService) -> Unit) {
        val svc = api ?: run {
            _state.value = _state.value.copy(error = "Please sign in first.")
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(loading = true, error = null)
            try {
                block(svc)
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = humanize(e))
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
        _state.value = _state.value.copy(available = emptyList(), numbers = svc.numbers().numbers)
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

    private fun humanize(e: Exception): String {
        val msg = e.message ?: "Request failed"
        return when {
            msg.contains("401") -> "Session expired — please sign in again."
            msg.contains("403") -> "Not allowed (check your signup key)."
            else -> msg
        }
    }
}
