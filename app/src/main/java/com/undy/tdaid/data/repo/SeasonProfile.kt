package com.undy.tdaid.data.repo

import com.undy.tdaid.data.local.SeasonResult
import com.undy.tdaid.data.model.asOrdinal
import com.undy.tdaid.data.remote.PdgaPlayerProfile
import com.undy.tdaid.data.remote.PdgaProfileScraper
import java.util.Calendar

/** The same recent/win/best facts the profile page scrape gives (see [PdgaPlayerProfile]), rebuilt
 *  from one player's results in the stored season library instead — for while their profile page
 *  is blocked. Covers whatever window the results passed in cover (a rolling 12 months, from the
 *  caller), so [PdgaPlayerProfile.bestResultThisYear] here means best in that window. Only as
 *  complete as the library: events outside the country it covers, and wins from before that
 *  window, aren't in it. No member-since year either; that only ever came from the profile page.
 *  A result from any year but [currentYear] gets that year appended, since without it last
 *  December's event reads exactly like one from this week — [currentYear] defaults to the real
 *  current year but is overridable so tests don't depend on when they happen to run. Null when the
 *  player has no stored results. */
internal fun seasonProfileFrom(
    results: List<SeasonResult>,
    currentYear: Int = Calendar.getInstance().get(Calendar.YEAR),
): PdgaPlayerProfile? {
    if (results.isEmpty()) return null
    fun SeasonResult.label(): String {
        val year = endDate.take(4).toIntOrNull()
        val yearSuffix = if (year != null && year != currentYear) " ($year)" else ""
        return "${place.asOrdinal()} · ${PdgaProfileScraper.stripTrailingDivisionList(eventName)}$yearSuffix"
    }
    return PdgaPlayerProfile(
        memberSince = null,
        recentResult = results.maxByOrNull { it.endDate }?.label(),
        lastWin = results.filter { it.place == 1 }.maxByOrNull { it.endDate }?.label(),
        // Same ranking as the profile page scrape: real prize money first, then rating points,
        // falling back to placement only to break an exact tie.
        bestResultThisYear = results.maxWithOrNull(compareBy({ it.prizeUsd }, { it.points }, { -it.place }))?.label(),
    )
}
