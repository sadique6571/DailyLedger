package com.sadique.dailyledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.sadique.dailyledger.data.SalaryScheduleStore
import com.sadique.dailyledger.ui.screens.SalarySettingsCard
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID
import java.time.LocalDate

class SalaryDayUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun calendarPersistsOnlyForCurrentOwnerAndCanReturnToAutomatic() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val owner = "salary-test-${UUID.randomUUID()}"
        val store = SalaryScheduleStore(context, owner)
        val other = SalaryScheduleStore(context, "$owner-other")
        store.selectReference(LocalDate.parse("2026-01-31"), null)
        assertEquals(LocalDate.parse("2026-02-28"), store.calendar.expected)
        store.selectExpected(LocalDate.parse("2026-03-12"), null)
        assertEquals(store.calendar, SalaryScheduleStore(context, owner).calendar)
        assertNull(other.calendar.expected)
        compose.setContent { DailyLedgerTheme("DARK") { SalarySettingsCard(store, emptyList()) } }
        compose.onNodeWithTag("salary-reference-date").assertHasClickAction()
        compose.onNodeWithText("Salary date").assertExists()
        compose.onNodeWithText("Next expected salary").assertExists()
        screenshot("salary-fields-dark")
        compose.onNodeWithTag("salary-expected-date").performClick()
        compose.onNodeWithTag("salary-calendar").assertExists()
        compose.onNodeWithText("Select next expected salary date").assertExists()
        screenshot("salary-picker-dark")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithTag("salary-calendar-auto").performClick()
        compose.waitUntil(5_000) { store.calendar.expected == null }
        compose.runOnIdle {
            assertNull(store.calendar.expected)
            assertNull(SalaryScheduleStore(context, owner).calendar.expected)
        }
    }
    private fun screenshot(name: String) {
        compose.waitForIdle()
        val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
