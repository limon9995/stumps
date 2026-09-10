package com.mdlimonhossain.stumps.ui.designsystem

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * The app's standard "card" — a small rounded, gently raised box used to group related content
 * (a match's details, a tournament's summary, a settings row, and so on).
 *
 * Right now, roughly 60 places across the app each build their OWN card by hand, calling
 * Compose's raw `Card`/`Surface` directly and picking their own rounding/colour/elevation —
 * which is why cards currently look very slightly different from screen to screen (some use
 * 14dp rounded corners, some 16dp, some 20dp, and so on). `AppCard` is the shared replacement
 * for all of those: it always uses the theme's `medium` corner shape (see ui/theme/Shape.kt), a
 * soft "tonal" elevation (a gentle colour shift rather than a heavy drop-shadow — the "clean
 * modern Material3" look this app is going for), and the `surfaceContainer` colour from the
 * theme, so it reads correctly in both light and dark mode automatically without any extra work
 * from whoever uses it.
 *
 * This card isn't wired into any screen yet in this phase — later phases replace each screen's
 * own hand-rolled card, one screen group at a time, with this shared one.
 */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable () -> Unit
) {
    Card(
        // Only make the whole card tappable if a click handler was actually given — plenty of
        // cards (e.g. a static "need help?" block) aren't meant to be tapped at all.
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(contentPadding)) {
            content()
        }
    }
}
