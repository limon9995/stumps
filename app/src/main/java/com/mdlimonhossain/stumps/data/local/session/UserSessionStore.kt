package com.mdlimonhossain.stumps.data.local.session

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// "DataStore" is a simple key-value storage system, good for small bits of settings-like data
// (unlike Room, which is for proper structured tables). Think of it like a tiny text file where
// we can save "current_uid = abc123" and read it back later.
private val Context.dataStore by preferencesDataStore(name = "stumps_session")

/**
 * Persists which user is currently signed in, so offline-first data (Room) can be
 * attributed to the right uid even before Firestore sync has run.
 *
 * In plain words: we need to remember "who is using the app right now" even between app
 * launches, and even before we've had a chance to talk to Firebase over the internet — this
 * small file on the phone is where that one piece of info lives.
 */
class UserSessionStore(private val context: Context) {
    private val currentUidKey = stringPreferencesKey("current_uid")

    // A live stream — automatically updates if the saved uid ever changes (e.g. on logout).
    val currentUid: Flow<String?> = context.dataStore.data.map { it[currentUidKey] }

    suspend fun setCurrentUid(uid: String?) {
        context.dataStore.edit { prefs ->
            // Passing null means "log out" — we remove the key entirely rather than saving "null" as text.
            if (uid == null) prefs.remove(currentUidKey) else prefs[currentUidKey] = uid
        }
    }
}
