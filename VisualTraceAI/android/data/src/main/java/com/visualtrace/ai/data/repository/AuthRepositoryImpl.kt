package com.visualtrace.ai.data.repository

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.userProfileChangeRequest
import com.visualtrace.ai.data.remote.FirebaseAuthHolder
import com.visualtrace.ai.data.remote.TokenProvider
import com.visualtrace.ai.data.remote.VisualTraceApi
import com.visualtrace.ai.data.remote.apiUnit
import com.visualtrace.ai.domain.model.AppError
import com.visualtrace.ai.domain.model.Outcome
import com.visualtrace.ai.domain.model.SessionState
import com.visualtrace.ai.domain.model.UserAccount
import com.visualtrace.ai.domain.repository.AuthRepository
import com.visualtrace.ai.domain.repository.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val holder: FirebaseAuthHolder,
    private val tokens: TokenProvider,
    private val settings: SettingsRepository,
    private val api: VisualTraceApi,
) : AuthRepository {

    private val auth: FirebaseAuth? get() = holder.auth
    override val isConfigured: Boolean get() = auth != null

    private val firebaseUser: Flow<UserAccount?> = auth?.let { fa ->
        callbackFlow {
            val listener = FirebaseAuth.AuthStateListener { a ->
                trySend(a.currentUser?.let { u ->
                    UserAccount(u.uid, u.email, u.displayName, u.photoUrl?.toString(),
                        u.providerData.lastOrNull { it.providerId != "firebase" }?.providerId ?: "password")
                })
            }
            fa.addAuthStateListener(listener)
            awaitClose { fa.removeAuthStateListener(listener) }
        }
    } ?: flowOf(null)

    override val session: Flow<SessionState> =
        combine(firebaseUser, settings.settings.map { it.guestModeChosen }.distinctUntilChanged()) { user, guest ->
            when {
                user != null -> SessionState.SignedIn(user)
                guest -> SessionState.Guest
                else -> SessionState.SignedOut
            }
        }

    private suspend fun run(block: suspend (FirebaseAuth) -> Unit): Outcome<Unit> {
        val fa = auth ?: return Outcome.Failure(AppError.AuthNotConfigured)
        return try {
            block(fa)
            settings.setGuestModeChosen(false)
            Outcome.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Outcome.Failure(mapAuthError(e))
        }
    }

    override suspend fun signInWithEmail(email: String, password: String) =
        run { it.signInWithEmailAndPassword(email.trim(), password).await() }

    override suspend fun register(email: String, password: String, displayName: String?) = run { fa ->
        val result = fa.createUserWithEmailAndPassword(email.trim(), password).await()
        if (!displayName.isNullOrBlank()) {
            result.user?.updateProfile(userProfileChangeRequest { this.displayName = displayName.trim() })?.await()
        }
        result.user?.sendEmailVerification()?.await()
    }

    override suspend fun signInWithGoogleIdToken(idToken: String) =
        run { it.signInWithCredential(GoogleAuthProvider.getCredential(idToken, null)).await() }

    override suspend fun sendPasswordReset(email: String): Outcome<Unit> {
        val fa = auth ?: return Outcome.Failure(AppError.AuthNotConfigured)
        return try {
            fa.sendPasswordResetEmail(email.trim()).await()
            Outcome.Success(Unit)
        } catch (c: CancellationException) {
            throw c
        } catch (e: Exception) {
            Outcome.Failure(mapAuthError(e))
        }
    }

    override suspend fun continueAsGuest() = settings.setGuestModeChosen(true)

    override suspend fun signOut() {
        auth?.signOut()
        settings.setGuestModeChosen(false)
        settings.setCloudSync(false)
    }

    override suspend fun deleteServerData(): Outcome<Unit> = apiUnit { api.deleteMe() }

    override suspend fun idToken(forceRefresh: Boolean): String? = tokens.idToken(forceRefresh)

    private fun mapAuthError(e: Exception): AppError = when (e) {
        is FirebaseNetworkException -> AppError.NoInternet
        is FirebaseTooManyRequestsException -> AppError.RateLimited(null)
        is FirebaseAuthWeakPasswordException -> AppError.Validation("Choose a stronger password (at least 6 characters).")
        is FirebaseAuthUserCollisionException -> AppError.Validation("An account with this email already exists.")
        is FirebaseAuthInvalidUserException -> AppError.Authentication("No account matches this email.")
        is FirebaseAuthInvalidCredentialsException -> AppError.Authentication("The email or password is incorrect.")
        is FirebaseAuthException -> AppError.Authentication(e.message)
        else -> AppError.Unknown(e.message)
    }
}
