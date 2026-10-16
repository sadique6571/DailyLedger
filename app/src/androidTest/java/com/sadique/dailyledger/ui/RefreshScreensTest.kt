package com.sadique.dailyledger.ui

import android.os.ParcelFileDescriptor
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sadique.dailyledger.data.CreditDraft
import com.sadique.dailyledger.ui.screens.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RefreshScreensTest {
    @get:Rule val compose = createComposeRule()
    @Test fun creditDraftDateCanBeCorrectedBeforeAnySave() {
        var saved = emptyList<CreditDraft>()
        compose.setContent { DailyLedgerTheme("AQUA") { CreditAiDialog({}, { rows, _ -> saved = rows }) } }
        compose.onNodeWithTag("credit-ai-input").performTextInput("1 Oct 2026 rashan 5000 Aslam Store se udhar, due 15 Oct 2026")
        compose.onNodeWithTag("credit-ai-prepare").performScrollTo().performClick()
        assertTrue(saved.isEmpty())
        compose.onNodeWithTag("credit-ai-list").performScrollToKey("credit-0")
        compose.onNodeWithTag("credit-edit-0").performScrollTo().performClick()
        compose.onNodeWithText("Purchase date YYYY-MM-DD").performScrollTo().performTextReplacement("2026-10-02")
        compose.onNodeWithText("Save", useUnmergedTree = true).performClick()
        compose.onNodeWithTag("credit-ai-list").performScrollToKey("credit-0")
        compose.onNodeWithText("Purchase 2026-10-02").assertIsDisplayed()
        shot("udhar-review")
        compose.onNodeWithTag("credit-ai-save").performClick()
        compose.waitForIdle()
        assertEquals("2026-10-02", saved.single().purchaseDate)
        assertEquals("2026-10-15", saved.single().dueDate)
    }
    @Test fun backupCardNeverCallsAnUnverifiedBackupSuccessfulAndActionsWork() {
        var action = ""
        compose.setContent { DailyLedgerTheme("AQUA") { LedgerBackdrop {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LedgerBrandHeader("Settings & backup")
                AccountBackupCard(true, false, "", "", false, {}, { action = "restore" }, { action = "backup" }, { action = "details" })
                PersonalizationCard(true, true, "Okara", {}, {}, { action = "location" }, {})
                SavingsSettingsCard(0) { action = "savings" }
                EncryptionBanner()
            }
        } } }
        compose.onNodeWithText("Awaiting first Drive backup").assertIsDisplayed()
        compose.onNodeWithText("Google Drive backup available").assertDoesNotExist()
        compose.onNodeWithText("Restore data").performClick()
        assertEquals("restore", action)
        compose.onNodeWithText("Back up now").performClick()
        assertEquals("backup", action)
        shot("aqua-settings")
        compose.onNodeWithText("Change").performScrollTo().performClick()
        assertEquals("location", action)
        compose.onNodeWithText("View savings").performScrollTo().performClick()
        assertEquals("savings", action)
    }
    @Test fun moreKeepsOnlyScreensWithoutDedicatedTabs() {
        var action = ""
        compose.setContent { DailyLedgerTheme("AQUA") { MoreScreen({}, { action = "loans" }, {}, {}, {}, {}) } }
        compose.onNodeWithText("Udhar Saman").assertDoesNotExist()
        compose.onNodeWithText("Kameti").assertDoesNotExist()
        compose.onNodeWithText("Savings").assertDoesNotExist()
        compose.onNodeWithText("Loans").performScrollTo().performClick()
        assertEquals("loans", action)
        shot("aqua-more")
    }
    @Test fun settingsChildrenReturnToSettingsBeforeHome() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val owner = "settings-navigation-preview"
        val repo = com.sadique.dailyledger.data.LedgerRepository(com.sadique.dailyledger.data.AppDatabase.get(context), owner)
        kotlinx.coroutines.runBlocking {
            com.sadique.dailyledger.data.AppDatabase.get(context).ledgerDao().upsertTransaction(
                com.sadique.dailyledger.data.TransactionEntity("settings-preview-entry", owner, "EXPENSE", 10000, "Milk", "", java.time.LocalDate.now().toString(), 1, 1))
        }
        try {
            compose.setContent { DailyLedgerTheme("AQUA") {
                DailyLedgerApp(com.sadique.dailyledger.auth.UserProfile(owner, "preview@example.com", "Preview"),
                    com.sadique.dailyledger.data.SettingsStore(context), "AQUA", false, false, 0L, "", false, "", "", "", 0L, false, {})
            } }
            compose.onNodeWithText("More").performClick()
            compose.onNodeWithText("Settings & backup").performClick()
            compose.onNodeWithTag("settings-list").performScrollToKey("settings-savings")
            compose.onNodeWithText("View savings").performScrollTo().performClick()
            compose.onNodeWithTag("return-to-settings").assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("settings-list").assertExists()
            compose.onNodeWithText("View savings").assertIsDisplayed()
            compose.onNodeWithTag("settings-list").performScrollToKey("settings-header")
            compose.onNodeWithContentDescription("AI Auto Fill").performClick()
            compose.onNodeWithTag("autofill-list").assertExists()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("settings-list").assertExists()
            compose.onNodeWithTag("settings-list").performScrollToKey("settings-personalization")
            compose.onNodeWithText("Change").performScrollTo().performClick()
            compose.onNodeWithText("Weather location").performScrollTo().assertIsDisplayed()
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithTag("settings-list").assertExists()
            compose.onNodeWithText("Weather location").assertDoesNotExist()
            compose.onNodeWithTag("settings-list").performScrollToKey("settings-export")
            compose.onNodeWithTag("export-save").performScrollTo().assertIsEnabled(); shot("settings-export")
            androidx.test.espresso.Espresso.pressBack()
            compose.onNodeWithText("Your money, organized").assertIsDisplayed()
            compose.onNodeWithText("Home").performClick()
            compose.onNodeWithTag("dashboard-list").performScrollToKey("balance")
            compose.onNodeWithText("Remaining balance").assertIsDisplayed()
        } finally { kotlinx.coroutines.runBlocking { repo.clearAllForOwner() } }
    }
    private fun shot(name: String) {
        compose.waitForIdle()
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("screencap -p /data/local/tmp/dailyledger-$name.png")
        ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
    }
}
