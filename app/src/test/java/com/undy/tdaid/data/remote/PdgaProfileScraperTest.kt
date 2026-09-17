package com.undy.tdaid.data.remote

import org.junit.Assert.assertEquals
import org.junit.Test

class PdgaProfileScraperTest {

    @Test
    fun `strips a parenthesized division list from the end of a tournament name`() {
        assertEquals(
            "Turkey Shoot",
            PdgaProfileScraper.stripTrailingDivisionList("Turkey Shoot (MPO, MA1, MA40)"),
        )
    }

    @Test
    fun `strips a bare division list separated by a dash`() {
        assertEquals(
            "Turkey Shoot",
            PdgaProfileScraper.stripTrailingDivisionList("Turkey Shoot - MPO, MA1, MA40"),
        )
    }

    @Test
    fun `strips a bare division list with no separator at all`() {
        assertEquals(
            "Turkey Shoot",
            PdgaProfileScraper.stripTrailingDivisionList("Turkey Shoot MPO, MA1, MA40"),
        )
    }

    @Test
    fun `leaves a single trailing division alone, since one division isn't a list`() {
        assertEquals(
            "Turkey Shoot (MPO)",
            PdgaProfileScraper.stripTrailingDivisionList("Turkey Shoot (MPO)"),
        )
    }

    @Test
    fun `leaves a name with no division suffix unchanged`() {
        assertEquals(
            "Turkey Shoot",
            PdgaProfileScraper.stripTrailingDivisionList("Turkey Shoot"),
        )
    }

    @Test
    fun `leaves a sponsor name in parens alone, since it isn't a division list`() {
        assertEquals(
            "Ledgestone Insurance Open presented by Discraft (Ledgestone)",
            PdgaProfileScraper.stripTrailingDivisionList(
                "Ledgestone Insurance Open presented by Discraft (Ledgestone)",
            ),
        )
    }
}
