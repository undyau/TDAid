package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

private const val LEADERBOARD_URL = "https://www.australiandiscgolf.com/leaderboard/"
private const val USER_AGENT = "Mozilla/5.0 (Android) TDAid/1.0"

data class AdgLeaderboardRow(
    val rank: Int,
    val name: String,
    val adgNumber: String,
    val division: String,
    val points: Int,
    val eventsPlayed: Int,
)

/**
 * There is no ADG Tour API — the public leaderboard at australiandiscgolf.com is a plain,
 * server-rendered HTML page (no login, no JS-driven data fetch), so this scrapes it directly.
 * Each row carries a stable `data-label` attribute per cell, which is what makes this durable
 * against ordinary theme/styling changes (as opposed to scraping by CSS class or column index).
 */
class AdgScraper {

    suspend fun fetchLeaderboard(): List<AdgLeaderboardRow> = withContext(Dispatchers.IO) {
        val document = Jsoup.connect(LEADERBOARD_URL)
            .userAgent(USER_AGENT)
            .timeout(15_000)
            .get()

        document.select("tr.adg-leaderboard-result-row").mapNotNull { row -> row.toLeaderboardRow() }
    }

    private fun Element.toLeaderboardRow(): AdgLeaderboardRow? {
        fun cell(label: String): String? = selectFirst("td[data-label=$label]")?.text()?.trim()

        val rank = cell("Place")?.toIntOrNull() ?: return null
        val name = cell("Player") ?: return null
        val adgNumber = cell("ADG Number") ?: return null
        val division = cell("Division") ?: return null
        val points = cell("Points")?.toIntOrNull() ?: 0
        val events = selectFirst("span.adg-event-count")?.text()?.trim()?.toIntOrNull() ?: 0

        return AdgLeaderboardRow(rank, name, adgNumber, division, points, events)
    }
}
