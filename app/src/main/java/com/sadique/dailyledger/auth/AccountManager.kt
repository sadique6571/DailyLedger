package com.sadique.dailyledger.auth

import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.sadique.dailyledger.data.AppDatabase
import com.sadique.dailyledger.data.LedgerRepository
import com.sadique.dailyledger.data.SettingsStore
import com.sadique.dailyledger.sync.BackupLock
import com.sadique.dailyledger.sync.SyncScheduler
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.first
import kotlin.coroutines.cancellation.CancellationException

class AccountManager(private val context: Context) {
    val configured get() = FirebaseRuntime.configured(context)
    val googleConfigured get() = configured && FirebaseRuntime.googleClientId(context).isNotBlank()

    suspend fun restoreSession() {
        val settings = SettingsStore(context)
        settings.migrateLegacyDriveSettings()
        val user = if (configured) FirebaseRuntime.auth(context).currentUser else null
        if (user == null) settings.clearUser() else finishSignIn(user)
    }
    suspend fun signIn(email: String, password: String) {
        val result = FirebaseRuntime.auth(context).signInWithEmailAndPassword(email.trim(), password).awaitResult()
        finishSignIn(requireNotNull(result.user))
    }
    suspend fun signUp(name: String, email: String, password: String) {
        require(name.isNotBlank()) { "Enter your name." }
        require(email.isNotBlank()) { "Enter your email address." }
        require(password.length >= 8) { "Use at least 8 characters for your password." }
        val user = requireNotNull(FirebaseRuntime.auth(context)
            .createUserWithEmailAndPassword(email.trim(), password).awaitResult().user)
        finishSignIn(user, name.trim())
        try {
            user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).awaitResult()
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { android.util.Log.w("DailyLedgerAuth", "Account was created, but the optional profile-name update failed.") }
    }
    suspend fun google() {
        val token = GoogleAuthManager(context).idToken(FirebaseRuntime.googleClientId(context))
        val result = FirebaseRuntime.auth(context)
            .signInWithCredential(GoogleAuthProvider.getCredential(token, null)).awaitResult()
        finishSignIn(requireNotNull(result.user))
    }
    suspend fun resetPassword(email: String) {
        FirebaseRuntime.auth(context).sendPasswordResetEmail(email.trim()).awaitResult()
    }
    suspend fun signOut() {
        SyncScheduler.cancelImmediate(context)
        try { GoogleAuthManager(context).clear() }
        catch (e: CancellationException) { throw e }
        catch (_: Exception) { }
        BackupLock.mutex.withLock {
            if (configured) FirebaseRuntime.auth(context).signOut()
            SettingsStore(context).clearUser()
        }
    }
    private suspend fun finishSignIn(user: FirebaseUser, fallbackName: String? = null) {
        BackupLock.mutex.withLock {
            val google = user.providerData.firstOrNull { it.providerId == GoogleAuthProvider.PROVIDER_ID }
            // A verified Google identity can claim its own records from the older app.
            google?.uid?.takeIf { it != user.uid }?.let { oldOwner ->
                LedgerRepository(AppDatabase.get(context), user.uid).migrateOwner(oldOwner)
                SettingsStore(context).migrateDriveAccount(oldOwner, user.uid)
            }
            val settings = SettingsStore(context)
            val storedName = settings.all.first().user?.takeIf { it.id == user.uid }?.name
            settings.saveUser(UserProfile(
                user.uid, user.email.orEmpty(),
                user.displayName?.takeIf { it.isNotBlank() } ?: fallbackName?.takeIf { it.isNotBlank() }
                    ?: storedName?.takeIf { it.isNotBlank() } ?: user.email.orEmpty(),
                user.photoUrl?.toString(), google?.email,
            ))
        }
        SyncScheduler.syncNow(context)
    }
}

fun friendlyAuthError(error: Throwable): String = when (error) {
    is GetCredentialCancellationException -> "Google sign-in was cancelled."
    is FirebaseNetworkException -> "No internet connection. Please try again when you are online."
    is FirebaseTooManyRequestsException -> "Too many attempts. Please wait and try again."
    is FirebaseAuthException -> when (error.errorCode) {
        "ERROR_EMAIL_ALREADY_IN_USE", "ERROR_ACCOUNT_EXISTS_WITH_DIFFERENT_CREDENTIAL" ->
            "This email already has an account. Sign in with the method you used before."
        "ERROR_WEAK_PASSWORD" -> "Password does not meet Firebase's password policy. Use at least 8 characters and try again."
        "ERROR_INVALID_EMAIL" -> "Please enter a valid email address."
        "ERROR_USER_DISABLED" -> "This account is disabled. Contact the app owner."
        "ERROR_OPERATION_NOT_ALLOWED" ->
            "Email/password sign-up is disabled in Firebase Authentication. Enable Email/Password under Firebase Console → Authentication → Sign-in method."
        "ERROR_INVALID_API_KEY", "ERROR_APP_NOT_AUTHORIZED" ->
            "Firebase sign-in is not configured for this Android app. Check the Firebase project and google-services.json."
        "ERROR_USER_NOT_FOUND", "ERROR_WRONG_PASSWORD", "ERROR_INVALID_CREDENTIAL", "ERROR_INVALID_LOGIN_CREDENTIALS" ->
            "Email or password is incorrect. Try again or use Forgot password."
        else -> "Sign-in could not be completed. Please try again."
    }
    is java.util.concurrent.TimeoutException -> "The connection timed out. Please try again."
    else -> error.message ?: "Sign-in could not be completed. Please try again."
}
