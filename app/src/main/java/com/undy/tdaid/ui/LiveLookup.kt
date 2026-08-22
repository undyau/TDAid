package com.undy.tdaid.ui

import com.undy.tdaid.data.remote.AdgLeaderboardRow
import com.undy.tdaid.data.remote.PdgaPlayerResult

/** Result of an on-demand, explicit live fetch against the real PDGA API for one roster player.
 *  Never silently overwrites the card's displayed identity: [nameMismatch] flags when the PDGA
 *  number returned a real member whose name doesn't match, since our roster's numbers are demo
 *  placeholders, not necessarily real assigned numbers. */
data class PdgaLiveState(
    val loading: Boolean = false,
    val result: PdgaPlayerResult? = null,
    val notFound: Boolean = false,
    val error: String? = null,
) {
    fun nameMismatch(rosterName: String): Boolean =
        result != null && !"${result.firstName} ${result.lastName}".equals(rosterName, ignoreCase = true)
}

/** Result of an on-demand live fetch against the real ADG Tour leaderboard, matched by name. */
data class AdgLiveState(
    val loading: Boolean = false,
    val result: AdgLeaderboardRow? = null,
    val notFound: Boolean = false,
    val error: String? = null,
)
