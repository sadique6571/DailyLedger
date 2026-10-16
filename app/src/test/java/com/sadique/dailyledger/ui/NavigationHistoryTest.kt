package com.sadique.dailyledger.ui

import org.junit.Assert.*
import org.junit.Test

class NavigationHistoryTest {
    @Test fun nestedSettingsSearchAndAiReturnThroughEveryPreviousScreen() {
        var nav = NavigationHistory().selectTab(6).open(8).open(4).open(14).open(1).open(9)
        for (expected in listOf(1, 14, 4, 8, 6, 0)) { nav = nav.back(); assertEquals(expected, nav.current) }
        assertFalse(nav.canGoBack)
        assertEquals(nav, nav.back())
    }
    @Test fun bottomTabStartsANewPathAndDuplicateOpenDoesNotTrapBack() {
        val nav = NavigationHistory().selectTab(6).open(8).open(15).selectTab(3).open(3)
        assertEquals(listOf(0, 3), nav.entries)
        assertEquals(0, nav.back().current)
        assertEquals(listOf(0), nav.selectTab(0).entries)
    }
    @Test fun restoredPathRejectsInvalidRoutes() {
        assertTrue(runCatching { NavigationHistory(emptyList()) }.isFailure)
        assertTrue(runCatching { NavigationHistory(listOf(8, 3)) }.isFailure)
        assertTrue(runCatching { NavigationHistory(listOf(0, 100)) }.isFailure)
        assertEquals(listOf(0, 6, 8, 15), NavigationHistory(listOf(0, 6, 8, 15)).entries)
    }
}
