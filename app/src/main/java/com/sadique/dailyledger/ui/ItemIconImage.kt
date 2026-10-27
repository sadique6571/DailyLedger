package com.sadique.dailyledger.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import com.sadique.dailyledger.ai.ItemIconService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The downloaded original icon for an item label, or null when none is
 * stored yet (then the caller shows the default category visual). The file
 * is tiny (at most 64 KB, at most 128x128) and decoded off the main thread.
 */
@Composable
fun rememberItemIconBitmap(label: String): ImageBitmap? {
    val context = LocalContext.current
    val file = remember(label) { ItemIconService.iconFile(context, label) }
    val bitmap by produceState<ImageBitmap?>(initialValue = null, file) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                if (!file.exists() || file.length() == 0L) null
                else BitmapFactory.decodeFile(file.absolutePath)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}
