package com.undy.tdaid.data.repo

import com.undy.tdaid.data.model.RowStatus
import com.undy.tdaid.notify.TeeAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FakeTournamentRepositoryTest {

    private lateinit var repository: TournamentRepository

    @Before
    fun setUp() {
        repository = FakeTournamentRepository()
    }

    @Test
    fun `tee groups are non-empty and every player has a unique id`() {
        val groups = repository.teeGroups()
        assertTrue(groups.isNotEmpty())

        val ids = groups.flatMap { it.players }.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `MPO division's starter count matches the number of players in tee groups`() {
        val mpo = repository.divisions().first { it.code == "MPO" }
        val playerCount = repository.teeGroups().sumOf { it.players.size }
        assertEquals(mpo.starterCount, playerCount)
    }

    @Test
    fun `full schedule contains exactly one current row`() {
        val currentRows = repository.fullSchedule().count { it.status == RowStatus.CURRENT }
        assertEquals(1, currentRows)
    }

    @Test
    fun `full schedule rows are in chronological order`() {
        // The screen displays these top-to-bottom assuming chronological order; a data-entry
        // regression here would silently produce a confusing, out-of-order schedule.
        val millis = repository.fullSchedule().map { requireNotNull(TeeAlarmScheduler.parseTodayMillis(it.time)) }
        assertEquals(millis, millis.sorted())
    }
}
