package com.sadique.dailyledger.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * True when the device allows continuous ambient animations.
 * The CI emulator disables animations (animator duration scale 0), so ambient loops
 * stay off during UI tests and tests can go idle; on real phones the scale is 1.
 */
@Composable
fun rememberAmbientAnimationsOn(): Boolean {
    val context = LocalContext.current
    return remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}
