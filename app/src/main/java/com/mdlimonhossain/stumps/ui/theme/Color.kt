package com.mdlimonhossain.stumps.ui.theme

import androidx.compose.ui.graphics.Color

// The app's whole colour palette lives in one place, as named constants, so every screen uses
// the exact same greens/reds instead of each screen picking its own slightly-different shade.
// The 0xFF at the start of each is the "fully opaque" marker, followed by the RGB hex code.

val PitchGreen = Color(0xFF1B6E43) // the main brand green, used for buttons etc. in light mode
val PitchGreenDark = Color(0xFF54CC8F) // a brighter green used instead, in dark mode
val BallRed = Color(0xFFB3122B) // cricket-ball red, used sparingly for emphasis (sixes, live badges)
val Cream = Color(0xFFF6F8F4) // near-white background for light mode
val InkGreen = Color(0xFF16211A) // near-black text colour for light mode
val NightBg = Color(0xFF0D1712) // near-black background for dark mode
val NightSurface = Color(0xFF142019) // slightly lighter than NightBg, for cards/surfaces in dark mode

// ---------------------------------------------------------------------------------------------
// Everything below this line fills in the REST of Material3's colour "roles" that the app didn't
// have names for before — Material3 expects roughly 25 different named roles (secondary colour,
// tertiary colour, several "container" shades of each, outlines, etc.), but this file used to
// only define the handful needed for the 7 roles Theme.kt was actually setting. Screens/components
// that referenced any of the missing roles were silently falling back to Compose's own generic
// purple defaults instead of an on-brand colour. Grouped by role family, light then dark, below.
// ---------------------------------------------------------------------------------------------

// "Container" shades of the main brand green — a soft, low-emphasis background (e.g. behind a
// selected primary chip) paired with a colour that's readable written on TOP of that background.
val PrimaryContainerLight = Color(0xFFBFE9D2)
val OnPrimaryContainerLight = Color(0xFF00210F)
val PrimaryContainerDark = Color(0xFF0B5132)
val OnPrimaryContainerDark = Color(0xFFBFE9D2)

// Secondary = a quieter, muted sage-green used for less prominent buttons/chips, so not
// everything on screen has to shout using the same bright PitchGreen as primary.
val SecondaryGreen = Color(0xFF4F6358)
val OnSecondaryLight = Cream
val SecondaryContainerLight = Color(0xFFD2E8DA)
val OnSecondaryContainerLight = Color(0xFF0C1F15)
val SecondaryGreenDark = Color(0xFFB6CCBE)
val OnSecondaryDark = Color(0xFF213529)
val SecondaryContainerDark = Color(0xFF384B40)
val OnSecondaryContainerDark = Color(0xFFD2E8DA)

// Tertiary = a THIRD brand colour, separate from the two greens, used sparingly for
// achievement/trophy-flavoured moments (tournament winners, standings highlights) — a warm gold
// felt like the obvious cricket-trophy association rather than reusing green for everything.
val TrophyGold = Color(0xFF8C5F00)
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFFFDDAE)
val OnTertiaryContainerLight = Color(0xFF2B1700)
val TrophyGoldDark = Color(0xFFF0BE6E)
val OnTertiaryDark = Color(0xFF462C00)
val TertiaryContainerDark = Color(0xFF654200)
val OnTertiaryContainerDark = Color(0xFFFFDDAE)

// A "container" shade for errors, to match — used behind things like a form's error banner.
val ErrorContainerLight = Color(0xFFFFDAD9)
val OnErrorContainerLight = Color(0xFF410007)
val ErrorContainerDark = Color(0xFF93000B)
val OnErrorContainerDark = Color(0xFFFFDAD9)

// A ladder of FIVE background shades, each very slightly darker/lighter than the last (lowest
// = closest to the very back of the screen, highest = closest to the front/most "raised" thing
// on screen). Cards, the bottom nav bar, and the drawer all pick one of these five instead of
// each screen inventing its own semi-transparent overlay colour by hand like before.
val SurfaceContainerLowestLight = Color(0xFFFFFFFF)
val SurfaceContainerLowLight = Color(0xFFF0F3EB)
val SurfaceContainerLight = Color(0xFFEAEDE4)
val SurfaceContainerHighLight = Color(0xFFE4E8DE)
val SurfaceContainerHighestLight = Color(0xFFDEE2D8)
val SurfaceContainerLowestDark = Color(0xFF080D0A)
val SurfaceContainerLowDark = Color(0xFF141F19)
val SurfaceContainerDark = Color(0xFF18231C)
val SurfaceContainerHighDark = Color(0xFF222D26)
val SurfaceContainerHighestDark = Color(0xFF2D3830)

// Outlines = subtle border/divider colours (a divider line, an unfilled button's border) that
// are much lower-contrast than normal text, so they read as a hint rather than shouting.
val OutlineGreen = Color(0xFF74796F)
val OutlineVariantGreen = Color(0xFFC4C9BC)
val OutlineGreenDark = Color(0xFF8E9389)
val OutlineVariantGreenDark = Color(0xFF44483F)

// "Inverse" colours = the OPPOSITE of the current theme, used for things like a Snackbar that
// deliberately stands out against whatever mode (light/dark) the rest of the app is in.
val InverseSurfaceLight = Color(0xFF2B3230)
val InverseOnSurfaceLight = Color(0xFFECF2E9)
val InverseSurfaceDark = Color(0xFFE2E8DE)
val InverseOnSurfaceDark = Color(0xFF1B211D)
