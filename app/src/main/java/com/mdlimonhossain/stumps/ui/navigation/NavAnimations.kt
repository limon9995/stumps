package com.mdlimonhossain.stumps.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry

/**
 * ONE shared "recipe" for how screens animate in and out, used by every destination in our
 * NavHost — so the whole app's screen-to-screen motion feels like one consistent system,
 * instead of every screen either inventing its own transition or (like before this file
 * existed) having NO transition at all and just hard-cutting instantly.
 *
 * This is a simplified take on what Material Design calls a "shared axis" transition: the new
 * screen slides gently in from the right while fading in, and the screen being left slides
 * slightly to the left while fading out — like two pages sliding past each other along one
 * shared horizontal line. Going "back" (a "pop") plays the same motion in reverse, so it always
 * feels like you're moving forward/backward along that same line rather than two unrelated
 * animations bolted together.
 */
private const val NavAnimationDurationMillis = 300

// How far the sliding starts/ends from, as a FRACTION of the screen's own width (dividing the
// full width by this number). A bigger divisor means a SMALLER slide distance — we want a
// small, subtle nudge that reads as "one smooth motion", not a big dramatic swipe across the
// whole screen, so this is a fairly large divisor.
private const val SlideDivisor = 6

/** Plays when navigating FORWARD to a new screen: the new screen slides in from the right. */
val sharedAxisEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        animationSpec = tween(NavAnimationDurationMillis),
        initialOffsetX = { fullWidth -> fullWidth / SlideDivisor }
    ) + fadeIn(animationSpec = tween(NavAnimationDurationMillis))
}

/** Plays when navigating FORWARD to a new screen: the screen being left slides slightly left. */
val sharedAxisExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(
        animationSpec = tween(NavAnimationDurationMillis),
        targetOffsetX = { fullWidth -> -fullWidth / SlideDivisor }
    ) + fadeOut(animationSpec = tween(NavAnimationDurationMillis))
}

/** Plays when going BACK: the previous screen slides back in from the left. */
val sharedAxisPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideInHorizontally(
        animationSpec = tween(NavAnimationDurationMillis),
        initialOffsetX = { fullWidth -> -fullWidth / SlideDivisor }
    ) + fadeIn(animationSpec = tween(NavAnimationDurationMillis))
}

/** Plays when going BACK: the screen being left behind slides out slightly to the right. */
val sharedAxisPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutHorizontally(
        animationSpec = tween(NavAnimationDurationMillis),
        targetOffsetX = { fullWidth -> fullWidth / SlideDivisor }
    ) + fadeOut(animationSpec = tween(NavAnimationDurationMillis))
}
