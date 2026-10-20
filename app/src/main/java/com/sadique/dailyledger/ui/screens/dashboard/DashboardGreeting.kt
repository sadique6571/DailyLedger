package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedText as Text
import com.sadique.dailyledger.ui.LedgerSurface as Surface
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
) {
    val hour = LocalTime.now().hour
    Row(Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 4.dp)
        .testTag("compact-greeting"), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(when (hour) {
            in 5..11 -> "🌅 Good morning,"; in 12..16 -> "☀️ Good afternoon,"
            in 17..20 -> "🌆 Good evening,"; else -> "🌙 Good night,"
        }, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(userName.substringBefore(' ').ifBlank { "Friend" } + " 👋", translate = false,
            fontSize = 25.sp, fontWeight = FontWeight.Bold, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("greeting-name"))
        }
        if (weatherEnabled && weatherCity.isNotBlank()) {
            Surface(modifier = Modifier.widthIn(max = 130.dp), shape = com.sadique.dailyledger.ui.LedgerTokens.ShapeCard, color = MaterialTheme.colorScheme.surface.copy(alpha = .86f)) {
                Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp).testTag("weather-chip"),
                    verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(when {
                        weatherCondition.contains("storm", true) -> "⛈️"
                        weatherCondition.contains("rain", true) -> "🌧️"
                        weatherCondition.contains("cloud", true) -> "☁️"
                        weatherCondition.contains("snow", true) -> "❄️"
                        hour !in 6..18 -> "🌙"; else -> "☀️"
                    }, fontSize = 15.sp)
                    Text(listOf(weatherCity, weatherTemperature, weatherCondition).filter { it.isNotBlank() }.joinToString(" • "),
                        fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier)
                }
            }
        }
    }
}
