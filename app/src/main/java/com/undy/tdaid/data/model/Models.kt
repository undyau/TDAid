package com.undy.tdaid.data.model

/** A player's core PDGA identity facts — fetched, not editable in-app. */
data class PdgaProfile(
    val pdgaNumber: String,
    /** Null when PDGA hasn't assigned this player a rating yet, e.g. a new or inactive member. */
    val rating: Int?,
    val memberSince: String,
    /** City/country as reported by PDGA — null for demo players and any real player who hasn't
     *  listed one. */
    val homeLocation: String? = null,
)

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
    val adg: AdgRanking? = null,
    /** A real headshot published on PDGA Live — null for demo players and any real player PDGA
     *  doesn't have a photo on file for. */
    val avatarUrl: String? = null,
    /** Which real division this player is competing in — empty for demo players. A tee group can
     *  legitimately mix divisions (a shared card teeing off together), so this lives on the
     *  player rather than only on the group. */
    val division: String = "",
) {
    val initials: String
        get() = name.split(" ").mapNotNull { it.firstOrNull() }.take(2).joinToString("").uppercase()
}

data class TeeGroup(
    val time: String,
    val players: List<Player>,
    /** Which real division this group belongs to — empty for demo groups, which never need to
     *  disambiguate since there's only ever one. Lets a merged, cross-division announcing queue
     *  show which division each group is (divisions commonly tee off in the same window). */
    val division: String = "",
)

data class Division(
    val code: String,
    val name: String,
    val starterCount: Int,
)

enum class RowStatus { DONE, CURRENT, UPCOMING }

data class ScheduleRow(
    val time: String,
    val division: String,
    val names: String,
    val status: RowStatus,
    /** The real players in this group, if any — lets the schedule show each player's real live
     *  score alongside their name instead of just plain text. Empty for the demo schedule. */
    val players: List<Player> = emptyList(),
)

/** Strokes relative to par, formatted the way an announcer would say it: "-4", "+3", or "E". */
fun Int.asScoreLabel(): String = when {
    this < 0 -> toString()
    this > 0 -> "+$this"
    else -> "E"
}
