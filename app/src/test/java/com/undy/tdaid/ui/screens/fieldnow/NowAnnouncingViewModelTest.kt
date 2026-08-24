package com.undy.tdaid.ui.screens.fieldnow

import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.remote.PdgaCourseMeta
import com.undy.tdaid.data.repo.LiveRoster
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
