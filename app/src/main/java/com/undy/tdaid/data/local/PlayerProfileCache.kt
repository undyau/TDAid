package com.undy.tdaid.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.undy.tdaid.data.remote.PdgaApiPlayerStats
import com.undy.tdaid.data.remote.PdgaApiProfile
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

/** The official-API fallback's data for a player (see [PdgaApiProfile]), cached per tournament
 *  separately from [PlayerProfileCacheEntity] — a hit here must not count as a full profile, or
 *  the richer scrape would never be retried once pdga.com stops blocking it. [stats] is
 *  [encodeStats]'s flat text form, since it's only ever read back whole. */
@Entity(tableName = "player_api_profile_cache", primaryKeys = ["tournamentId", "pdgaNumber"])
data class PlayerApiProfileCacheEntity(
    val tournamentId: String,
    val pdgaNumber: String,
    val memberLocation: String?,
    val stats: String,
)

@Dao
interface PlayerProfileCacheDao {
    @Query("SELECT * FROM player_profile_cache WHERE tournamentId = :tournamentId")
    suspend fun forTournament(tournamentId: String): List<PlayerProfileCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: PlayerProfileCacheEntity)

    @Query("DELETE FROM player_profile_cache WHERE tournamentId = :tournamentId")
    suspend fun clearForTournament(tournamentId: String)

    @Query("SELECT * FROM player_api_profile_cache WHERE tournamentId = :tournamentId")
    suspend fun apiForTournament(tournamentId: String): List<PlayerApiProfileCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApi(entity: PlayerApiProfileCacheEntity)

    @Query("DELETE FROM player_api_profile_cache WHERE tournamentId = :tournamentId")
    suspend fun clearApiForTournament(tournamentId: String)
}

interface PlayerProfileCacheRepository {
    /** Everything cached for this tournament so far, keyed by PDGA number. */
    suspend fun get(tournamentId: String): Map<String, PdgaPlayerProfile>
    suspend fun save(tournamentId: String, pdgaNumber: String, profile: PdgaPlayerProfile)
    /** Official-API fallback data cached for this tournament so far, keyed by PDGA number. */
    suspend fun getApi(tournamentId: String): Map<String, PdgaApiProfile>
    suspend fun saveApi(tournamentId: String, pdgaNumber: String, profile: PdgaApiProfile)
    /** Drops every cached profile for this tournament — scraped and API fallback alike — so the
     *  next prefetch re-fetches everyone instead of treating them as cache hits. */
    suspend fun clear(tournamentId: String)
}

/** One "year|division|tournaments|prize" entry per row, separated by ";" — none of those fields
 *  can contain either separator. Internal so the round trip can be unit tested. */
internal fun encodeStats(stats: List<PdgaApiPlayerStats>): String =
    stats.joinToString(";") { "${it.year}|${it.divisionCode}|${it.tournaments}|${it.prize}" }

internal fun decodeStats(encoded: String): List<PdgaApiPlayerStats> =
    encoded.split(";").filter { it.isNotBlank() }.mapNotNull { row ->
        val parts = row.split("|")
        if (parts.size != 4) return@mapNotNull null
        PdgaApiPlayerStats(
            year = parts[0].toIntOrNull() ?: return@mapNotNull null,
            divisionCode = parts[1],
            tournaments = parts[2].toIntOrNull() ?: 0,
            prize = parts[3].toDoubleOrNull() ?: 0.0,
        )
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

    override suspend fun getApi(tournamentId: String): Map<String, PdgaApiProfile> =
        dao.apiForTournament(tournamentId).associate {
            it.pdgaNumber to PdgaApiProfile(it.memberLocation, decodeStats(it.stats))
        }

    override suspend fun saveApi(tournamentId: String, pdgaNumber: String, profile: PdgaApiProfile) {
        dao.upsertApi(
            PlayerApiProfileCacheEntity(
                tournamentId = tournamentId,
                pdgaNumber = pdgaNumber,
                memberLocation = profile.memberLocation,
                stats = encodeStats(profile.stats),
            ),
        )
    }

    override suspend fun clear(tournamentId: String) {
        dao.clearForTournament(tournamentId)
        dao.clearApiForTournament(tournamentId)
    }
}
