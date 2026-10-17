package com.sadique.dailyledger.sync

import android.accounts.Account
import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

sealed interface DriveAuthOutcome {
    data class Token(val value: String) : DriveAuthOutcome
    data class Resolution(val request: IntentSenderRequest) : DriveAuthOutcome
    data class Error(val message: String) : DriveAuthOutcome
}

object DriveAuth {
    const val SCOPE = "https://www.googleapis.com/auth/drive.appdata"

    private fun request(email: String) = AuthorizationRequest.Builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .setAccount(Account(email, "com.google"))
        .build()

    suspend fun authorize(activity: Activity, email: String): DriveAuthOutcome = withContext(Dispatchers.IO) {
        try {
            val result = Tasks.await(Identity.getAuthorizationClient(activity).authorize(request(email)))
            if (result.hasResolution()) {
                DriveAuthOutcome.Resolution(IntentSenderRequest.Builder(requireNotNull(result.pendingIntent).intentSender).build())
            } else {
                result.accessToken?.let { DriveAuthOutcome.Token(it) } ?: DriveAuthOutcome.Error("No access token")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            DriveAuthOutcome.Error(e.message ?: "Drive authorization failed")
        }
    }

    suspend fun silentToken(context: Context, email: String): String? = withContext(Dispatchers.IO) {
        val result = Tasks.await(Identity.getAuthorizationClient(context).authorize(request(email)), 30, java.util.concurrent.TimeUnit.SECONDS)
        if (result.hasResolution()) null else result.accessToken
            ?: error("Google Drive did not return an access token.")
    }

    fun tokenFromResult(context: Context, intent: Intent?): String? = runCatching {
        intent?.let { Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(it).accessToken }
    }.getOrNull()
}
