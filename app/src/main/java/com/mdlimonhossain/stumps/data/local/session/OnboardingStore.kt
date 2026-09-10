package com.mdlimonhossain.stumps.data.local.session

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// Same DataStore idea as UserSessionStore.kt, but its own separate little storage file, just
// for remembering one true/false switch: "has this phone already seen the welcome/onboarding screens?"
private val Context.onboardingDataStore by preferencesDataStore(name = "stumps_onboarding")

/** Remembers whether the welcome + onboarding screens have already been shown, so they only ever appear once. */
class OnboardingStore(private val context: Context) {
    private val hasSeenOnboardingKey = booleanPreferencesKey("has_seen_onboarding")

    // Defaults to false (meaning "show onboarding") if we've never saved anything yet — that's
    // exactly what happens the very first time the app is opened on a phone.
    val hasSeenOnboarding: Flow<Boolean> = context.onboardingDataStore.data.map { it[hasSeenOnboardingKey] ?: false }

    /** Called once the user taps through to the end of onboarding — after this, it won't show again. */
    suspend fun markSeen() {
        context.onboardingDataStore.edit { it[hasSeenOnboardingKey] = true }
    }
}
