package com.sadique.dailyledger.ai
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test
class ManualInsightsTest {
    private val tips = listOf(SpendingTip("Review snacks", "Use recorded totals."))
    @Test fun contextAndConsentNeverRequestAndManualRefreshBypassesCache() = runBlocking {
        var calls = 0
        val c = ManualInsights(this, false, { tips }, { _, _ -> })
        c.updateContext("one", true); c.setEnabled(true); c.updateContext("two", true); yield()
        assertEquals(0, calls); assertEquals(tips, c.tips.value)
        c.refresh("two", true) { calls++; tips }; yield()
        c.refresh("two", true) { calls++; tips }; yield()
        assertEquals(2, calls); assertFalse(c.busy.value)
    }
    @Test fun repeatedClicksShareOneInFlightRequest() = runBlocking {
        var calls = 0; var saved = ""
        val response = CompletableDeferred<List<SpendingTip>>()
        val c = ManualInsights(this, true, { null }, { key, _ -> saved = key })
        c.refresh("one", true) { calls++; response.await() }
        c.refresh("one", true) { calls++; tips }; yield()
        assertTrue(c.busy.value); assertEquals(1, calls)
        response.complete(tips); yield()
        assertEquals("one", saved); assertEquals(tips, c.tips.value); assertFalse(c.busy.value)
    }
    @Test fun changedRecordsDiscardLateResultsWithoutAutomaticReplacement() = runBlocking {
        var calls = 0; var saved = false
        val response = CompletableDeferred<List<SpendingTip>>()
        val c = ManualInsights(this, true, { null }, { _, _ -> saved = true })
        c.refresh("old", true) { calls++; withContext(NonCancellable) { response.await() } }; yield()
        c.updateContext("new", true); response.complete(tips); yield()
        assertEquals(1, calls); assertFalse(saved); assertTrue(c.tips.value.isEmpty()); assertFalse(c.busy.value)
    }
    @Test fun disabledConsentAndEmptyRecordsCannotRequestOrAcceptLateResults() = runBlocking {
        var saved = false
        val response = CompletableDeferred<List<SpendingTip>>()
        val c = ManualInsights(this, true, { null }, { _, _ -> saved = true })
        c.refresh("empty", false) { error("Empty request") }
        c.refresh("one", true) { withContext(NonCancellable) { response.await() } }; yield()
        c.setEnabled(false); response.complete(tips); yield()
        assertFalse(saved); assertTrue(c.tips.value.isEmpty())
        c.refresh("one", true) { error("Disabled request") }; assertFalse(c.busy.value)
    }
    @Test fun failedProviderKeepsPreviousTipsAndNeverRetriesAutomatically() = runBlocking {
        var calls = 0
        val c = ManualInsights(this, true, { tips }, { _, _ -> })
        c.refresh("one", true) { calls++; throw AiException("Daily limit reached") }; yield()
        assertEquals(tips, c.tips.value); assertTrue(c.status.value.contains("Daily limit reached")); assertFalse(c.busy.value)
        c.updateContext("one", true); yield(); assertEquals(1, calls)
    }
}
