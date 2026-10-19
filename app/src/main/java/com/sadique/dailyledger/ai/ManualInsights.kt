package com.sadique.dailyledger.ai

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Record and consent changes are local. Only refresh() can start a network request. */
class ManualInsights(
    private val scope: CoroutineScope,
    initiallyEnabled: Boolean,
    private val cached: (String) -> List<SpendingTip>?,
    private val save: (String, List<SpendingTip>) -> Unit,
) {
    private val _enabled = MutableStateFlow(initiallyEnabled)
    private val _tips = MutableStateFlow<List<SpendingTip>>(emptyList())
    private val _status = MutableStateFlow("")
    private val _busy = MutableStateFlow(false)
    val enabled = _enabled.asStateFlow()
    val tips = _tips.asStateFlow()
    val status = _status.asStateFlow()
    val busy = _busy.asStateFlow()
    private var contextKey = ""
    private var hasRecords = false
    private var generation = 0
    private var job: Job? = null

    fun setEnabled(value: Boolean) {
        cancelRequest()
        _enabled.value = value
        _tips.value = emptyList()
        showCachedOrPrompt()
    }
    fun updateContext(key: String, recordsAvailable: Boolean) {
        if (key == contextKey && recordsAvailable == hasRecords) return
        cancelRequest()
        if(key!=contextKey)_tips.value=emptyList()
        contextKey = key
        hasRecords = recordsAvailable
        showCachedOrPrompt()
    }
    private fun showCachedOrPrompt() {
        if (!_enabled.value || !hasRecords) {
            _tips.value = emptyList()
            _status.value = if (_enabled.value) "Add an income or expense entry before refreshing." else ""
            return
        }
        val saved = cached(contextKey)
        if (saved != null) {
            _tips.value = saved
            _status.value = "Saved online suggestions for these records. Tap Refresh to update."
        } else {
            _status.value = if (_tips.value.isEmpty()) "Tap Refresh to get online suggestions."
                else "Records changed. Suggestions below are from earlier records. Tap Refresh to update."
        }
    }
    fun refresh(key: String, recordsAvailable: Boolean, generate: suspend () -> List<SpendingTip>) {
        updateContext(key, recordsAvailable)
        if (!_enabled.value || !hasRecords || _busy.value) return
        val request = ++generation
        _busy.value = true
        _status.value = "Preparing online suggestions…"
        job = scope.launch {
            try {
                val result = generate()
                if (request == generation && _enabled.value && contextKey == key) {
                    save(key, result)
                    _tips.value = result
                    _status.value = "Online suggestions updated. Refresh again when you need new advice."
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                if (request == generation) {
                    val error = (e as? AiException)?.message ?: "Online suggestions are unavailable. Try Refresh again later."
                    _status.value = error + if (_tips.value.isNotEmpty()) " Previous suggestions are still shown below." else ""
                }
            } finally { if (request == generation) _busy.value = false }
        }
    }
    private fun cancelRequest() {
        generation++
        job?.cancel()
        job = null
        _busy.value = false
    }
}
