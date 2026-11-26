package com.undy.tdaid.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.undy.tdaid.data.remote.PdgaEventPlacement

/** A completed event whose official results have been read (see
 *  [com.undy.tdaid.data.remote.PdgaEventResultsScraper]). Not scoped to whichever tournament the
 *  TD is running — a finished event's results don't change, so each one is fetched once ever and
 *  shared by every later load, and isn't wiped by "clear bio data on new event". */
@Entity(tableName = "season_events", primaryKeys = ["tournamentId"])
data class SeasonEventEntity(
    val tournamentId: String,
    val name: String,
    /** YYYY-MM-DD, so plain string comparison sorts chronologically. */
    val endDate: String,
)

@Entity(tableName = "season_results", primaryKeys = ["tournamentId", "pdgaNumber", "division"])
data class SeasonResultEntity(
    val tournamentId: String,
    val pdgaNumber: String,
    val division: String,
    val place: Int,
    val points: Double,
    val prizeUsd: Int,
)

/** One player's finish at one event, joined with that event's name and date. */
data class SeasonResult(
    val tournamentId: String,
    val eventName: String,
    val endDate: String,
    val pdgaNumber: String,
    val division: String,
    val place: Int,
    val points: Double,
    val prizeUsd: Int,
)

@Dao
interface SeasonResultsDao {
    @Query("SELECT tournamentId FROM season_events")
    suspend fun eventIds(): List<String>

    @Query(
        """SELECT e.tournamentId, e.name AS eventName, e.endDate, r.pdgaNumber, r.division, r.place, r.points, r.prizeUsd
           FROM season_results r JOIN season_events e ON e.tournamentId = r.tournamentId
           WHERE e.endDate >= :fromDate""",
    )
    suspend fun resultsSince(fromDate: String): List<SeasonResult>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertEvent(event: SeasonEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertResults(results: List<SeasonResultEntity>)

    /** The event row is what marks it as fetched, so it and its results land together or not
     *  at all — a crash between the two can't leave an event that looks done but has no results. */
    @Transaction
    suspend fun saveEvent(event: SeasonEventEntity, results: List<SeasonResultEntity>) {
        upsertResults(results)
        upsertEvent(event)
    }
}

interface SeasonResultsRepository {
    /** Every event whose results are already stored. */
    suspend fun fetchedEventIds(): Set<String>
    /** Every stored result from events ending on or after [fromDate] (YYYY-MM-DD). */
    suspend fun resultsSince(fromDate: String): List<SeasonResult>
    suspend fun saveEvent(tournamentId: String, name: String, endDate: String, placements: List<PdgaEventPlacement>)
}

class RoomSeasonResultsRepository(private val dao: SeasonResultsDao) : SeasonResultsRepository {
    override suspend fun fetchedEventIds(): Set<String> = dao.eventIds().toSet()

    override suspend fun resultsSince(fromDate: String): List<SeasonResult> = dao.resultsSince(fromDate)

    override suspend fun saveEvent(tournamentId: String, name: String, endDate: String, placements: List<PdgaEventPlacement>) {
        dao.saveEvent(
            SeasonEventEntity(tournamentId, name, endDate),
            placements.map { SeasonResultEntity(tournamentId, it.pdgaNumber, it.division, it.place, it.points, it.prizeUsd) },
        )
    }
}
