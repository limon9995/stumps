package com.mdlimonhossain.stumps.ui.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * A compact, single-row card for the many "list of things" screens across the app — match
 * history rows, a team's player roster, a club's member list, the people you're following,
 * search results, and so on. Each row is: an optional leading icon/avatar slot on the left, a
 * title + optional subtitle in the middle, and an optional bit of trailing content (a badge, an
 * arrow, a button) on the right.
 *
 * Today, each of those list screens builds its own row layout by hand with slightly different
 * padding/spacing/text styling — this is the shared version later phases switch those screens
 * over to, one at a time, so every list in the app ends up lining up exactly the same way.
 */
@Composable
fun ListItemCard(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    // Unspecified means "use whatever colour normal text already uses" — only overridden for
    // rows that need to stand out semantically, like a destructive "Logout" row shown in red.
    titleColor: Color = Color.Unspecified,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null
) {
    AppCard(modifier = modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(12.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, color = titleColor)
                if (subtitle != null) {
                    Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}
