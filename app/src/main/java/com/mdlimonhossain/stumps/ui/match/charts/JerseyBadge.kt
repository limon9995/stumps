package com.mdlimonhossain.stumps.ui.match.charts

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Draws a simple cricket jersey silhouette — a body with two sleeves and a V-neck — filled with
 * one solid team colour, with a player's initials shown on top. No image file is used for this;
 * the whole shape is just straight lines drawn with Compose's Canvas/Path, the same way
 * WagonWheelChart draws the field circle and shot lines. `initials` is drawn as a normal Text
 * composable layered ON TOP of the Canvas (via a Box), which is simpler than trying to draw text
 * directly onto the canvas itself.
 */
@Composable
fun JerseyBadge(color: Color, initials: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(0.85f), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxWidth().aspectRatio(0.85f)) {
            val w = size.width
            val h = size.height
            // One continuous outline: left sleeve -> left shoulder -> V-neck -> right shoulder ->
            // right sleeve -> down the right side -> across the bottom -> back up the left side.
            val jersey = Path().apply {
                moveTo(0f, h * 0.15f) // left sleeve, outer-top corner
                lineTo(w * 0.25f, 0f) // left shoulder
                lineTo(w * 0.42f, h * 0.10f) // start of the V-neck's left edge
                lineTo(w * 0.5f, h * 0.22f) // bottom point of the V-neck
                lineTo(w * 0.58f, h * 0.10f) // start of the V-neck's right edge
                lineTo(w * 0.75f, 0f) // right shoulder
                lineTo(w, h * 0.15f) // right sleeve, outer-top corner
                lineTo(w * 0.82f, h * 0.38f) // right sleeve, outer-bottom corner (sleeve end)
                lineTo(w * 0.75f, h * 0.30f) // right underarm
                lineTo(w * 0.75f, h) // down the right side to the hem
                lineTo(w * 0.25f, h) // across the bottom hem
                lineTo(w * 0.25f, h * 0.30f) // up the left side to the underarm
                lineTo(w * 0.18f, h * 0.38f) // left sleeve, outer-bottom corner
                close()
            }
            drawPath(path = jersey, color = color)
            // A thin darker outline on top gives the flat fill a bit of definition, so it
            // doesn't look like a plain blob of colour.
            drawPath(path = jersey, color = color.copy(alpha = 1f).darken(), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3f))
        }
        androidx.compose.material3.Text(
            text = initials,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 28.sp
        )
    }
}

/** A quick "make this colour a bit darker" helper, just for the jersey's outline stroke. */
private fun Color.darken(factor: Float = 0.75f): Color =
    Color(red = red * factor, green = green * factor, blue = blue * factor, alpha = alpha)
