package com.sadique.dailyledger.ui

import android.app.Application
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.data.AppDatabase
import com.sadique.dailyledger.data.LedgerRepository
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class MainViewModelStartupTest {
    @Test fun coldAndWarmStartsLoadCategoryStateWithoutAnInitializationError() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val owner = "startup-test-${UUID.randomUUID()}"
        val repo = LedgerRepository(AppDatabase.get(app), owner)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        try {
            repeat(3) {
                val store = ViewModelStore()
                lateinit var model: MainViewModel
                instrumentation.runOnMainSync {
                    model = MainViewModel(app, owner)
                    store.put("startup", model)
                }
                try {
                    val state = withTimeout(15_000) {
                        combine(model.learnedOverlayVersion, model.error) { version, error -> version to error }
                            .first { (version, error) -> version > 0 || error != null }
                    }
                    assertNull("Startup must not expose a constructor race", state.second)
                    assertTrue("Learned category initialization must complete", state.first > 0)
                } finally {
                    instrumentation.runOnMainSync { store.clear() }
                }
            }
        } finally {
            repo.clearAllForOwner()
        }
    }
}
