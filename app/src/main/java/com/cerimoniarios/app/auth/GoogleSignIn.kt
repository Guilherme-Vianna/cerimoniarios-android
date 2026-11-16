package com.cerimoniarios.app.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.cerimoniarios.app.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException

sealed interface SignInResult {
    data class Success(val idToken: String) : SignInResult

    /** [code] is sent to the web app as `native/<code>` (see web/src/lib/auth/errors.ts). */
    data class Failure(val code: String) : SignInResult
}

/**
 * Google blocks OAuth inside WebViews, so the Google account is picked natively and the
 * resulting ID token is handed to Firebase on the web side (`signInWithCredential`).
 */
object GoogleSignIn {
    suspend fun idToken(activity: Activity): SignInResult {
        val webClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
        if (webClientId.isBlank()) return SignInResult.Failure("not-configured")

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
            .build()

        return try {
            val credential = CredentialManager.create(activity).getCredential(activity, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                SignInResult.Success(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                SignInResult.Failure("failed")
            }
        } catch (_: GetCredentialCancellationException) {
            SignInResult.Failure("cancelled")
        } catch (_: NoCredentialException) {
            SignInResult.Failure("no-account")
        } catch (_: GetCredentialException) {
            SignInResult.Failure("failed")
        } catch (_: GoogleIdTokenParsingException) {
            SignInResult.Failure("failed")
        }
    }

    suspend fun signOut(context: Context) {
        try {
            CredentialManager.create(context).clearCredentialState(ClearCredentialStateRequest())
        } catch (_: Exception) {
            // Nothing to clear is fine: the web session is what matters.
        }
    }
}
