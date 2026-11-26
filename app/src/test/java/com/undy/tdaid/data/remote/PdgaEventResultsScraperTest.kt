package com.undy.tdaid.data.remote

import org.jsoup.Jsoup
import org.junit.Assert.assertEquals
import org.junit.Test

// Modeled directly on the real markup at pdga.com/tour/event/{id} — one <details> block per
// division, its code as the h3's id.
private fun eventPage(vararg divisions: Pair<String, String>): org.jsoup.nodes.Document {
    val blocks = divisions.joinToString("") { (code, rowsHtml) ->
        """<details open><summary><h3 class="division" id="$code">$code &middot; Division <span class="players"> (2)</span></h3></summary>
           |<div class="table-container"><table class="results sticky-enabled" id="tournament-stats-0">
           |<thead><tr><th class="place">Place</th><th class="points">Points</th><th class="name">Name</th><th class="pdga-number">PDGA#</th><th class="prize">Prize (USD)</th></tr></thead>
           |<tbody>$rowsHtml</tbody></table></div></details>"""
            .trimMargin()
    }
    return Jsoup.parse("""<div class="leaderboard singles mode-normal">$blocks</div>""")
}

private fun row(place: String, points: String, pdgaNumber: String, prize: String? = null) =
    """<tr><td class="place">$place</td><td class="points">$points</td>
       |<td class="player"><a href="/player/$pdgaNumber">Someone</a></td><td class="pdga-number">$pdgaNumber</td>
       |${prize?.let { """<td class="prize">$it</td>""" } ?: ""}</tr>"""
        .trimMargin()

class PdgaEventResultsScraperTest {

    @Test
    fun `reads every division's placements, prize money in whole dollars`() {
        val placements = PdgaEventResultsScraper.placementsFrom(
            eventPage(
                "MPO" to row("1", "180.00", "161437", "$1,250") + row("2", "170.50", "80785", "$815.50"),
                "MA40" to row("1", "96.00", "244416"),
            ),
        )
        assertEquals(
            listOf(
                PdgaEventPlacement("161437", "MPO", place = 1, points = 180.0, prizeUsd = 1250),
                PdgaEventPlacement("80785", "MPO", place = 2, points = 170.5, prizeUsd = 815),
                PdgaEventPlacement("244416", "MA40", place = 1, points = 96.0, prizeUsd = 0),
            ),
            placements,
        )
    }

    @Test
    fun `skips players with no PDGA number and DNFs`() {
        val placements = PdgaEventResultsScraper.placementsFrom(
            eventPage("MA3" to row("1", "50.00", "") + row("DNF", "", "300001") + row("2", "45.00", "300002")),
        )
        assertEquals(listOf(PdgaEventPlacement("300002", "MA3", place = 2, points = 45.0, prizeUsd = 0)), placements)
    }
}
