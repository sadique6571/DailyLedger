package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.ui.LedgerSurface as Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import com.sadique.dailyledger.ui.ledgerCardBrush
import com.sadique.dailyledger.ui.LedgerColors
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

/** Compact greeting and live weather; no decorative card competes with the balance. */
@Composable
internal fun DashboardGreetingSection(
    userName: String, month: YearMonth, cycleStartLabel: String?,
    weatherEnabled: Boolean, weatherCity: String, weatherTemperature: String, weatherCondition: String,
    compact: Boolean = false,
) {
    val hour = LocalTime.now().hour
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp).animateContentSize()
        .testTag("compact-greeting"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        if (!compact) Text(when (hour) {
            in 5..11 -> "🌅 Good morning,"; in 12..16 -> "☀️ Good afternoon,"
            in 17..20 -> "🌆 Good evening,"; else -> "🌙 Good night,"
        }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // The hand actually waves once when the dashboard appears (W).
            val handWave = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(0f) }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                handWave.animateTo(0f, androidx.compose.animation.core.keyframes {
                    durationMillis = 750
                    0f at 0; 16f at 120; -10f at 280; 12f at 430; -5f at 580; 0f at 750
                })
            }
            Text(userName.substringBefore(' ').ifBlank { "Friend" }, translate = false,
                fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.Bold, maxLines = 1,
                overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false).testTag("greeting-name"))
            Text("👋", translate = false, fontSize = if (compact) 17.sp else 22.sp,
                modifier = Modifier.graphicsLayer { rotationZ = handWave.value; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.85f, 0.85f) })
        }
        }
        if (weatherEnabled && weatherCity.isNotBlank() && !compact) {
            Surface(modifier = Modifier.widthIn(max = 168.dp), shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeCard, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.background(ledgerCardBrush(LedgerColors.Green)).padding(10.dp).testTag("weather-chip"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    // Animated sky: the scene matches the live weather condition.
                    WeatherScene(weatherCondition, isDay = hour in 6..18,
                        modifier = Modifier.fillMaxWidth().height(56.dp))
                    Spacer(Modifier.height(4.dp))
                    Text(weatherTemperature.ifBlank { weatherCity }, style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface)
                    if (weatherTemperature.isNotBlank()) Text(weatherCity, style = MaterialTheme.typography.labelMedium,
                        maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (weatherCondition.isNotBlank()) Text(weatherCondition, style = MaterialTheme.typography.labelSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
