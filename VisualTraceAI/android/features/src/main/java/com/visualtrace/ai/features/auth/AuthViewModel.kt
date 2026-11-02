package com.visualtrace.ai.features.auth

import android.util.Patterns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthMode { SIGN_IN, REGISTER, FORGOT }

data class AuthUiState(
    val mode: AuthMode = AuthMode.SIGN_IN,
    val email: String = "",
    val password: String = "",
    val name: String = "",
    val busy: Boolean = false,
    val error: AppError? = null,
    val info: AuthInfo? = null,
    val emailError: Boolean = false,
    val passwordError: Boolean = false,
)

enum class AuthInfo { RESET_SENT, VERIFICATION_SENT }

@HiltViewModel
class AuthViewModel @Inject constructor(private val auth: AuthRepository) : ViewModel() {
    private val _state = MutableStateFlow(AuthUiState())
    val state: StateFlow<AuthUiState> = _state.asStateFlow()
    val isConfigured: Boolean get() = auth.isConfigured

    fun setMode(mode: AuthMode) = _state.update { it.copy(mode = mode, error = null, info = null) }
    fun onEmail(v: String) = _state.update { it.copy(email = v.take(320), emailError = false) }
    fun onPassword(v: String) = _state.update { it.copy(password = v.take(128), passwordError = false) }
    fun onName(v: String) = _state.update { it.copy(name = v.take(100)) }
    fun clearMessages() = _state.update { it.copy(error = null, info = null) }

    companion object {
        fun isValidEmail(email: String) = email.isNotBlank() && Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()
        fun isValidPassword(pw: String) = pw.length >= 8
    }

    fun submit() {
        val st = _state.value
        val emailOk = isValidEmail(st.email)
        val pwOk = st.mode == AuthMode.FORGOT || (if (st.mode == AuthMode.REGISTER) isValidPassword(st.password) else st.password.isNotEmpty())
        if (!emailOk || !pwOk) {
            _state.update { it.copy(emailError = !emailOk, passwordError = !pwOk) }
            return
        }
        _state.update { it.copy(busy = true, error = null, info = null) }
        viewModelScope.launch {
            val r = when (st.mode) {
                AuthMode.SIGN_IN -> auth.signInWithEmail(st.email, st.password)
                AuthMode.REGISTER -> auth.register(st.email, st.password, st.name.ifBlank { null })
                AuthMode.FORGOT -> auth.sendPasswordReset(st.email)
            }
            _state.update {
                when (r) {
                    is Outcome.Success -> it.copy(busy = false, info = when (st.mode) {
                        AuthMode.FORGOT -> AuthInfo.RESET_SENT
                        AuthMode.REGISTER -> AuthInfo.VERIFICATION_SENT
                        else -> null
                    })
                    is Outcome.Failure -> it.copy(busy = false, error = r.error)
                }
            }
        }
    }

    fun onGoogleIdToken(token: String) {
        _state.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            val r = auth.signInWithGoogleIdToken(token)
            _state.update { it.copy(busy = false, error = (r as? Outcome.Failure)?.error) }
        }
    }

    fun onGoogleError(message: String?) = _state.update { it.copy(busy = false, error = AppError.Authentication(message)) }
    fun continueAsGuest() = viewModelScope.launch { auth.continueAsGuest() }
}
