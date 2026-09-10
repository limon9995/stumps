package com.mdlimonhossain.stumps.ui.match

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.atan2
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * A tap-to-pick field diagram for recording where a boundary shot went, for the wagon wheel.
 * 0 degrees points straight down the ground (away from the batsman, at the top of the circle);
 * angle increases clockwise, matching how a real wagon wheel is drawn.
 *
 * The tricky bit of maths here is `atan2` — it's a function that answers "given a point's x and
 * y offset from the centre, what ANGLE is that point at?". We feed it how far right (dx) and
 * how far down (dy) the tap was from the middle of the circle, and it hands back the angle in
 * radians (a different, more "mathematical" way of measuring angles than degrees). We then
 * convert that to degrees and shift it so 0° means "straight up" instead of maths' usual
 * "straight right", to match how a wagon wheel is normally drawn.
 */
@Composable
fun ShotDirectionDialog(runs: Int, onPicked: (Int?) -> Unit) {
    AlertDialog(
        onDismissRequest = { onPicked(null) },
        title = { Text("$runs রান — বল কোথায় গেলো?") },
        text = {
            Column {
                Text("মাঠের যেদিকে শট গেছে সেখানে ট্যাপ করো", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                // Canvas is Compose's "blank drawing surface" — instead of describing UI with
                // widgets like Button/Text, inside a Canvas we draw raw shapes (circles, lines)
                // ourselves at exact pixel positions, like drawing on paper.
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f) // keep it perfectly square, so the circle isn't squashed
                        .padding(8.dp)
                        // pointerInput lets us listen for raw touch gestures on this Canvas —
                        // detectTapGestures specifically watches for simple taps.
                        .pointerInput(Unit) {
                            detectTapGestures { offset ->
                                val center = Offset(size.width / 2f, size.height / 2f)
                                // How far right (dx) and down (dy) the tap was from the centre point.
                                val dx = offset.x - center.x
                                val dy = offset.y - center.y
                                // atan2(dx, -dy): 0 deg = straight up (down the ground), clockwise positive
                                val angle = (Math.toDegrees(atan2(dx.toDouble(), -dy.toDouble())) + 360.0) % 360.0
                                onPicked(angle.roundToInt())
                            }
                        }
                ) {
                    // Draw the field: an outlined circle (the boundary rope), a very faint fill
                    // inside it (so it reads as "grass"), and a small red dot marking the centre
                    // (where the batsman stands).
                    val radius = min(size.width, size.height) / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)
                    drawCircle(color = Color(0xFF1B6E43), radius = radius, center = center, style = Stroke(width = 3f))
                    drawCircle(color = Color(0xFF1B6E43).copy(alpha = 0.08f), radius = radius, center = center)
                    drawCircle(color = Color(0xFFB3122B), radius = 10f, center = center)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = { onPicked(null) }) { Text("স্কিপ করো") } }
    )
}
