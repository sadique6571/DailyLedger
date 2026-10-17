package com.sadique.dailyledger.data

import org.junit.Assert.*
import org.junit.Test

class OrganizerRosterImportTest {
    @Test fun spreadsheetTabsAndMultipleTurns() {
        val rows = parseOrganizerRosterText("name\tphone\twhatsapp\tshare\tturn\nAli\t03001234567\t\t1\t3\nAli\t03001234567\t\t0.5\t8")
        assertEquals(2, rows.size)
        assertEquals(3, rows[0].turn)
        assertEquals(1, rows[1].units)
        checkOrganizerImportCapacity(20, emptyList(), rows)
    }
    @Test fun quotedCsvAndWindowsNewlines() {
        val rows = parseOrganizerRosterText("Name,Phone,WhatsApp,Share,Turn\r\n\"Ahmed, Khan\",0300,,full,2\r\n")
        assertEquals("Ahmed, Khan", rows.single().name)
        assertEquals(2, rows.single().turn)
    }
    @Test(expected = IllegalArgumentException::class)
    fun conflictingFullTurnRejected() {
        checkOrganizerImportCapacity(2, emptyList(), parseOrganizerRosterText("Ali,,,1,1\nBilal,,,1,1"))
    }
    @Test(expected = IllegalArgumentException::class)
    fun malformedQuotedRecordRejected() {
        parseOrganizerRosterText("\"Unclosed,record")
    }
    @Test(expected = IllegalArgumentException::class)
    fun overCapacityRejected() {
        checkOrganizerImportCapacity(2, emptyList(), parseOrganizerRosterText("A,,,1,1\nB,,,1,2\nC,,,0.5,0"))
    }
}
