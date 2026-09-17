package com.undy.tdaid.data.remote

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// Modeled directly on the real markup at pdga.com/player/{number}/wins.
private fun winsPage(vararg rows: Pair<String, String>): org.jsoup.nodes.Document {
    val rowsHtml = rows.joinToString("") { (dateKey, tournament) ->
        """<tr><td data-text="$dateKey" class="dates">$dateKey</td>
           |<td class="tournament"><a href="/tour/event/1#MA50">$tournament</a></td>
           |<td class="division">Mixed Amateur 50+</td><td class="tier">C</td><td class="prize"></td></tr>"""
            .trimMargin()
    }
    return Jsoup.parse("""<table id="player-wins"><tbody>$rowsHtml</tbody></table>""")
}

class PdgaProfileScraperTest {

    @Test
    fun `last win label names the tournament from a single-win career-wins page`() {
        assertEquals(
            "1st · Sefton Shank",
            PdgaProfileScraper.lastWinLabelFrom(winsPage("2025-12-14" to "Sefton Shank"), currentYear = 2025),
        )
    }

    @Test
    fun `last win label picks the most recent row regardless of table order`() {
        val page = winsPage(
            "2023-01-05" to "Older Win",
            "2025-12-14" to "Newer Win",
        )
        assertEquals("1st · Newer Win", PdgaProfileScraper.lastWinLabelFrom(page, currentYear = 2025))
    }

    @Test
    fun `last win label is null when the wins table has no rows`() {
        assertNull(PdgaProfileScraper.lastWinLabelFrom(winsPage()))
    }

    @Test
    fun `last win label is null when the wins table is missing entirely`() {
        assertNull(PdgaProfileScraper.lastWinLabelFrom(Jsoup.parse("<html><body>No wins here</body></html>")))
    }

    @Test
    fun `last win label strips a trailing division list from the tournament name`() {
        assertEquals(
            "1st · Turkey Shoot",
            PdgaProfileScraper.lastWinLabelFrom(
                winsPage("2025-12-14" to "Turkey Shoot (MPO, MA1)"),
                currentYear = 2025,
            ),
        )
    }

    @Test
    fun `last win label omits the year when the win was this year`() {
        assertEquals(
            "1st · Sefton Shank",
            PdgaProfileScraper.lastWinLabelFrom(winsPage("2025-12-14" to "Sefton Shank"), currentYear = 2025),
        )
    }

    @Test
    fun `last win label appends the year when the win wasn't this year`() {
        assertEquals(
            "1st · Sefton Shank (2025)",
            PdgaProfileScraper.lastWinLabelFrom(winsPage("2025-12-14" to "Sefton Shank"), currentYear = 2026),
        )
    }

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
