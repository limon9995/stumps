package com.mdlimonhossain.stumps.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.mdlimonhossain.stumps.data.local.db.match.BallDao
import com.mdlimonhossain.stumps.data.local.db.match.BallEntity
import com.mdlimonhossain.stumps.data.local.db.match.InningsDao
import com.mdlimonhossain.stumps.data.local.db.match.InningsEntity
import com.mdlimonhossain.stumps.data.local.db.match.MatchDao
import com.mdlimonhossain.stumps.data.local.db.match.MatchEntity
import com.mdlimonhossain.stumps.data.local.db.match.PlayerDao
import com.mdlimonhossain.stumps.data.local.db.match.PlayerEntity
import com.mdlimonhossain.stumps.data.local.db.match.TeamDao
import com.mdlimonhossain.stumps.data.local.db.match.TeamEntity
import com.mdlimonhossain.stumps.data.local.db.club.ClubDao
import com.mdlimonhossain.stumps.data.local.db.club.ClubEntity
import com.mdlimonhossain.stumps.data.local.db.social.FollowDao
import com.mdlimonhossain.stumps.data.local.db.social.FollowEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentDao
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureDao
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentFixtureEntity
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamDao
import com.mdlimonhossain.stumps.data.local.db.tournament.TournamentTeamEntity

/**
 * This is the "front door" to the whole local database. The @Database annotation lists every
 * table (Entity) the database has, and Room reads this list to actually build the real SQLite
 * database file on the phone the very first time the app runs.
 *
 * This class is "abstract" (meaning we don't write its actual code) because Room automatically
 * GENERATES a real working class behind the scenes that implements all these abstract functions
 * — we just declare what Daos we want access to, and Room wires it all up for us.
 */
@Database(
    entities = [
        UserEntity::class,
        TeamEntity::class,
        PlayerEntity::class,
        MatchEntity::class,
        InningsEntity::class,
        BallEntity::class,
        TournamentEntity::class,
        TournamentTeamEntity::class,
        TournamentFixtureEntity::class,
        ClubEntity::class,
        FollowEntity::class
    ],
    version = 7, // bumped from 6 when matches.resultText was added
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun teamDao(): TeamDao
    abstract fun playerDao(): PlayerDao
    abstract fun matchDao(): MatchDao
    abstract fun inningsDao(): InningsDao
    abstract fun ballDao(): BallDao
    abstract fun tournamentDao(): TournamentDao
    abstract fun tournamentTeamDao(): TournamentTeamDao
    abstract fun tournamentFixtureDao(): TournamentFixtureDao
    abstract fun clubDao(): ClubDao
    abstract fun followDao(): FollowDao

    companion object {
        /**
         * A "migration" is a small, hand-written set of instructions for upgrading an EXISTING
         * database from one version to the next, without deleting anything already in it — the
         * opposite of just wiping and recreating the whole database. Each one only needs to
         * describe the DIFFERENCE between the two versions (here: two brand new nullable
         * columns), because SQLite already has everything else from the previous version intact.
         *
         * `ADD COLUMN` is safe to run on a table that already has rows in it — existing rows just
         * get the new column filled in with NULL (which is exactly what our Kotlin `= null`
         * defaults above mean anyway), so nobody's existing matches or balls are touched.
         */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE balls ADD COLUMN fielderId TEXT")
                db.execSQL("ALTER TABLE matches ADD COLUMN format TEXT")
            }
        }

        // Adds teams.location — a saved team's home city/area, shown on its detail page and
        // editable from Team Settings. Same "just ADD COLUMN, existing rows get NULL" idea as
        // MIGRATION_2_3 above.
        private val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE teams ADD COLUMN location TEXT")
            }
        }

        // Adds players.isCaptain/isViceCaptain — the C/VC toggle circles on a team's Players tab.
        // SQLite has no real boolean type, so Room stores a Kotlin Boolean as an INTEGER column
        // that's always 0 or 1 — "NOT NULL DEFAULT 0" means every existing player row instantly
        // becomes "false" for both new columns, matching this entity's own `= false` defaults.
        private val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE players ADD COLUMN isCaptain INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE players ADD COLUMN isViceCaptain INTEGER NOT NULL DEFAULT 0")
            }
        }

        // Adds the Create Tournament form's new fields (club/city/season/dates/ball type) — all
        // nullable, so every tournament created before this pass just gets NULL for each.
        private val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tournaments ADD COLUMN clubName TEXT")
                db.execSQL("ALTER TABLE tournaments ADD COLUMN city TEXT")
                db.execSQL("ALTER TABLE tournaments ADD COLUMN season TEXT")
                db.execSQL("ALTER TABLE tournaments ADD COLUMN startDate INTEGER")
                db.execSQL("ALTER TABLE tournaments ADD COLUMN endDate INTEGER")
                db.execSQL("ALTER TABLE tournaments ADD COLUMN ballType TEXT")
            }
        }

        // Adds matches.resultText — the saved "won by X runs/wickets" line, filled in once a
        // match's second innings finishes (see MatchRepository.finalizeCompletedMatch). Every
        // match scored before this existed just gets NULL, same idea as the migrations above.
        private val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE matches ADD COLUMN resultText TEXT")
            }
        }

        // We only ever want ONE database connection open at a time for the whole app — opening
        // multiple would waste memory and could cause weird bugs. @Volatile + synchronized here
        // is a standard Kotlin/Java pattern called a "singleton": the first time getInstance is
        // called, it creates the database and remembers it; every call after that just reuses
        // the same one instead of creating a new one.
        @Volatile private var instance: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "stumps.db" // the actual filename this gets saved as on the phone's storage
                )
                    .addMigrations(MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6, MIGRATION_6_7)
                    // A real migration now exists for every version bump so far (see above). This
                    // destructive fallback only kicks in for a version jump nobody's written a
                    // migration for yet — every NEW schema change from here on should add its own
                    // real Migration object instead of relying on this wiping people's data.
                    .fallbackToDestructiveMigration()
                    .build().also { instance = it }
            }
    }
}
