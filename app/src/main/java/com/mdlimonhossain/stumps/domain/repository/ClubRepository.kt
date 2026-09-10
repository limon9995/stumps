package com.mdlimonhossain.stumps.domain.repository

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.mdlimonhossain.stumps.data.local.db.club.ClubDao
import com.mdlimonhossain.stumps.data.local.db.club.ClubEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.tasks.await
import java.util.UUID

/**
 * The "middleman" (repository) between the Club screens and the club data. Two data sources
 * live here, same "local truth + cloud mirror" split as LiveBroadcastRepository:
 *  - `clubDao` (Room): this device's OWN clubs — the source of truth for "my clubs", works
 *    fully offline.
 *  - `firestore`'s "clubs" collection: a SHARED, public mirror every user's device pushes its
 *    own clubs into, so Search can find clubs OTHER people registered, not just your own. A
 *    club still works perfectly locally even if this cloud push never succeeds (e.g. no
 *    internet at registration time) — it just won't show up in anyone else's search until it does.
 */
class ClubRepository(
    private val clubDao: ClubDao,
    firestoreOverride: FirebaseFirestore? = null
) {
    // See the matching comment in TournamentRepository for why this is lazy rather than a plain
    // constructor default — it stops Robolectric unit tests (which build this repository with no
    // real Firebase app running) from crashing just by constructing a ClubRepository.
    private val firestore: FirebaseFirestore by lazy { firestoreOverride ?: FirebaseFirestore.getInstance() }
    private fun cloudClubsRef() = firestore.collection("clubs")

    fun observeClubsForUser(uid: String): Flow<List<ClubEntity>> = clubDao.observeClubsForUser(uid)

    suspend fun getClubOnce(id: String): ClubEntity? = clubDao.getById(id)

    /** Searches only THIS device's own clubs — kept for the "My Clubs" screen's own use, if ever needed offline. */
    suspend fun searchByName(uid: String, query: String): List<ClubEntity> = clubDao.searchByName(uid, query)

    /** Registers a brand new club, owned by whoever is currently signed in — saved locally AND pushed to the shared cloud directory. */
    suspend fun registerClub(
        createdByUid: String,
        name: String,
        city: String,
        establishedYear: Int,
        ballType: String
    ): String {
        val id = UUID.randomUUID().toString()
        val club = ClubEntity(
            id = id,
            name = name,
            city = city,
            establishedYear = establishedYear,
            ballType = ballType,
            createdByUid = createdByUid,
            createdAt = System.currentTimeMillis()
        )
        clubDao.upsert(club)
        // Best-effort — if this fails (no internet right now, Firestore rules not set up yet,
        // etc.) the club still exists and works perfectly for its own owner locally. It just
        // won't be findable by OTHER users' Search until a later successful sync.
        runCatching { pushToCloud(club) }
        return id
    }

    private suspend fun pushToCloud(club: ClubEntity) {
        cloudClubsRef().document(club.id).set(
            mapOf(
                "name" to club.name,
                "city" to club.city,
                "establishedYear" to club.establishedYear,
                "ballType" to club.ballType,
                "createdByUid" to club.createdByUid,
                "createdAt" to club.createdAt
            )
        ).await()
    }

    /**
     * Searches the SHARED cloud directory of every user's registered clubs — this is what makes
     * "find someone else's club" actually possible, unlike `searchByName` above which only ever
     * sees this one device's own data.
     *
     * Firestore has no built-in "contains anywhere in the name" text search, so this fetches a
     * bounded, most-recently-registered slice of the whole public directory and filters
     * client-side. That's perfectly fine at this app's current scale — it would need a real
     * search index (e.g. Algolia, or Firestore's own extensions) if the directory ever grew to
     * thousands of clubs.
     */
    suspend fun searchCloudByName(query: String): List<ClubEntity> {
        if (query.isBlank()) return emptyList()
        val snapshot = cloudClubsRef().orderBy("createdAt", Query.Direction.DESCENDING).limit(200).get().await()
        return snapshot.documents.mapNotNull { doc ->
            val name = doc.getString("name") ?: return@mapNotNull null
            if (!name.contains(query, ignoreCase = true)) return@mapNotNull null
            ClubEntity(
                id = doc.id,
                name = name,
                city = doc.getString("city") ?: "",
                establishedYear = (doc.getLong("establishedYear") ?: 0L).toInt(),
                ballType = doc.getString("ballType") ?: "LEATHER",
                createdByUid = doc.getString("createdByUid") ?: "",
                createdAt = doc.getLong("createdAt") ?: 0L
            )
        }
    }

    /** One-shot lookup of a single club from the shared cloud directory, by id — used when opening a club found via Search. */
    suspend fun getCloudClub(id: String): ClubEntity? {
        val doc = cloudClubsRef().document(id).get().await()
        if (!doc.exists()) return null
        val name = doc.getString("name") ?: return null
        return ClubEntity(
            id = doc.id,
            name = name,
            city = doc.getString("city") ?: "",
            establishedYear = (doc.getLong("establishedYear") ?: 0L).toInt(),
            ballType = doc.getString("ballType") ?: "LEATHER",
            createdByUid = doc.getString("createdByUid") ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L
        )
    }
}
