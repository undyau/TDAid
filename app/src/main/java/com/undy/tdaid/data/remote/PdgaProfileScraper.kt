package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Calendar
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

private const val USER_AGENT = "Mozilla/5.0 (Android) TDAid/1.0"

// pdga.com's own robots.txt says `Crawl-delay: 10` — applies to every request to the host, so a
// second request for the same player (the wins page, below) still has to wait this long after
// the first one.
private const val CRAWL_DELAY_MS = 10_000L

// PDGA division codes look like MPO, FPO, MA1, MA40, FJ18, etc.
private const val DIVISION_TOKEN = """[A-Z]{2,4}\d{0,3}"""
private val TRAILING_DIVISION_LIST_IN_PARENS = Regex(
    """\s*\(\s*$DIVISION_TOKEN(?:\s*[,/&]\s*$DIVISION_TOKEN)+\s*\)\s*$"""
)
private val TRAILING_DIVISION_LIST_BARE = Regex(
    """\s*[-–—]?\s*$DIVISION_TOKEN(?:\s*[,/&]\s*$DIVISION_TOKEN)+\s*$"""
)

/** A player's real member-since year and this-year results, across every division they've
 *  played, scraped from their public PDGA profile page — everything an announcer bio can draw
 *  from. */
data class PdgaPlayerProfile(
    val memberSince: String?,
    /** Their most recently played event & placement, in whichever division that was — a player
     *  isn't necessarily playing the same division at every event this year. */
    val recentResult: String?,
    /** Their most recent career win, in any division, if they have one — not scoped to this
     *  year, since most players go a whole year or more between wins. */
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
 *  robots.txt Crawl-delay instead of firing them all at once. May issue a second, further-delayed
 *  request for the same player (see [fetchLastCareerWin]). */
class PdgaProfileScraper {

    /** Not scoped to any one division — a player's bio should reflect their real activity across
     *  every division they've played, not just whichever one they're registered in for the
     *  tournament that happened to trigger this fetch. */
    suspend fun fetchProfile(pdgaNumber: String): PdgaPlayerProfile =
        withContext(Dispatchers.IO) {
            val document = Jsoup.connect("https://www.pdga.com/player/$pdgaNumber")
                .userAgent(USER_AGENT)
                .timeout(15_000)
                .get()

            val memberSince = document.selectFirst("li.join-date")?.ownText()?.trim()?.ifEmpty { null }

            val (recentResult, bestResultThisYear) = recentAndBestResultFrom(document)

            // The main profile page only lists the current year's results, so a win from any
            // earlier year never shows up in recentAndBestResultFrom's results above — that's
            // what a real player hit: no win yet this year, but a real career win last December
            // that the bio should still surface. The player's own "Career Wins" count (right on
            // this same page, no extra request needed) tells us whether that second page is even
            // worth fetching — most amateur players have zero career wins, so this skips the vast
            // majority.
            val careerWinsCount = document.selectFirst("li.career-wins a")?.text()?.trim()?.toIntOrNull() ?: 0
            val lastWin = if (careerWinsCount > 0) fetchLastCareerWin(pdgaNumber) else null

            PdgaPlayerProfile(
                memberSince = memberSince,
                recentResult = recentResult,
                lastWin = lastWin,
                bestResultThisYear = bestResultThisYear,
            )
        }

    /** The player's single most recent win, across every division they've ever won in, from
     *  their dedicated "Career Wins" page — the main profile page only lists the current year.
     *  A network hiccup here shouldn't sink the rest of an otherwise-successful profile fetch, so
     *  this swallows its own failures and just comes back empty. */
    private suspend fun fetchLastCareerWin(pdgaNumber: String): String? = runCatching {
        delay(CRAWL_DELAY_MS)
        val document = Jsoup.connect("https://www.pdga.com/player/$pdgaNumber/wins")
            .userAgent(USER_AGENT)
            .timeout(15_000)
            .get()
        lastWinLabelFrom(document)
    }.getOrNull()

    companion object {
        private data class Result(
            val place: Int,
            val points: Double,
            val prizeDollars: Int,
            val dateKey: Long,
            val label: String,
        )

        /** The main profile page's `recentResult` and `bestResultThisYear` facts, combined across
         *  every division's results table on the page — a player can be registered in a different
         *  division per event (age brackets open up through the year, or they fill in for a
         *  smaller division), so finding their real most recent event means checking all of them,
         *  not just whichever division happens to match the tournament that triggered this fetch.
         *  Internal (rather than private) so it can be unit tested directly, against a real page
         *  fragment, without a network call. */
        internal fun recentAndBestResultFrom(profilePageDocument: Document): Pair<String?, String?> {
            val results = profilePageDocument.select("table[id^=player-results-]")
                .flatMap { it.select("tbody tr") }
                .mapNotNull { it.toResult() }
            val recentResult = results.maxByOrNull { it.dateKey }?.label
            // Real prize money first (professional, cash events), then real rating points
            // (everything else, including amateur divisions that never pay cash) — falling back
            // to placement only to break an exact tie deterministically.
            val bestResultThisYear = results
                .maxWithOrNull(compareBy({ it.prizeDollars }, { it.points }, { -it.place }))
                ?.label
            return recentResult to bestResultThisYear
        }

        private fun Element.toResult(): Result? {
            val placeText = selectFirst("td.place")?.text()?.trim() ?: return null
            val place = placeText.toIntOrNull() ?: return null
            val tournament = selectFirst("td.tournament a")?.text()?.trim()?.let(::stripTrailingDivisionList) ?: return null
            val points = selectFirst("td.points")?.text()?.trim()?.toDoubleOrNull() ?: 0.0
            val prizeDollars = selectFirst("td.prize")?.text()?.trim()?.filter { it.isDigit() }?.toIntOrNull() ?: 0
            // Unix epoch seconds, unlike the ISO date string on the separate Career Wins page's table.
            val dateKey = selectFirst("td.dates")?.attr("data-text")?.trim()?.toLongOrNull() ?: 0L
            return Result(place, points, prizeDollars, dateKey, "${placeText.asPlaceLabel()} · $tournament")
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

        /** Multi-division events sometimes list every division the tournament covered right on
         *  the end of its name, e.g. "Turkey Shoot (MPO, MA1, MA40)" — that's noise for a bio,
         *  not part of the event's actual name, so strip it. Internal (rather than private) so
         *  it can be unit tested directly. */
        internal fun stripTrailingDivisionList(name: String): String =
            name.replace(TRAILING_DIVISION_LIST_IN_PARENS, "")
                .replace(TRAILING_DIVISION_LIST_BARE, "")
                .trim()

        private data class Win(val dateKey: String, val tournament: String)

        /** Parses the "Career Wins" page's table into a "1st · <tournament>" label for whichever
         *  row is most recent, by [Win.dateKey] (the ISO date PDGA already stamps on each row, so
         *  no date-format parsing is needed — a plain string comparison sorts it correctly). A win
         *  from any year but [currentYear] gets that year appended, since without it a year-old
         *  win reads exactly like one from this week. Internal (rather than private) so it can be
         *  unit tested directly, against a real page fragment, without a network call —
         *  [currentYear] defaults to the real current year but is overridable so those tests don't
         *  depend on when they happen to run. */
        internal fun lastWinLabelFrom(
            winsPageDocument: Document,
            currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
        ): String? {
            val win = winsPageDocument.selectFirst("table#player-wins")
                ?.select("tbody tr").orEmpty()
                .mapNotNull { it.toWin() }
                .maxByOrNull { it.dateKey }
                ?: return null
            val winYear = win.dateKey.take(4).toIntOrNull()
            val yearSuffix = if (winYear != null && winYear != currentYear) " ($winYear)" else ""
            return "1st · ${win.tournament}$yearSuffix"
        }

        private fun Element.toWin(): Win? {
            val dateKey = selectFirst("td.dates")?.attr("data-text")?.trim()?.ifEmpty { null } ?: return null
            val tournament = selectFirst("td.tournament a")?.text()?.trim()?.let(::stripTrailingDivisionList) ?: return null
            return Win(dateKey, tournament)
        }
    }
}
