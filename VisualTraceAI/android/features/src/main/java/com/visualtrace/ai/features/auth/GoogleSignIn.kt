package com.visualtrace.ai.features.auth

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

/** Google Sign-In through Credential Manager. Returns the Google ID token for Firebase. */
object GoogleSignIn {
    sealed interface Result {
        data class Token(val idToken: String) : Result
        data object Cancelled : Result
        data class Error(val message: String?) : Result
    }

    suspend fun request(activityContext: Context, webClientId: String): Result {
        if (webClientId.isBlank()) return Result.Error("Google Sign-In is not configured for this build.")
        val option = GetSignInWithGoogleOption.Builder(webClientId).build()
        val request = GetCredentialRequest.Builder().addCredentialOption(option).build()
        return try {
            val credential = CredentialManager.create(activityContext).getCredential(activityContext, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                Result.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                Result.Error("Unexpected credential type.")
            }
        } catch (e: GetCredentialCancellationException) {
            Result.Cancelled
        } catch (e: GetCredentialException) {
            Result.Error(e.message)
        } catch (e: GoogleIdTokenParsingException) {
            Result.Error(e.message)
        }
    }
}
