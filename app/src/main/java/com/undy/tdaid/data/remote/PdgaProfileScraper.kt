package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

private const val USER_AGENT = "Mozilla/5.0 (Android) TDAid/1.0"

/** A player's real member-since year and most recent tournament result for one division, scraped
 *  from their public PDGA profile page. */
data class PdgaPlayerProfile(
    val memberSince: String?,
    val recentResult: String?,
)

/** There's no documented API for member-since or recent results — this scrapes the same public
 *  profile page a human would see (`pdga.com/player/{number}`), which is plain server-rendered
 *  HTML, not a JS-driven page. No login needed. Called at most once per player per real event
 *  load — see [com.undy.tdaid.data.repo.LiveRosterRepository], which paces these calls to respect
 *  pdga.com's robots.txt Crawl-delay instead of firing them all at once. */
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
            val lastRow = resultsTable?.select("tbody tr")?.lastOrNull()
            val place = lastRow?.selectFirst("td.place")?.text()?.trim()
            val tournament = lastRow?.selectFirst("td.tournament a")?.text()?.trim()
            val recentResult = if (!place.isNullOrEmpty() && !tournament.isNullOrEmpty()) {
                "${place.asPlaceLabel()} · $tournament"
            } else {
                null
            }

            PdgaPlayerProfile(memberSince = memberSince, recentResult = recentResult)
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
