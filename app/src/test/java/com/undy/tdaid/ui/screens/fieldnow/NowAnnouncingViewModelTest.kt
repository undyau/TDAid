package com.undy.tdaid.ui.screens.fieldnow

import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.remote.PdgaCourseMeta
import com.undy.tdaid.data.repo.LiveRoster
import com.undy.tdaid.notify.TeeAlarmScheduler
import org.junit.Assert.assertEquals
import org.junit.Test

// Real, previously-confirmed courses (verified via curl against PDGA Live's own endpoints):
// Tali Disc Golf Park (27319) from TournID 101036, Detroit Palmer Park (292766) from TournID 106944.
private val taliDiscGolfPark = PdgaCourseMeta(27319, "Tali Disc Golf Park")
private val detroitPalmerPark = PdgaCourseMeta(292766, "Detroit Palmer Park")

private fun player(id: String, division: String) = Player(
    id = id,
    name = id,
    pdga = PdgaProfile(pdgaNumber = "1", rating = null, memberSince = "—"),
    recentResult = "—",
    bio = "",
    division = division,
)

class CoursesForGroupTest {
    @Test
    fun `no course shown when every division shares the same course`() {
        val rosters = mapOf(
            "MPO" to LiveRoster("t", "MPO", 1, emptyList(), courses = listOf(taliDiscGolfPark)),
            "FPO" to LiveRoster("t", "FPO", 1, emptyList(), courses = listOf(taliDiscGolfPark)),
        )
        val group = TeeGroup(time = "9:00 AM", players = listOf(player("p1", "MPO")), division = "MPO")
        assertEquals(emptyList<String>(), coursesForGroup(group, rosters))
    }

    @Test
    fun `no course shown when course data is unavailable for every division`() {
        val rosters = mapOf(
            "MPO" to LiveRoster("t", "MPO", 1, emptyList(), courses = emptyList()),
            "MA1" to LiveRoster("t", "MA1", 1, emptyList(), courses = emptyList()),
        )
        val group = TeeGroup(time = "9:00 AM", players = listOf(player("p1", "MPO")), division = "MPO")
        assertEquals(emptyList<String>(), coursesForGroup(group, rosters))
    }

    @Test
    fun `shows the group's own course when divisions use different real courses`() {
        val rosters = mapOf(
            "MPO" to LiveRoster("t", "MPO", 1, emptyList(), courses = listOf(taliDiscGolfPark)),
            "MA1" to LiveRoster("t", "MA1", 1, emptyList(), courses = listOf(detroitPalmerPark)),
        )
        val mpoGroup = TeeGroup(time = "9:00 AM", players = listOf(player("p1", "MPO")), division = "MPO")
        val ma1Group = TeeGroup(time = "9:10 AM", players = listOf(player("p2", "MA1")), division = "MA1")
        assertEquals(listOf("Tali Disc Golf Park"), coursesForGroup(mpoGroup, rosters))
        assertEquals(listOf("Detroit Palmer Park"), coursesForGroup(ma1Group, rosters))
    }

    @Test
    fun `unions courses for a shared-tee-time group spanning two divisions on different courses`() {
        val rosters = mapOf(
            "MPO" to LiveRoster("t", "MPO", 1, emptyList(), courses = listOf(taliDiscGolfPark)),
            "MA1" to LiveRoster("t", "MA1", 1, emptyList(), courses = listOf(detroitPalmerPark)),
        )
        val mixedGroup = TeeGroup(
            time = "9:00 AM",
            players = listOf(player("p1", "MPO"), player("p2", "MA1")),
            division = "",
        )
        assertEquals(listOf("Tali Disc Golf Park", "Detroit Palmer Park"), coursesForGroup(mixedGroup, rosters))
    }
}

class CountdownLabelTest {
    private fun group(time: String) = TeeGroup(time = time, players = emptyList())

    @Test
    fun `shows real minutes remaining, not a fixed placeholder`() {
        val now = TeeAlarmScheduler.parseTodayMillis("09:00:00")!!
        assertEquals("in 30 min", countdownLabel(group("09:30:00"), now))
        assertEquals("in 90 min", countdownLabel(group("10:30:00"), now))
        assertEquals("in 5 min", countdownLabel(group("09:05:00"), now))
    }

    @Test
    fun `rounds to the nearest minute`() {
        // Tee times are always minute-granular (parseTodayMillis drops seconds), but the real
        // "now" this is compared against isn't — so the diff is realistically fractional.
        val teeMillis = TeeAlarmScheduler.parseTodayMillis("09:01:00")!!
        val now = teeMillis - 40_000L // 40 real seconds before the tee time
        assertEquals("in 1 min", countdownLabel(group("09:01:00"), now))
    }

    @Test
    fun `reports a group already at or past its tee time`() {
        val now = TeeAlarmScheduler.parseTodayMillis("09:00:00")!!
        assertEquals("now", countdownLabel(group("09:00:00"), now))
        assertEquals("10 min ago", countdownLabel(group("08:50:00"), now))
    }

    @Test
    fun `blank when the tee time doesn't parse`() {
        val now = TeeAlarmScheduler.parseTodayMillis("09:00:00")!!
        assertEquals("", countdownLabel(group("TBD"), now))
    }
}
