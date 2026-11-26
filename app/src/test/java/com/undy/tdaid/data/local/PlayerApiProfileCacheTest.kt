package com.undy.tdaid.data.local

import com.undy.tdaid.data.remote.PdgaApiPlayerStats
import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerApiProfileCacheTest {

    @Test
    fun `stats survive the cache's text form`() {
        val stats = listOf(
            PdgaApiPlayerStats(year = 2026, divisionCode = "MPO", tournaments = 9, prize = 1180.5),
            PdgaApiPlayerStats(year = 2026, divisionCode = "MP40", tournaments = 3, prize = 0.0),
        )
        assertEquals(stats, decodeStats(encodeStats(stats)))
    }

    @Test
    fun `a player with no stats round trips to empty`() {
        assertEquals(emptyList<PdgaApiPlayerStats>(), decodeStats(encodeStats(emptyList())))
    }
}
