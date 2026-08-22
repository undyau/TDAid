package com.undy.tdaid.data.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelsTest {

    @Test
    fun `negative score reads as-is, e_g minus four`() {
        assertEquals("-4", (-4).asScoreLabel())
    }

    @Test
    fun `positive score gets an explicit plus sign`() {
        assertEquals("+3", 3.asScoreLabel())
    }

    @Test
    fun `zero reads as E, not plus or minus zero`() {
        assertEquals("E", 0.asScoreLabel())
    }

    @Test
    fun `player initials take the first letter of up to two words`() {
        val player = Player(
            id = "x",
            name = "Jordan Kessler",
            pdga = PdgaProfile(pdgaNumber = "1", rating = 1000, memberSince = "2020"),
            recentResult = "",
            bio = "",
        )
        assertEquals("JK", player.initials)
    }

    @Test
    fun `single-word name still produces initials without crashing`() {
        val player = Player(
            id = "x",
            name = "Cher",
            pdga = PdgaProfile(pdgaNumber = "1", rating = 1000, memberSince = "2020"),
            recentResult = "",
            bio = "",
        )
        assertEquals("C", player.initials)
    }
}
