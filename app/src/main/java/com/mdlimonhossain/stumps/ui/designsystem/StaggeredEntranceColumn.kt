package com.mdlimonhossain.stumps.ui.designsystem

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * A little reusable animation for lists: instead of every row/card in a list just instantly
 * appearing all at once, each item fades in and slides up slightly, with each one starting its
 * own animation a little LATER than the one before it — like dominoes falling, or a small wave
 * moving across the list. This is often called a "staggered" entrance animation.
 *
 * Before this file existed, the whole app had exactly ONE shared animation anywhere at all
 * (`AnimatedTabChip`'s colour fade) — this is a second reusable one, meant first for the
 * horizontally-scrolling match/tournament rows on the Home screen, and for any other list
 * screens later phases apply it to.
 *
 * How to use it: inside a `LazyRow`/`LazyColumn`'s `items(...)` block, add
 * `Modifier.staggeredEntrance(index)` to each item, passing that item's own position in the
 * list — item 0 starts animating immediately, item 1 starts a tiny bit after that, item 2 a
 * tiny bit after THAT, and so on.
 */
fun Modifier.staggeredEntrance(index: Int, itemDelayMillis: Int = 40): Modifier = composed {
    // `remember(index)` (rather than a plain `remember { }`) makes sure that if this same
    // Composable slot ever gets reused for a DIFFERENT list index (which LazyRow/LazyColumn can
    // do for performance), the animation correctly restarts from scratch for the new item
    // instead of reusing stale progress from whatever used to be there.
    val alpha = remember(index) { Animatable(0f) }
    val offsetY = remember(index) { Animatable(16f) }

    LaunchedEffect(index) {
        // Cap how far the stagger delay can stretch out — past roughly the 8th item, every
        // remaining item animates in together instead of the wait growing longer and longer
        // for items further down a long list.
        val cappedIndex = index.coerceAtMost(8)
        delay((cappedIndex * itemDelayMillis).toLong())
        launch { alpha.animateTo(1f, animationSpec = tween(280)) }
        launch { offsetY.animateTo(0f, animationSpec = tween(280)) }
    }

    this.alpha(alpha.value).offset(y = offsetY.value.dp)
}
