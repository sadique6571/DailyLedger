package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi

/** Exact easeOutCubic from the user-approved demo: 1 - (1 - f)^3. */
private val EaseOutCubic = Easing { f -> val inv = 1f - f; 1f - inv * inv * inv }

/** Card slide-in matching the user-approved demo video: each card slides in
 * from fully off-screen left over 550ms (easeOutCubic) with a 110ms stagger,
 * and REPLAYS whenever the card scrolls back into view — plain remember means
 * a LazyColumn item that leaves composition resets and animates again on
 * return. Reduced-motion (animator scale 0) still snaps straight to visible.
 */
@Composable
fun CardEntrance(index: Int = 0, modifier: Modifier = Modifier, entranceKey: Int = 0, content: @Composable () -> Unit) {
    var entered by remember(entranceKey) { mutableStateOf(false) }
    val progress = remember(entranceKey) { Animatable(if (entered) 1f else 0f) }
    // Full screen width: the card starts completely off-screen, as in the demo.
    val distance = with(LocalDensity.current) { LocalConfiguration.current.screenWidthDp.dp.toPx() }
    val context = LocalContext.current
    val reduceMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    LaunchedEffect(entranceKey) {
        if (!entered) {
            entered = true
            if (reduceMotion) progress.snapTo(1f)
            else progress.animateTo(1f, tween(LedgerTokens.EntranceDurationMs,
                delayMillis = (index.coerceIn(0, 4) * LedgerTokens.EntranceStaggerMs), easing = EaseOutCubic))
        }
    }
    Column(modifier.graphicsLayer {
        translationX = -distance * (1f - progress.value)
        alpha = progress.value
    }) { content() }
}

@OptIn(ExperimentalFoundationApi::class)
fun LazyListScope.enteringItem(key: Any? = null, index: Int = 0, entranceKey: Int = 0, content: @Composable LazyItemScope.() -> Unit) {
    item(key = key) {
        val scope = this
        CardEntrance(index, entranceKey = entranceKey) { scope.content() }
    }
}

@OptIn(ExperimentalFoundationApi::class)
fun <T> LazyListScope.enteringItems(items: List<T>, key: (T) -> Any, content: @Composable LazyItemScope.(T) -> Unit) {
    itemsIndexed(items, key = { _, value -> key(value) }) { index, value ->
        val scope = this
        CardEntrance(index) { scope.content(value) }
    }
}
