package com.undy.tdaid.data.repo

import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** One real division/round's worth of starters & tee times, fetched from PDGA Live. Roster and
 *  Field Mode both read the same cached value (via [LiveRosterRepository]), so once a TD loads
 *  real data for a division, both screens show it and stay in agreement — same as they already
 *  do for the demo data. */
data class LiveRoster(
    val tournamentId: String,
    val division: String,
    val round: Int,
    val groups: List<TeeGroup>,
)

/**
 * Caches the real roster loaded from PDGA Live's unauthenticated `live_results_fetch_round`
 * endpoint for whichever division/round the TD is actually running. Demo tournaments never
 * populate this — screens fall back to [TournamentRepository]'s sample data until a real load
 * succeeds here.
 */
interface LiveRosterRepository {
    val current: StateFlow<LiveRoster?>
    val loading: StateFlow<Boolean>
    val error: StateFlow<String?>
    suspend fun load(tournamentId: String, division: String, round: Int)
}

class RealLiveRosterRepository(private val pdgaRepository: PdgaRepository) : LiveRosterRepository {
    private val _current = MutableStateFlow<LiveRoster?>(null)
    override val current: StateFlow<LiveRoster?> = _current.asStateFlow()
    private val _loading = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    override suspend fun load(tournamentId: String, division: String, round: Int) {
        _loading.value = true
        _error.value = null
        pdgaRepository.fetchLiveResults(tournamentId, division, round)
            .onSuccess { results ->
                // Real foursomes group by TeeTime, not the API's CardNum (which turned out to be
                // a ~30-player wave/pod id, not a playing group — confirmed against live data).
                val groups = results
                    .groupBy { it.teeTime }
                    .entries
                    .sortedBy { it.key }
                    .map { (teeTime, players) ->
                        TeeGroup(
                            time = teeTime.ifEmpty { "TBD" },
                            players = players.map { r ->
                                Player(
                                    id = "pdga-${r.pdgaNumber}",
                                    name = "${r.firstName} ${r.lastName}".trim(),
                                    pdga = PdgaProfile(pdgaNumber = r.pdgaNumber.toString(), rating = r.rating ?: 0, memberSince = "—"),
                                    recentResult = "—",
                                    bio = "Real PDGA Live starter — use \"Check Live PDGA/ADG Data\" for a full profile.",
                                )
                            },
                        )
                    }
                _current.value = LiveRoster(tournamentId, division, round, groups)
                if (groups.isEmpty()) _error.value = "No tee times published yet for this round"
            }
            .onFailure { e -> _error.value = e.message ?: "Live lookup failed" }
        _loading.value = false
    }
}
