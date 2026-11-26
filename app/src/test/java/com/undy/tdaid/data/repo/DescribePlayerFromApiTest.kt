package com.undy.tdaid.data.repo

import com.undy.tdaid.data.remote.PdgaApiPlayerStats
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
            "From Canberra, ACT · 12 PDGA events in 2026 · \$1,241 prize money in 2026 · played MPO & MP40.",
            describePlayerFromApi("Canberra, ACT", stats),
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
    fun `falls back to location alone with no stats`() {
        assertEquals("From Sydney, NSW.", describePlayerFromApi("Sydney, NSW", emptyList()))
    }

    @Test
    fun `returns null with nothing to say`() {
        assertNull(describePlayerFromApi(null, emptyList()))
    }
}
