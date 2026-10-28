package com.sadique.dailyledger.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sadique.dailyledger.data.ItemArtwork
import com.sadique.dailyledger.ui.screens.LocalIconVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** One icon policy for picker, AI drafts and ledger rows. Explicit user choices win. */
@Composable
fun ItemCategoryIcon(label: String, type: String = "EXPENSE", size: Dp = 30.dp, itemName: String = "") {
    val context = LocalContext.current
    val visual = rememberCategoryVisual(label, type)
    val version = CategoryVisualOverrides.version
    val custom = remember(label, version) { CategoryVisualOverrides.get(context, label) }
    val asset = remember(label, itemName) { ItemArtwork.asset(itemName) ?: ItemArtwork.asset(label) }
    val bundled by produceState<ImageBitmap?>(null, asset) {
        value = withContext(Dispatchers.IO) {
            asset?.let { path -> runCatching {
                context.assets.open(path).use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull() }
        }
    }
    // Known artwork is curated and never replaced by an unrelated web-search photo.
    val downloaded = if (asset == null && custom == null)
        rememberItemIconBitmap(itemName.trim().ifBlank { label }, LocalIconVersion.current) else null
    val bitmap = bundled ?: downloaded
    if (custom != null || bitmap == null) {
        GlowIcon(visual.icon, visual.color, size = size)
    } else {
        val scale = LocalDisplayPreferences.current?.iconMultiplier ?: 1f
        Box(Modifier.size(size * scale).background(visual.color.copy(alpha = .10f), CircleShape), contentAlignment = Alignment.Center) {
            Image(bitmap, contentDescription = null, modifier = Modifier.padding(3.dp).size(size * scale - 6.dp))
        }
    }
}
