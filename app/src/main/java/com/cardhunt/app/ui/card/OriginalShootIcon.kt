package com.cardhunt.app.ui.card

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color

/** Gold medal with a camera glyph — the "Original Shoot" mark. */
@Composable
fun OriginalShootIcon(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        drawCircle(color = Color(0xFFF5C518))
        drawCircle(color = Color.White,
            style = androidx.compose.ui.graphics.drawscope.Stroke(w * 0.07f),
            radius = size.minDimension / 2 - w * 0.08f)
        // camera top bump
        drawRoundRect(Color.White,
            topLeft = Offset(w * 0.38f, h * 0.26f), size = Size(w * 0.24f, h * 0.14f),
            cornerRadius = CornerRadius(w * 0.04f))
        // camera body
        drawRoundRect(Color.White,
            topLeft = Offset(w * 0.24f, h * 0.36f), size = Size(w * 0.52f, h * 0.38f),
            cornerRadius = CornerRadius(w * 0.07f))
        // lens
        drawCircle(color = Color(0xFF7A5C00), radius = w * 0.12f,
            center = Offset(w * 0.5f, h * 0.55f))
        drawCircle(color = Color.White, radius = w * 0.045f,
            center = Offset(w * 0.5f, h * 0.55f))
    }
}