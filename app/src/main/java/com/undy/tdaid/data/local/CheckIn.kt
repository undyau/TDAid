package com.undy.tdaid.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Whether a TD has marked a real starter as checked in, scoped per tournament round — a player's
 *  [Player.id] is derived from their PDGA number, which is stable across events, so without the
 *  tournament scope a check-in from a past event would incorrectly carry forward onto the same
 *  player in a new one. Scoped by round too, on top of that: a TD re-checks the field in fresh
 *  each round rather than carrying Round 1's check-ins forward onto Round 2. */
@Entity(tableName = "check_ins", primaryKeys = ["tournamentId", "round", "playerId"])
data class CheckInEntity(
    val tournamentId: String,
    val round: Int,
    val playerId: String,
    val checkedIn: Boolean,
)

@Dao
interface CheckInDao {
    @Query("SELECT * FROM check_ins WHERE tournamentId = :tournamentId AND round = :round")
    suspend fun forRound(tournamentId: String, round: Int): List<CheckInEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CheckInEntity)
}

interface CheckInRepository {
    /** Every check-in recorded for this tournament round so far, keyed by player id. Only players
     *  actually toggled on are present — absence means not checked in. */
    suspend fun get(tournamentId: String, round: Int): Map<String, Boolean>
    suspend fun setCheckedIn(tournamentId: String, round: Int, playerId: String, checkedIn: Boolean)
}

class RoomCheckInRepository(private val dao: CheckInDao) : CheckInRepository {
    override suspend fun get(tournamentId: String, round: Int): Map<String, Boolean> =
        dao.forRound(tournamentId, round).associate { it.playerId to it.checkedIn }

    override suspend fun setCheckedIn(tournamentId: String, round: Int, playerId: String, checkedIn: Boolean) {
        dao.upsert(CheckInEntity(tournamentId, round, playerId, checkedIn))
    }
}
