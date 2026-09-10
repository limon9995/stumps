package com.mdlimonhossain.stumps.ui.match.charts

import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp

/** A simple bar-per-over run comparison — how many runs came off each over of the innings. */
@Composable
fun OverRunsChart(runsPerOver: List<Int>, modifier: Modifier = Modifier) {
    if (runsPerOver.isEmpty()) return // nothing to draw yet if no overs have been bowled
    // The tallest bar should reach the top of the chart — everything else is scaled relative
    // to it. coerceAtLeast(1) just avoids a divide-by-zero if every over somehow scored 0.
    val maxRuns = (runsPerOver.maxOrNull() ?: 1).coerceAtLeast(1)

    // Colours are read from the app's THEME here, outside the Canvas below, instead of being
    // hardcoded hex values like this chart used to have — that's what makes the bars correctly
    // switch shade between light and dark mode, and stay on-brand if the theme colours ever
    // change, instead of always drawing the exact same colour no matter what.
    val barColor = MaterialTheme.colorScheme.primary
    val boundaryBarColor = MaterialTheme.colorScheme.error
    val baselineColor = MaterialTheme.colorScheme.outlineVariant

    // A small "grow in from nothing" animation, played once, the very first time this chart
    // appears on screen: `animatedGrowth` climbs from 0 up to 1 over a third of a second or so,
    // and every bar's real height below is multiplied by it — so the bars visibly rise up into
    // place rather than just instantly popping into existence.
    var growthStarted by remember { mutableStateOf(false) }
    val animatedGrowth by animateFloatAsState(
        targetValue = if (growthStarted) 1f else 0f,
        animationSpec = tween(durationMillis = 450, easing = LinearOutSlowInEasing),
        label = "overRunsChartGrowth"
    )
    LaunchedEffect(Unit) { growthStarted = true }

    Canvas(modifier = modifier.fillMaxWidth().height(140.dp)) {
        val barGap = 4f // small gap between neighbouring bars
        // Fit exactly `runsPerOver.size` bars (plus their gaps) into the available width.
        val barWidth = (size.width - barGap * (runsPerOver.size - 1).coerceAtLeast(0)) / runsPerOver.size
        val baselineY = size.height - 16f // where the bottom of every bar sits (the "0 runs" line)

        // Draw a faint horizontal line at the baseline, like the x-axis of a normal chart.
        drawLine(
            color = baselineColor,
            start = Offset(0f, baselineY),
            end = Offset(size.width, baselineY),
            strokeWidth = 2f
        )

        runsPerOver.forEachIndexed { index, runs ->
            // How tall this particular bar should be, as a fraction of the tallest possible bar
            // — multiplied by animatedGrowth so the bars grow upward on first appearance instead
            // of snapping straight to full height.
            val barHeight = (runs.toFloat() / maxRuns) * (baselineY - 12f) * animatedGrowth
            val x = index * (barWidth + barGap) // each bar sits to the right of the previous one
            val isBoundaryOver = runs >= 10 // highlight any especially high-scoring over
            drawRect(
                color = if (isBoundaryOver) boundaryBarColor else barColor,
                topLeft = Offset(x, baselineY - barHeight), // bars grow UPWARDS from the baseline
                size = Size(barWidth, barHeight)
            )
        }
    }
}
