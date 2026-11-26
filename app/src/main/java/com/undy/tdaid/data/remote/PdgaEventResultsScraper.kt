package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.HttpStatusException
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

private const val USER_AGENT = "Mozilla/5.0 (Android) TDAid/1.0"

/** One player's final result in one division of a completed event. */
data class PdgaEventPlacement(
    val pdgaNumber: String,
    val division: String,
    val place: Int,
    /** PDGA rating points for the finish — what ranks results at events that pay no cash. */
    val points: Double,
    /** Whole US dollars, as the event page itself converts it; 0 for no cash. */
    val prizeUsd: Int,
)

/**
 * Reads a completed event's official results from its public page (`pdga.com/tour/event/{id}`),
 * which lists every division's final standings in one server-rendered page — one request per
 * event, rather than one per division through PDGA Live. This is how a player's recent and best
 * results are rebuilt while their own profile page is behind pdga.com's bot check (see
 * [PdgaProfileBlockedException]); event pages weren't behind it when that started (Nov 2026).
 * Same host as the profile pages, so callers must pace these at its robots.txt Crawl-delay.
 */
class PdgaEventResultsScraper {

    suspend fun fetchResults(tournamentId: String): List<PdgaEventPlacement> = withContext(Dispatchers.IO) {
        val document = try {
            Jsoup.connect("https://www.pdga.com/tour/event/$tournamentId")
                .userAgent(USER_AGENT)
                .timeout(15_000)
                .get()
        } catch (e: HttpStatusException) {
            if (e.statusCode == 403 || e.statusCode == 503) {
                throw PdgaProfileBlockedException("pdga.com returned HTTP ${e.statusCode} for event pages")
            }
            throw e
        }
        if (document.title().startsWith("Just a moment")) {
            throw PdgaProfileBlockedException("pdga.com served a bot check instead of the event page")
        }
        placementsFrom(document)
    }

    companion object {
        /** Each division is a `<details>` block holding an `h3.division` (whose id is the division
         *  code) and its `table.results`. Players without a PDGA number have nothing to match
         *  against and are skipped, as are DNFs (no numeric place). Internal (rather than private)
         *  so it can be unit tested directly, against a real page fragment, without a network
         *  call. */
        internal fun placementsFrom(eventPageDocument: Document): List<PdgaEventPlacement> =
            eventPageDocument.select("details").flatMap { block ->
                val division = block.selectFirst("h3.division")?.id()?.trim()?.ifEmpty { null } ?: return@flatMap emptyList()
                block.select("table.results tbody tr").mapNotNull { row ->
                    val pdgaNumber = row.selectFirst("td.pdga-number")?.text()?.trim()?.ifEmpty { null } ?: return@mapNotNull null
                    val place = row.selectFirst("td.place")?.text()?.trim()?.toIntOrNull() ?: return@mapNotNull null
                    PdgaEventPlacement(
                        pdgaNumber = pdgaNumber,
                        division = division,
                        place = place,
                        points = row.selectFirst("td.points")?.text()?.trim()?.toDoubleOrNull() ?: 0.0,
                        prizeUsd = row.selectFirst("td.prize")?.text()?.substringBefore('.')?.filter { it.isDigit() }?.toIntOrNull() ?: 0,
                    )
                }
            }
    }
}
