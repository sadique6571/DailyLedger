package com.sadique.dailyledger.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import android.provider.Settings
import androidx.compose.ui.unit.dp

/** One entrance per page/item, independent of amounts and normal recomposition.
 * rememberSaveable also prevents a LazyColumn item replaying as it scrolls back in.
 */
@Composable
fun CardEntrance(index: Int = 0, modifier: Modifier = Modifier, entranceKey: Int = 0, content: @Composable () -> Unit) {
    var entered by rememberSaveable(entranceKey) { mutableStateOf(false) }
    val progress = remember(entranceKey) { Animatable(if (entered) 1f else 0f) }
    val distance = with(LocalDensity.current) { 48.dp.toPx() }
    val context = LocalContext.current
    val reduceMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    LaunchedEffect(entranceKey) {
        if (!entered) {
            entered = true
            if (reduceMotion) progress.snapTo(1f)
            else progress.animateTo(1f, tween(220, delayMillis = (index.coerceIn(0, 4) * 40), easing = FastOutSlowInEasing))
        }
    }
    Column(modifier.graphicsLayer {
        translationX = -distance * (1f - progress.value)
        alpha = progress.value
    }) { content() }
}

fun LazyListScope.enteringItem(key: Any? = null, index: Int = 0, entranceKey: Int = 0, content: @Composable LazyItemScope.() -> Unit) {
    item(key = key) {
        val scope = this
        CardEntrance(index, entranceKey = entranceKey) { scope.content() }
    }
}

fun <T> LazyListScope.enteringItems(items: List<T>, key: (T) -> Any, content: @Composable LazyItemScope.(T) -> Unit) {
    itemsIndexed(items, key = { _, value -> key(value) }) { index, value ->
        val scope = this
        CardEntrance(index) { scope.content(value) }
    }
}
