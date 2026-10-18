package com.sadique.dailyledger.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sadique.dailyledger.data.*
import com.sadique.dailyledger.ui.screens.OrganizerKametiScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OrganizerKametiUiTest {
    @get:Rule val compose = createComposeRule()

    @Test fun organizerShowsPooledProgressPairedHalvesAndContactPicker() {
        val c = OrganizerCircleEntity("c","owner","Al Madina",500000,20,"2026-10",1)
        val alice = OrganizerMemberEntity("a","owner","c","Ali","03001111111","",1,6,1)
        val ahmed = OrganizerMemberEntity("b","owner","c","Ahmed","03002222222","",1,6,2)
        val unassigned = OrganizerMemberEntity("u","owner","c","Usman","03003333333","",2,0,3)
        val payments = listOf(
            OrganizerCollectionEntity("p1","owner","c","a","2026-10",250000,"2026-10-15","Cash",1),
            OrganizerCollectionEntity("p2","owner","c","b","2026-10",250000,"2026-10-15","Cash",2),
        )
        compose.setContent { DailyLedgerTheme("LIGHT") {
            OrganizerKametiScreen(listOf(c), listOf(alice,ahmed,unassigned), payments, emptyList(),
                onBack = {}, onAddCircle = { _,_,_,_ -> }, onAddMembers = { _,_,_ -> },
                onAssignTurn = { _,_,_ -> }, onUpdateContact = { _,_,_,_ -> }, onCollect = { _,_,_,_,_ -> },
                onPayout = { _,_,_,_ -> }, onDeleteMember = {},
                onDeleteCircle = {})
        } }
        compose.onNodeWithTag("organizer-collection-progress", useUnmergedTree = true).assertExists()
        compose.onNodeWithText("Al Madina").assertExists()
        compose.onNodeWithText("Paired: Ahmed", substring = true).assertExists()
        compose.onNodeWithText("+ Add member / 0.5 or full share").performScrollTo().performClick()
        compose.onNodeWithTag("pick-phone-contact").assertExists()
        compose.onNodeWithText("0.5 share").assertExists()
        compose.onNodeWithText("1 full share").assertExists()
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithText("Pick number").performScrollTo().assertIsDisplayed().performClick()
        try {
            compose.waitUntil(5_000) { compose.onAllNodesWithText("Random pick").fetchSemanticsNodes().isNotEmpty() }
        } finally {
            val fd = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /data/local/tmp/dailyledger-organizer-draw.png")
            android.os.ParcelFileDescriptor.AutoCloseInputStream(fd).use { it.readBytes() }
        }
        compose.onNodeWithText("Random pick").assertExists()
        compose.onNodeWithText("Or manually choose:").assertExists()
    }
}
