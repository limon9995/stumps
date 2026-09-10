package com.mdlimonhossain.stumps.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * A small rounded, pill-shaped tab/chip used all over this app (Profile's Overview/Statistics
 * switch, Match Centre's tab row and innings switcher, Tournament's tab row, Search's category
 * chips, and more) — pulled out into ONE shared composable so every one of those switches looks
 * and animates exactly the same way, instead of four or five near-identical copies each doing
 * their own slightly-different thing.
 *
 * The background and text colour both use `animateColorAsState`, which smoothly fades between
 * the "selected" and "unselected" colours over a fifth of a second, instead of the color just
 * instantly snapping — that's what makes tapping between tabs feel smooth rather than jumpy.
 */
@Composable
fun AnimatedTabChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val backgroundColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        animationSpec = tween(durationMillis = 220),
        label = "tabChipBackground"
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 220),
        label = "tabChipContent"
    )

    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = backgroundColor
    ) {
        Text(
            text = label,
            color = contentColor,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 13.sp,
            maxLines = 1,
            textAlign = TextAlign.Center,
            // fillMaxWidth here means: if the chip is stretched wide (e.g. via a `weight(1f)`
            // modifier from the caller), the label centres itself across that FULL width,
            // rather than just hugging the left edge — matches how all four original
            // hand-written versions of this chip behaved.
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)
        )
    }
}
