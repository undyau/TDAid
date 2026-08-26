package com.undy.tdaid.data.model

/** A player's core PDGA identity facts — fetched, not editable in-app. */
data class PdgaProfile(
    val pdgaNumber: String,
    /** Null when PDGA hasn't assigned this player a rating yet, e.g. a new or inactive member. */
    val rating: Int?,
    val memberSince: String,
    /** City/country as reported by PDGA — null for any player who hasn't listed one. */
    val homeLocation: String? = null,
) {
    /** False for a real starter with no PDGA member number (e.g. an amateur/junior in a mixed
     *  local event) — there's no pdga.com profile to link to or prefetch for these players. */
    val hasPdgaNumber: Boolean get() = pdgaNumber.isNotBlank()
}

/** Optional ranking pulled from the Australian Disc Golf (ADG) Tour Leaderboard. Null if unranked there. */
data class AdgRanking(
    val rank: Int,
    val division: String,
    val points: Int,
)

/** A completed round's result: strokes relative to par, and the raw stroke count. */
data class RoundResult(
    val scoreToPar: Int,
    val strokes: Int,
)

/** Where a player stands in the tournament so far. */
data class TournamentStanding(
    val scoreToPar: Int,
    val position: String,
)

data class Player(
    val id: String,
    val name: String,
    val pdga: PdgaProfile,
    val recentResult: String,
    val bio: String,
    val hasCustomNotes: Boolean = false,
    val round1: RoundResult? = null,
    val overall: TournamentStanding? = null,
    /** Every ADG Tour division this player is ranked in — a player can be ranked in more than one
     *  (e.g. Open and Masters), so this isn't just their single best ranking. Empty if unranked. */
    val adg: List<AdgRanking> = emptyList(),
    /** Which real division this player is competing in. A tee group can legitimately mix
     *  divisions (a shared card teeing off together), so this lives on the player rather than
     *  only on the group. */
    val division: String = "",
) {
    val initials: String
        get() = name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
}

/** Formats a rank as "1st", "2nd", "3rd", "4th", etc. */
fun Int.asOrdinal(): String {
    val suffix = when {
        this % 100 in 11..13 -> "th"
        this % 10 == 1 -> "st"
        this % 10 == 2 -> "nd"
        this % 10 == 3 -> "rd"
        else -> "th"
    }
    return "$this$suffix"
}

/** The [Player.id] a real PDGA-sourced starter gets — used anywhere a PDGA number needs to be
 *  matched back to a player, such as bio-note lookups, without depending on a roster load. */
fun playerIdForPdgaNumber(pdgaNumber: String) = "pdga-$pdgaNumber"

data class TeeGroup(
    val time: String,
    val players: List<Player>,
    /** Which real division this group belongs to — blank when its players aren't all the same
     *  division. Lets a merged, cross-division announcing queue show which division each group
     *  is (divisions commonly tee off in the same window). */
    val division: String = "",
)

data class Division(
    val code: String,
    val starterCount: Int,
    /** The course/layout this division is actually playing — null until that division's own
     *  roster has loaded. Shown instead of PDGA's full division name (e.g. "Mixed Pro Open"),
     *  since the code ("MPO") already says which division this is. */
    val courseName: String? = null,
)

enum class RowStatus { DONE, CURRENT, UPCOMING }

data class ScheduleRow(
    val time: String,
    val division: String,
    val names: String,
    val status: RowStatus,
    /** The real players in this group, if any — lets the schedule show each player's real live
     *  score alongside their name instead of just plain text. */
    val players: List<Player> = emptyList(),
)

/** Strokes relative to par, formatted the way an announcer would say it: "-4", "+3", or "E". */
fun Int.asScoreLabel(): String = when {
    this < 0 -> toString()
    this > 0 -> "+$this"
    else -> "E"
}
