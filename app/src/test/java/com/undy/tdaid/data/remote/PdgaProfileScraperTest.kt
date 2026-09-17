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

// Modeled directly on the real markup at pdga.com/player/{number} — one table per division,
// each row's `data-text` a Unix epoch (unlike the wins page's ISO date string).
private data class Row(
    val epochSeconds: Long,
    val tournament: String,
    val place: Int = 1,
    val points: Double = 0.0,
    val prizeDollars: Int = 0,
)

private fun profilePage(vararg divisionRows: Pair<String, List<Row>>): org.jsoup.nodes.Document {
    val tablesHtml = divisionRows.joinToString("") { (division, rows) ->
        val rowsHtml = rows.joinToString("") { row ->
            """<tr><td class="place">${row.place}</td><td class="points">${row.points}</td>
               |<td class="tournament"><a href="/tour/event/1#$division">${row.tournament}</a></td>
               |<td class="tier">C</td>
               |<td class="prize">${if (row.prizeDollars > 0) "$${row.prizeDollars}" else ""}</td>
               |<td class="dates" data-text="${row.epochSeconds}">a date</td></tr>"""
                .trimMargin()
        }
        """<table id="player-results-${division.lowercase()}"><tbody>$rowsHtml</tbody></table>"""
    }
    return Jsoup.parse(tablesHtml)
}

class PdgaProfileScraperTest {

    @Test
    fun `recent result comes from whichever division was played most recently, not the first table`() {
        // Modeled on PDGA #196442's real profile: MA50's table (listed first) has an event dated
        // after MA60's only event, so the true most recent result is in the second table.
        val page = profilePage(
            "MA50" to listOf(
                Row(epochSeconds = 1768107600, tournament = "Watagan Homestead First Flight", place = 7),
                Row(epochSeconds = 1785470400, tournament = "RPM Discs presents the Rumble", place = 3),
            ),
            "MA60" to listOf(
                Row(epochSeconds = 1781409600, tournament = "Sefton Sesh", place = 4),
            ),
        )
        val (recentResult, _) = PdgaProfileScraper.recentAndBestResultFrom(page)
        assertEquals("3rd · RPM Discs presents the Rumble", recentResult)
    }

    @Test
    fun `best result this year is ranked by real prize money across every division`() {
        val page = profilePage(
            "MA50" to listOf(Row(epochSeconds = 1, tournament = "Small Ams Event", place = 1, prizeDollars = 0, points = 10.0)),
            "MPO" to listOf(Row(epochSeconds = 2, tournament = "Big Cash Event", place = 14, prizeDollars = 500, points = 210.0)),
        )
        val (_, bestResultThisYear) = PdgaProfileScraper.recentAndBestResultFrom(page)
        assertEquals("14th · Big Cash Event", bestResultThisYear)
    }

    @Test
    fun `recent and best result are both null when there are no results tables at all`() {
        val (recentResult, bestResultThisYear) = PdgaProfileScraper.recentAndBestResultFrom(
            Jsoup.parse("<html><body>No results here</body></html>"),
        )
        assertNull(recentResult)
        assertNull(bestResultThisYear)
    }

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
