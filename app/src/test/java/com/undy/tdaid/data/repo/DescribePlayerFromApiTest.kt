package com.undy.tdaid.data.repo

import com.undy.tdaid.data.remote.PdgaApiPlayerStats
import com.undy.tdaid.data.remote.PdgaPlayerProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DescribePlayerFromApiTest {

    @Test
    fun `sums a season across every division played`() {
        val stats = listOf(
            PdgaApiPlayerStats(year = 2026, divisionCode = "MPO", tournaments = 9, prize = 1180.5),
            PdgaApiPlayerStats(year = 2026, divisionCode = "MP40", tournaments = 3, prize = 60.0),
        )
        assertEquals(
            "From Canberra, ACT · 12 PDGA events in 2026 · US\$1,241 prize money in 2026 · played MPO & MP40.",
            describePlayerFromApi("Canberra, ACT", stats),
        )
    }

    @Test
    fun `lists three or more divisions naturally, most played first`() {
        val stats = listOf(
            PdgaApiPlayerStats(year = 2026, divisionCode = "FA3", tournaments = 7, prize = 0.0),
            PdgaApiPlayerStats(year = 2026, divisionCode = "FA40", tournaments = 11, prize = 0.0),
            PdgaApiPlayerStats(year = 2026, divisionCode = "FA4", tournaments = 3, prize = 0.0),
        )
        assertEquals(
            "From Cambrian Hill, VIC · 21 PDGA events in 2026 · played FA40, FA3 & FA4.",
            describePlayerFromApi("Cambrian Hill, VIC", stats),
        )
    }

    @Test
    fun `leaves out prize money for an amateur who never won cash`() {
        val stats = listOf(PdgaApiPlayerStats(year = 2026, divisionCode = "MA2", tournaments = 1, prize = 0.0))
        assertEquals("1 PDGA event in 2026.", describePlayerFromApi(null, stats))
    }

    @Test
    fun `uses only the latest season when given more than one`() {
        val stats = listOf(
            PdgaApiPlayerStats(year = 2025, divisionCode = "MA2", tournaments = 20, prize = 0.0),
            PdgaApiPlayerStats(year = 2026, divisionCode = "MA2", tournaments = 4, prize = 0.0),
        )
        assertEquals("4 PDGA events in 2026.", describePlayerFromApi(null, stats))
    }

    @Test
    fun `adds season results between the event count and prize money`() {
        val stats = listOf(PdgaApiPlayerStats(year = 2026, divisionCode = "MPO", tournaments = 9, prize = 1180.5))
        val season = PdgaPlayerProfile(
            memberSince = null,
            recentResult = "4th · NSW Open",
            lastWin = "1st · Canberra Cup",
            bestResultThisYear = "1st · Canberra Cup",
        )
        assertEquals(
            "From Canberra, ACT · 9 PDGA events in 2026 · last event: 4th · NSW Open · " +
                "last win: 1st · Canberra Cup · US\$1,181 prize money in 2026.",
            describePlayerFromApi("Canberra, ACT", stats, season),
        )
    }

    @Test
    fun `labels the best result as covering the last 12 months`() {
        val season = PdgaPlayerProfile(
            memberSince = null,
            recentResult = "9th · Turkey Shoot",
            lastWin = null,
            bestResultThisYear = "2nd · State Champs (2025)",
        )
        assertEquals(
            "last event: 9th · Turkey Shoot · best result in the last 12 months: 2nd · State Champs (2025).",
            describePlayerFromApi(null, emptyList(), season),
        )
    }

    @Test
    fun `falls back to location alone with no stats`() {
        assertEquals("From Sydney, NSW.", describePlayerFromApi("Sydney, NSW", emptyList()))
    }

    @Test
    fun `returns null with nothing to say`() {
        assertNull(describePlayerFromApi(null, emptyList()))
    }
}
