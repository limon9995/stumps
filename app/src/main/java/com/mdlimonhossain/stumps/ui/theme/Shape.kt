package com.mdlimonhossain.stumps.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * How ROUNDED different sized pieces of the UI should be. Material3 groups corner-rounding into
 * five named "buckets" from smallest to largest, and standard components (Card, Button, Chip,
 * dialogs, bottom sheets) automatically pick the right bucket for their own size unless told
 * otherwise. Before this file existed, every screen just wrote its own
 * `RoundedCornerShape(16.dp)` (or 14dp, or 20dp) by hand wherever it needed a rounded corner,
 * which is why corners look very slightly different from screen to screen today. Having ONE
 * shared set of values here is what lets every screen line up exactly as they get updated to
 * use it, one screen group at a time, in later phases.
 */
val StumpsShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp), // tiny things: small chips, badges
    small = RoundedCornerShape(10.dp), // buttons, text fields
    medium = RoundedCornerShape(16.dp), // the "normal" card size — matches what most cards already use today
    large = RoundedCornerShape(20.dp), // bigger feature cards, dialogs
    extraLarge = RoundedCornerShape(28.dp) // full-screen sheets, big hero banners
)
