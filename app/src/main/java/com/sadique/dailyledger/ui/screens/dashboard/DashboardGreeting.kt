package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.YearMonth

/** Single-line greeting row; the weather card was removed (user order). */
@Composable
internal fun DashboardGreetingSection(
    userName: String, month: YearMonth, cycleStartLabel: String?,
    compact: Boolean = false,
) {
    val hour = LocalTime.now().hour
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp).animateContentSize()
        .testTag("compact-greeting"), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (!compact) Text(when (hour) {
            in 5..11 -> "🌅 Good morning,"; in 12..16 -> "☀️ Good afternoon,"
            in 17..20 -> "🌆 Good evening,"; else -> "🌙 Good night,"
        }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        // The hand waves every few seconds so it is actually seen (W).
        val handWave = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
        val waveCtx = androidx.compose.ui.platform.LocalContext.current
        val waveAmbientOn = androidx.compose.runtime.remember { android.provider.Settings.Global.getFloat(waveCtx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
        if (waveAmbientOn) androidx.compose.runtime.LaunchedEffect(Unit) {
            while (true) {
                handWave.animateTo(0f, androidx.compose.animation.core.keyframes {
                    durationMillis = 750
                    0f at 0; 16f at 120; -10f at 280; 12f at 430; -5f at 580; 0f at 750
                })
                kotlinx.coroutines.delay(3400)
            }
        }
        Text(userName.substringBefore(' ').ifBlank { "Friend" }, translate = false,
            fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).testTag("greeting-name"))
        Text("👋", translate = false, fontSize = if (compact) 17.sp else 22.sp,
            modifier = Modifier.graphicsLayer { rotationZ = handWave.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.85f) })
    }
}
