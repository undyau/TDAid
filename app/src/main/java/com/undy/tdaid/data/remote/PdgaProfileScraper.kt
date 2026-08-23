package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val USER_AGENT = "Mozilla/5.0 (Android) TDAid/1.0"

/** A player's real member-since year and this-year results for one division, scraped from their
 *  public PDGA profile page — everything an announcer bio can draw from. */
data class PdgaPlayerProfile(
    val memberSince: String?,
    /** Their most recently played event & placement. */
    val recentResult: String?,
    /** Their most recent 1st-place finish this year, if any. */
    val lastWin: String?,
    /** Their most valuable result this year, if different from the two above — ranked by real
     *  prize money for events that pay cash, or by real PDGA rating points otherwise (most
     *  amateur and small events), not by raw placement. A lower finish at a bigger, more
     *  competitive event is often worth more than a "better" placement at a small one — e.g. a
     *  real player's own results this year: 4th at a small one-round event earned 10 rating
     *  points, while 14th at a bigger one earned 210. */
    val bestResultThisYear: String?,
)

/** There's no documented API for member-since or results — this scrapes the same public profile
 *  page a human would see (`pdga.com/player/{number}`), which is plain server-rendered HTML, not
 *  a JS-driven page. No login needed. Called at most once per player per real event load — see
 *  [com.undy.tdaid.data.repo.LiveRosterRepository], which paces these calls to respect pdga.com's
 *  robots.txt Crawl-delay instead of firing them all at once. */
class PdgaProfileScraper {

    suspend fun fetchProfile(pdgaNumber: String, divisionCode: String): PdgaPlayerProfile =
        withContext(Dispatchers.IO) {
            val document = Jsoup.connect("https://www.pdga.com/player/$pdgaNumber")
                .userAgent(USER_AGENT)
                .timeout(15_000)
                .get()

            val memberSince = document.selectFirst("li.join-date")?.ownText()?.trim()?.ifEmpty { null }

            val resultsTable = document.selectFirst("table#player-results-${divisionCode.lowercase()}")
                ?: document.selectFirst("table[id^=player-results-]")
            // Rows run oldest to newest, so the last row is the most recently played event.
            val results = resultsTable?.select("tbody tr").orEmpty().mapNotNull { it.toResult() }

            PdgaPlayerProfile(
                memberSince = memberSince,
                recentResult = results.lastOrNull()?.label,
                lastWin = results.lastOrNull { it.place == 1 }?.label,
                // Real prize money first (professional, cash events), then real rating points
                // (everything else, including amateur divisions that never pay cash) — falling
                // back to placement only to break an exact tie deterministically.
                bestResultThisYear = results
                    .maxWithOrNull(compareBy({ it.prizeDollars }, { it.points }, { -it.place }))
                    ?.label,
            )
        }

    private data class Result(val place: Int, val points: Double, val prizeDollars: Int, val label: String)

    private fun Element.toResult(): Result? {
        val placeText = selectFirst("td.place")?.text()?.trim() ?: return null
        val place = placeText.toIntOrNull() ?: return null
        val tournament = selectFirst("td.tournament a")?.text()?.trim() ?: return null
        val points = selectFirst("td.points")?.text()?.trim()?.toDoubleOrNull() ?: 0.0
        val prizeDollars = selectFirst("td.prize")?.text()?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0
        return Result(place, points, prizeDollars, "${placeText.asPlaceLabel()} · $tournament")
    }

    private fun String.asPlaceLabel(): String {
        val n = toIntOrNull() ?: return this
        val suffix = when {
            n % 100 in 11..13 -> "th"
            n % 10 == 1 -> "st"
            n % 10 == 2 -> "nd"
            n % 10 == 3 -> "rd"
            else -> "th"
        }
        return "$n$suffix"
    }
}
