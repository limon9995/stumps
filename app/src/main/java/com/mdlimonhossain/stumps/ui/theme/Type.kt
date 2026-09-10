package com.mdlimonhossain.stumps.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Defines the app's text sizes/weights in one place — every "MaterialTheme.typography.XXX"
// used across all the screens refers back to one of these named styles, so changing a font
// size here changes it everywhere at once instead of hunting through every screen.
//
// Material3 actually expects FIFTEEN named text styles (display/headline/title/body/label, each
// in large/medium/small). Only 5 of those 15 used to be filled in here — any screen or standard
// component (like a NavigationBarItem's label, which reads `labelMedium`) that referenced one of
// the other 10 was silently using Compose's own generic default size/weight instead of a size
// that was actually chosen to match this app. All 15 are filled in below now, scaled evenly
// around the 5 that were already there, still using the phone's normal default font (no special
// custom font file is used — just size and boldness are being controlled here).
val Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp), // the biggest text in the app (rarely used, e.g. a big score number)
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 30.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 28.sp), // big page titles
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp), // section headings
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp), // e.g. a card's own title
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 15.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp), // normal readable text
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp), // slightly smaller normal text
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp), // e.g. text inside a Button
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp), // e.g. bottom nav bar tab labels
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp) // tiny labels/captions
)
