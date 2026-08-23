package com.undy.tdaid.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.undy.tdaid.data.remote.PdgaPlayerProfile

/** A player's real member-since date and this-year results, cached per tournament so it's only
 *  ever scraped once from PDGA's public profile pages — reloading the same tournament later (e.g.
 *  after an app restart) reuses this instead of re-fetching every player again. */
@Entity(tableName = "player_profile_cache", primaryKeys = ["tournamentId", "pdgaNumber"])
data class PlayerProfileCacheEntity(
    val tournamentId: String,
    val pdgaNumber: String,
    val memberSince: String?,
    val recentResult: String?,
    val lastWin: String?,
    val bestResultThisYear: String?,
)

@Dao
interface PlayerProfileCacheDao {
    @Query("SELECT * FROM player_profile_cache WHERE tournamentId = :tournamentId")
    suspend fun forTournament(tournamentId: String): List<PlayerProfileCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayerProfileCacheEntity)
}

interface PlayerProfileCacheRepository {
    /** Everything cached for this tournament so far, keyed by PDGA number. */
    suspend fun get(tournamentId: String): Map<String, PdgaPlayerProfile>
    suspend fun save(tournamentId: String, pdgaNumber: String, profile: PdgaPlayerProfile)
}

class RoomPlayerProfileCacheRepository(private val dao: PlayerProfileCacheDao) : PlayerProfileCacheRepository {
    override suspend fun get(tournamentId: String): Map<String, PdgaPlayerProfile> =
        dao.forTournament(tournamentId).associate {
            it.pdgaNumber to PdgaPlayerProfile(it.memberSince, it.recentResult, it.lastWin, it.bestResultThisYear)
        }

    override suspend fun save(tournamentId: String, pdgaNumber: String, profile: PdgaPlayerProfile) {
        dao.upsert(
            PlayerProfileCacheEntity(
                tournamentId = tournamentId,
                pdgaNumber = pdgaNumber,
                memberSince = profile.memberSince,
                recentResult = profile.recentResult,
                lastWin = profile.lastWin,
                bestResultThisYear = profile.bestResultThisYear,
            ),
        )
    }
}
