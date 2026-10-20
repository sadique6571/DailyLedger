package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.ui.LedgerSurface as Surface

import com.sadique.dailyledger.i18n.LocalizedText as Text

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sadique.dailyledger.ui.LandscapeArtwork
import com.sadique.dailyledger.ui.LedgerColors
import com.sadique.dailyledger.ui.LedgerTokens
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Time-aware greeting header with weather pill and cycle label. */
@Composable
internal fun DashboardGreetingSection(
    userName: String,
    month: YearMonth,
    cycleStartLabel: String?,
    weatherEnabled: Boolean,
    weatherCity: String,
    weatherTemperature: String,
    weatherCondition: String,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = LedgerTokens.ShapeCard,
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = LedgerTokens.Elevation1,
        border = androidx.compose.foundation.BorderStroke(1.dp, LedgerColors.Teal.copy(alpha = .22f)),
    ) {
        BoxWithConstraints(
            Modifier.fillMaxWidth().heightIn(min = 112.dp).clip(LedgerTokens.ShapeCard)
                .background(Brush.horizontalGradient(listOf(LedgerColors.Teal.copy(alpha = .14f), LedgerColors.Blue.copy(alpha = .05f))), LedgerTokens.ShapeCard)
                .padding(horizontal = LedgerTokens.Space12)
        ) {
            val compact = maxWidth < 370.dp
            LandscapeArtwork(Modifier.align(Alignment.BottomEnd).width(if (compact) 112.dp else 196.dp).height(112.dp))
            Column(Modifier.padding(top = 6.dp, bottom = 7.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(when (LocalTime.now().hour) { in 5..11 -> "🌅 Good morning,"; in 12..16 -> "☀️ Good afternoon,"; in 17..20 -> "🌆 Good evening,"; else -> "🌙 Good night," }, fontSize = 15.sp)
                Text(translate = false, text = "👋 " + userName.substringBefore(' ').ifBlank { "Friend" }, fontSize = if (compact) 23.sp else 27.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.fillMaxWidth(if (compact) .65f else .85f))
                if (weatherEnabled && weatherCity.isNotBlank()) {
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface.copy(alpha = .88f)) {
                        Row(Modifier.padding(horizontal = 9.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(when { weatherCondition.contains("rain", true) -> "🌧️"; weatherCondition.contains("cloud", true) -> "☁️"; weatherCondition.contains("storm", true) -> "⛈️"; weatherCondition.contains("snow", true) -> "❄️"; LocalTime.now().hour !in 6..18 -> "🌙"; else -> "☀️" }, fontSize = 20.sp)
                            Text(listOf(weatherCity, weatherTemperature, weatherCondition).filter { it.isNotBlank() }.joinToString(" • "),
                                fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 270.dp))
                        }
                    }
                } else Text(cycleStartLabel?.let { "Salary cycle from $it" } ?: month.format(DateTimeFormatter.ofPattern("MMMM yyyy", if (com.sadique.dailyledger.i18n.LocalStrings.current.urdu) Locale("ur", "PK") else Locale.ENGLISH)), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
