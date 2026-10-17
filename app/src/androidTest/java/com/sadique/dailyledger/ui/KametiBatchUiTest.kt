package com.sadique.dailyledger.ui

import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.OrganizerMemberDialog
import com.sadique.dailyledger.ui.screens.PersonalMemberBatchDialog
import kotlinx.coroutines.CompletableDeferred
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class KametiBatchUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun organizerWaitsForCommitAndKeepsFailedListForRetry() = exercise(true)
    @Test fun personalWaitsForCommitAndKeepsFailedListForRetry() = exercise(false)

    private fun exercise(organizer: Boolean) {
        var open by mutableStateOf(true)
        var calls = 0
        val keys = mutableListOf<String>()
        var gate = CompletableDeferred<Unit>()
        val save: suspend (String, Int) -> Unit = { key, count ->
            assertEquals(1, count)
            calls++; keys.add(key); gate.await()
        }
        compose.setContent { DailyLedgerTheme("LIGHT") {
            if (open) {
                if (organizer) OrganizerMemberDialog(
                    OrganizerCircleEntity("c", "owner", "Friends", 10000, 3, "2026-10", 0),
                    emptyList(), { open = false }) { key, rows -> save(key, rows.size) }
                else PersonalMemberBatchDialog(
                    CommitteeEntity("p", "owner", "Personal", 10000, 3, "2026-10", null, false, true, "", 0),
                    emptyList(), { open = false }) { key, rows -> save(key, rows.size) }
            }
        } }
        compose.onNodeWithText("Member name").performScrollTo().performTextInput("Ali")
        if (!organizer) compose.onNodeWithText("Turn number").performScrollTo().performTextInput("1")
        compose.onNodeWithText(if (organizer) "+ Add another member / turn" else "+ Add another turn / member")
            .performScrollTo().performClick()
        compose.onNodeWithTag("kameti-save-all").performClick()
        compose.waitForIdle()
        compose.onNodeWithTag("kameti-save-all").assertIsNotEnabled()
        compose.onNodeWithText("Cancel").assertIsNotEnabled()
        compose.runOnIdle { assertEquals(1, calls); assertTrue(open); gate.completeExceptionally(IllegalArgumentException("Please retry")) }
        compose.onNodeWithTag("kameti-save-error").assertExists()
        compose.onNodeWithText("Save all (1)").assertIsEnabled()
        compose.runOnIdle { assertTrue(open); gate = CompletableDeferred() }
        compose.onNodeWithTag("kameti-save-all").performClick()
        compose.runOnIdle { assertEquals(2, calls); assertEquals(keys[0], keys[1]); gate.complete(Unit) }
        compose.waitForIdle()
        compose.runOnIdle { assertFalse(open) }
    }
}
