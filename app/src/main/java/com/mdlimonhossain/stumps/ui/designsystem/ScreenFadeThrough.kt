package com.mdlimonhossain.stumps.ui.designsystem

import androidx.compose.animation.core.tween
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale

/**
 * A light "fade + grow slightly" entrance for a whole screen's worth of content, all at once —
 * for the (hopefully rare, after this phase) spots where content isn't already covered by one
 * of the NavHost's own screen-to-screen transitions (see ui/navigation/NavAnimations.kt), e.g.
 * content that only appears once data finishes loading from the network/database, well after
 * the screen itself has already finished appearing.
 *
 * Wrap whatever you want to animate in with `ScreenFadeThrough { ...your content... }` and it
 * will smoothly fade + scale in from 92% size up to full size the very first time it appears.
 */
@Composable
fun ScreenFadeThrough(content: @Composable () -> Unit) {
    val alpha = remember { Animatable(0f) }
    val scale = remember { Animatable(0.92f) }
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(260))
    }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, animationSpec = tween(260))
    }
    Box(modifier = Modifier.alpha(alpha.value).scale(scale.value)) {
        content()
    }
}
