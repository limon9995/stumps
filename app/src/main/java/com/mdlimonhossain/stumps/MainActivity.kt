package com.mdlimonhossain.stumps

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.Scaffold
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import com.mdlimonhossain.stumps.ui.StumpsApp
import com.mdlimonhossain.stumps.ui.theme.StumpsTheme

/**
 * The ONE and only "Activity" in this whole app — an Activity is Android's term for a single
 * screen/window that the operating system knows how to launch (this is what shows up when you
 * tap the app icon). Because this app is built entirely with Jetpack Compose, we don't need
 * separate Activities for every screen like older Android apps did — instead, everything after
 * this point (login, scoring, tournaments, all of it) is just Compose functions being swapped
 * in and out inside this ONE Activity's window. See StumpsApp.kt for how that switching works.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge() // let the app's content draw behind the phone's status bar/nav bar, for a more modern full-screen look
        setContent {
            // StumpsTheme wraps everything in our app's colours/fonts (see ui/theme/Theme.kt).
            // It defaults to following the phone's own light/dark setting automatically (see
            // Theme.kt's `darkTheme = isSystemInDarkTheme()` default) — no in-app toggle needed.
            StumpsTheme {
                // Scaffold is a standard Compose layout helper that reserves space for things
                // like the status bar automatically — innerPadding tells us how much empty
                // space to leave so our content doesn't get drawn UNDER the status bar.
                Scaffold { innerPadding ->
                    androidx.compose.foundation.layout.Box(modifier = Modifier.padding(innerPadding)) {
                        StumpsApp()
                    }
                }
            }
        }
    }
}
