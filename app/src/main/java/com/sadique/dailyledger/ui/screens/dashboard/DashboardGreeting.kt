package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.ui.LedgerSurface as Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.animation.animateContentSize
import com.sadique.dailyledger.ui.ledgerCardBrush
import com.sadique.dailyledger.ui.LedgerColors
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.sp
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

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
        Text(userName.substringBefore(' ').ifBlank { "Friend" } + " 👋", translate = false,
            fontSize = if (compact) 19.sp else 25.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("greeting-name"))
        }
        if (weatherEnabled && weatherCity.isNotBlank() && !compact) {
            Surface(modifier = Modifier.widthIn(max = 130.dp), shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeCard, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.background(ledgerCardBrush(LedgerColors.Green)).padding(12.dp).testTag("weather-chip"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    val weatherAnim = androidx.compose.runtime.remember { androidx.compose.animation.core.Animatable(-2.5f) }
                    if (!androidx.compose.ui.platform.LocalInspectionMode.current && !android.app.ActivityManager.isRunningInTestHarness()) androidx.compose.runtime.LaunchedEffect(Unit) {
                        while (true) {
                            weatherAnim.animateTo(2.5f, androidx.compose.animation.core.tween(1700))
                            weatherAnim.animateTo(-2.5f, androidx.compose.animation.core.tween(1700))
                        }
                    }
                    Text(when {
                        weatherCondition.contains("storm", true) -> "⛈️"
                        weatherCondition.contains("rain", true) -> "🌧️"
                        weatherCondition.contains("cloud", true) -> "☁️"
                        weatherCondition.contains("snow", true) -> "❄️"
                        hour !in 6..18 -> "🌙"; else -> "☀️"
                    }, fontSize = 15.sp, modifier = Modifier.offset { androidx.compose.ui.unit.IntOffset(0, weatherAnim.value.roundToInt()) })
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
