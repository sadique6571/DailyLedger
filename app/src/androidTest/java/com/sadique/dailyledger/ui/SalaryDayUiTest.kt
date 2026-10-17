package com.sadique.dailyledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import com.sadique.dailyledger.data.SalaryScheduleStore
import com.sadique.dailyledger.ui.screens.SalaryDayDialog
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.util.UUID

class SalaryDayUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun selectionPersistsOnlyForCurrentOwnerAndCanReturnToAutomatic() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val owner = "salary-test-${UUID.randomUUID()}"
        val store = SalaryScheduleStore(context, owner)
        val other = SalaryScheduleStore(context, "$owner-other")
        compose.setContent { DailyLedgerTheme("DARK") {
            SalaryDayDialog(null, {}) { store.setDay(it) }
        } }
        compose.onNodeWithTag("salary-day-31").performScrollTo().performClick()
        compose.onNodeWithTag("save-salary-day").performClick()
        compose.runOnIdle {
            assertEquals(31, store.day)
            assertEquals(31, SalaryScheduleStore(context, owner).day)
            assertNull(other.day)
        }
        compose.onNodeWithTag("salary-day-auto").performScrollTo().performClick()
        compose.onNodeWithTag("save-salary-day").performClick()
        compose.runOnIdle { assertNull(store.day); assertNull(SalaryScheduleStore(context, owner).day) }
    }
}
