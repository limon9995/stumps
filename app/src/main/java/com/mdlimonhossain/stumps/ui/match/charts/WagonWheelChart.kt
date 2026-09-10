package com.mdlimonhossain.stumps.ui.match.charts

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.mdlimonhossain.stumps.domain.scoring.ShotEvent
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Which of the two ways the wagon wheel can be drawn — one line per shot (RUNS), or one percentage label per direction "zone" (PERCENTAGE). */
enum class WagonWheelViewMode { RUNS, PERCENTAGE }

/**
 * Draws the wagon wheel two different ways depending on `viewMode`:
 *  - RUNS: every recorded shot as a line from the centre out to where it was hit — this is the
 *    "reading back" side of the maths in ShotDirectionDialog.kt: there we turned a tap position
 *    INTO an angle, here we turn an angle BACK INTO a position to draw the line.
 *  - PERCENTAGE: the field split into 8 equal 45°-wide directional zones, each labelled with
 *    what percentage of ALL recorded shots landed in that zone — matches the reference app's
 *    alternate wagon wheel view.
 */
@Composable
fun WagonWheelChart(shots: List<ShotEvent>, modifier: Modifier = Modifier, viewMode: WagonWheelViewMode = WagonWheelViewMode.RUNS) {
    val textMeasurer = rememberTextMeasurer()

    // Colours used to come from hardcoded hex values, which had a real bug hiding in them: the
    // percentage labels were drawn in plain black text with no colour set at all, which is
    // invisible against this app's near-black dark mode background. Reading everything from the
    // theme here fixes that, and also means the wheel switches shade correctly in dark mode
    // like every other themed part of the app.
    val fieldColor = MaterialTheme.colorScheme.primary
    val sixColor = MaterialTheme.colorScheme.error
    val fourColor = MaterialTheme.colorScheme.primary
    val otherShotColor = MaterialTheme.colorScheme.tertiary
    val centreDotColor = MaterialTheme.colorScheme.error
    val labelColor = MaterialTheme.colorScheme.onSurface

    // Same "grow in" idea as OverRunsChart: the field outline appears immediately (so the pitch
    // shape is visible right away), but the shot lines/percentage labels drawn OUT from the
    // centre grow outward from 0 to their real length over a third of a second, the first time
    // this chart appears — instead of every line just instantly snapping into place.
    var growthStarted by remember { mutableStateOf(false) }
    val animatedGrowth by animateFloatAsState(
        targetValue = if (growthStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
        label = "wagonWheelGrowth"
    )
    LaunchedEffect(Unit) { growthStarted = true }

    Canvas(modifier = modifier.fillMaxWidth().aspectRatio(1f)) {
        val radius = min(size.width, size.height) / 2f * 0.92f // slightly smaller than the full canvas, to leave a little edge margin
        val center = Offset(size.width / 2f, size.height / 2f)

        // Draw the field outline and a faint fill, same idea as ShotDirectionDialog's picker circle.
        drawCircle(color = fieldColor.copy(alpha = 0.5f), radius = radius, center = center, style = Stroke(width = 2f))
        drawCircle(color = fieldColor.copy(alpha = 0.06f), radius = radius, center = center)

        when (viewMode) {
            WagonWheelViewMode.RUNS -> shots.forEach { shot ->
                // How far out to draw the line — sixes go all the way to the boundary, fours go
                // most of the way, smaller shots are drawn shorter, roughly matching how far the
                // ball would realistically have travelled.
                val fraction = when {
                    shot.runs >= 6 -> 1f
                    shot.runs == 4 -> 0.8f
                    shot.runs > 0 -> 0.45f + shot.runs * 0.05f
                    else -> 0.2f
                } * animatedGrowth
                val angleRad = Math.toRadians(shot.angleDegrees - 90.0) // shift so 0deg (up) starts at top
                // cos gives the horizontal (x) component of the angle, sin gives the vertical (y)
                // component — together they turn "angle + distance" into an actual point to draw to.
                val end = Offset(
                    x = center.x + (radius * fraction * cos(angleRad)).toFloat(),
                    y = center.y + (radius * fraction * sin(angleRad)).toFloat()
                )
                // Colour-code the shot: red for a six, dark green for a four, a third colour for anything else.
                val color = if (shot.isSix) sixColor else if (shot.runs == 4) fourColor else otherShotColor
                drawLine(color = color, start = center, end = end, strokeWidth = 3f)
            }

            WagonWheelViewMode.PERCENTAGE -> {
                // Bucket every shot into one of 8 equal 45°-wide zones (0-7), based on its angle.
                val zoneCounts = IntArray(8)
                shots.forEach { shot ->
                    // The double-modulo handles a stray negative angle safely, even though
                    // angles are documented as always being 0-359.
                    val normalized = ((shot.angleDegrees % 360) + 360) % 360
                    zoneCounts[normalized / 45]++
                }
                val total = shots.size

                // Draw 8 faint spokes marking the zone boundaries, so the percentage labels
                // below have a visual "pie slice" to sit inside.
                for (zone in 0 until 8) {
                    val angleRad = Math.toRadians(zone * 45.0 - 90.0)
                    val end = Offset(
                        x = center.x + (radius * cos(angleRad)).toFloat(),
                        y = center.y + (radius * sin(angleRad)).toFloat()
                    )
                    drawLine(color = fieldColor.copy(alpha = 0.3f), start = center, end = end, strokeWidth = 1f)
                }

                // One percentage label per zone, positioned at that zone's midpoint direction.
                for (zone in 0 until 8) {
                    val percent = if (total == 0) 0 else (zoneCounts[zone] * 100.0 / total).roundToInt()
                    val midAngleDeg = zone * 45.0 + 22.5 - 90.0 // the CENTRE of this zone, shifted so 0deg is straight up
                    val angleRad = Math.toRadians(midAngleDeg)
                    val labelCenter = Offset(
                        x = center.x + (radius * 0.72f * animatedGrowth * cos(angleRad)).toFloat(),
                        y = center.y + (radius * 0.72f * animatedGrowth * sin(angleRad)).toFloat()
                    )
                    val text = textMeasurer.measure(AnnotatedString("$percent%"), style = TextStyle(color = labelColor))
                    // drawText positions from the TOP-LEFT corner of the text, so we shift by
                    // half its own width/height to actually center it on labelCenter.
                    drawText(
                        textLayoutResult = text,
                        topLeft = Offset(labelCenter.x - text.size.width / 2f, labelCenter.y - text.size.height / 2f)
                    )
                }
            }
        }

        // Draw the centre dot LAST, so it sits on top of all the shot lines instead of underneath them.
        drawCircle(color = centreDotColor, radius = 8f, center = center)
    }
}
