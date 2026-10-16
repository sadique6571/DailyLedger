package com.sadique.dailyledger.i18n

import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector

@Composable
fun LocalizedIcon(imageVector: ImageVector, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(imageVector, contentDescription?.let(LocalStrings.current::text), modifier, tint)
}
@Composable
fun LocalizedIcon(painter: Painter, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(painter, contentDescription?.let(LocalStrings.current::text), modifier, tint)
}
@Composable
fun LocalizedIcon(bitmap: ImageBitmap, contentDescription: String?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    androidx.compose.material3.Icon(bitmap, contentDescription?.let(LocalStrings.current::text), modifier, tint)
}
