package com.mdlimonhossain.stumps.ui.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A bigger, eye-catching "hero" card with a smooth colour gradient behind it, instead of a flat
 * background colour — used for the ONE or TWO most important things on a screen (e.g. the
 * signed-in user's own quick stats at the top of Home) where we WANT it to visually stand out
 * from all the ordinary [AppCard]s around it.
 *
 * This is pulled directly out of `HomeScreen.kt`'s `ProfileSummaryCard`, which already hand-built
 * exactly this look (a dark-green gradient `Box`) but only for that one place — turning it into
 * a reusable, parameterised composable means later phases can reuse the exact same "gradient
 * hero" look for other screens' headers (a tournament's header, a club's header, a match
 * centre's summary strip) without redoing the gradient math from scratch each time.
 */
@Composable
fun GradientHeroCard(
    modifier: Modifier = Modifier,
    gradientColors: List<Color> = listOf(
        MaterialTheme.colorScheme.primary,
        MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
    ),
    onClick: (() -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large) // the theme's "large" rounded-corner bucket — see ui/theme/Shape.kt
            .background(Brush.horizontalGradient(gradientColors))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(20.dp)
    ) {
        Column(content = { content() })
    }
}
