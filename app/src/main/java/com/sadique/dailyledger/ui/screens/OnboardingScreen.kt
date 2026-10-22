package com.sadique.dailyledger.ui.screens

import com.sadique.dailyledger.i18n.LocalizedIcon as Icon
import com.sadique.dailyledger.i18n.LocalizedText as Text
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountBalanceWallet
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Groups
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private data class OnboardingSlide(val icon: ImageVector, val title: String, val body: String)

private val OnboardingSlides = listOf(
    OnboardingSlide(
        Icons.Rounded.AccountBalanceWallet,
        "Welcome to Daily Ledger",
        "Roz ka hisaab asan: income, kharcha aur savings — sab ek jagah, PKR mein. Urdu ya English, jaise chaho.",
    ),
    OnboardingSlide(
        Icons.Rounded.Groups,
        "Udhar & Kameti",
        "Kis ko kitna diya, kis se kitna lena hai — due dates aur reminders ke sath, koi hisaab nahi bhoolega.",
    ),
    OnboardingSlide(
        Icons.Rounded.CloudSync,
        "Safe & backed up",
        "Aap ka data phone par encrypted rehta hai, aur Google Drive backup se naye phone par bhi wapas mil jata hai.",
    ),
)

/** One-time three-slide intro shown on first launch, before the main app. */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    var page by remember { mutableStateOf(0) }
    val last = page == OnboardingSlides.lastIndex
    Column(
        Modifier.fillMaxSize().padding(24.dp).testTag("onboarding"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(1f))
        androidx.compose.animation.AnimatedContent(targetState = page, transitionSpec = {
            (androidx.compose.animation.slideInHorizontally(androidx.compose.animation.core.tween(300)) { it / 2 } + androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(300))) togetherWith
                (androidx.compose.animation.slideOutHorizontally(androidx.compose.animation.core.tween(300)) { -it / 2 } + androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(200)))
        }, label = "onboarding-slide") { targetPage ->
            val s = OnboardingSlides[targetPage]
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(s.icon, null, Modifier.size(84.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(24.dp))
                Text(s.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                Spacer(Modifier.height(10.dp))
                Text(s.body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.weight(1f))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            OnboardingSlides.indices.forEach { i ->
                Box(
                    Modifier.size(if (i == page) 10.dp else 8.dp)
                        .background(if (i == page) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (!last) TextButton(onClick = onDone, modifier = Modifier.testTag("onboarding-skip")) { Text("Skip") }
            Spacer(Modifier.weight(1f))
            Button(
                onClick = { if (last) onDone() else page++ },
                modifier = Modifier.testTag(if (last) "onboarding-start" else "onboarding-next"),
            ) { Text(if (last) "Start" else "Next") }
        }
    }
}
