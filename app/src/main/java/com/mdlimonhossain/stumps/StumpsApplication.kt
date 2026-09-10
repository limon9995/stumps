package com.mdlimonhossain.stumps

import android.app.Application
import com.mdlimonhossain.stumps.data.local.db.AppDatabase
import com.mdlimonhossain.stumps.data.local.session.OnboardingStore
import com.mdlimonhossain.stumps.data.local.session.UserSessionStore
import com.mdlimonhossain.stumps.data.remote.auth.AuthRepository
import com.mdlimonhossain.stumps.data.remote.auth.FirebaseAuthRepository
import com.mdlimonhossain.stumps.data.remote.news.NewsRepository
import com.mdlimonhossain.stumps.data.remote.sync.LiveBroadcastRepository
import com.mdlimonhossain.stumps.domain.repository.ClubRepository
import com.mdlimonhossain.stumps.domain.repository.FollowRepository
import com.mdlimonhossain.stumps.domain.repository.MatchRepository
import com.mdlimonhossain.stumps.domain.repository.StatsRepository
import com.mdlimonhossain.stumps.domain.repository.TeamRepository
import com.mdlimonhossain.stumps.domain.repository.TournamentRepository
import com.mdlimonhossain.stumps.domain.repository.UserRepository

/**
 * This is the very first thing that gets created when the app starts — even before any
 * screen. Its job is simple but important: build ONE shared copy of every repository
 * (database + Firebase wrappers) that the rest of the app can use, so every screen talks
 * to the SAME database connection and the SAME Firebase login state, instead of each screen
 * accidentally creating its own separate copy.
 *
 * This pattern (one shared instance of each important object, created lazily and reused
 * everywhere) is a simple, hand-rolled version of what's called "dependency injection" —
 * bigger apps often use a library (like Hilt) to do this automatically, but for this app
 * a plain class like this is simple enough to understand at a glance.
 *
 * `by lazy { ... }` means "don't actually create this until the first time it's used, then
 * remember it and reuse the same one forever after" — so we're not wasting time building
 * things the app might not even need on a particular screen.
 */
class StumpsApplication : Application() {
    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }
    val sessionStore: UserSessionStore by lazy { UserSessionStore(this) }
    val onboardingStore: OnboardingStore by lazy { OnboardingStore(this) }
    val authRepository: AuthRepository by lazy { FirebaseAuthRepository() }
    val userRepository: UserRepository by lazy { UserRepository(authRepository, database, sessionStore) }
    val matchRepository: MatchRepository by lazy {
        MatchRepository(database.teamDao(), database.playerDao(), database.matchDao(), database.inningsDao(), database.ballDao())
    }
    val teamRepository: TeamRepository by lazy { TeamRepository(database.teamDao(), database.playerDao()) }
    val statsRepository: StatsRepository by lazy { StatsRepository(database.matchDao(), matchRepository) }
    val liveBroadcastRepository: LiveBroadcastRepository by lazy { LiveBroadcastRepository() }
    val tournamentRepository: TournamentRepository by lazy {
        TournamentRepository(
            database.tournamentDao(), database.tournamentTeamDao(), database.tournamentFixtureDao(),
            teamRepository, matchRepository, liveBroadcastRepository
        )
    }
    val clubRepository: ClubRepository by lazy { ClubRepository(database.clubDao()) }
    val followRepository: FollowRepository by lazy { FollowRepository(database.followDao()) }
    val newsRepository: NewsRepository by lazy { NewsRepository() }
}
