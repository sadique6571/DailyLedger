package com.sadique.dailyledger.auth

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.auth.FirebaseAuth
import com.sadique.dailyledger.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit

object FirebaseRuntime {
    private var appCheckInitialized = false

    fun configured(context: Context): Boolean = BuildConfig.FIREBASE_CONFIGURED &&
        (FirebaseApp.getApps(context).isNotEmpty() || FirebaseApp.initializeApp(context) != null)

    @Synchronized fun initAppCheck(context: Context) {
        if (!configured(context) || appCheckInitialized) return
        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(appCheckProvider())
        FirebaseAppCheck.getInstance().setTokenAutoRefreshEnabled(true)
        appCheckInitialized = true
    }

    fun auth(context: Context): FirebaseAuth {
        check(configured(context)) { "Online sign-in setup is pending for this app." }
        return FirebaseAuth.getInstance()
    }

    fun googleClientId(context: Context): String {
        val id = context.resources.getIdentifier("default_web_client_id", "string", context.packageName)
        return if (id != 0) context.getString(id) else BuildConfig.GOOGLE_WEB_CLIENT_ID
    }
}

/** Bounded wait on IO; no extra coroutine adapter dependency is needed. */
suspend fun <T> Task<T>.awaitResult(): T = withContext(Dispatchers.IO) {
    try { Tasks.await(this@awaitResult, 40, TimeUnit.SECONDS) }
    catch (e: ExecutionException) { throw (e.cause ?: e) }
}
