package com.undy.tdaid.data.repo

import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.remote.PdgaDivisionMeta
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One real division/round's worth of starters & tee times, fetched from PDGA Live. */
data class LiveRoster(
    val tournamentId: String,
    val division: String,
    val round: Int,
    val groups: List<TeeGroup>,
)

/**
 * Caches the real roster(s) loaded from PDGA Live's unauthenticated endpoints for whichever
 * tournament the TD is running. Demo tournaments never populate this — screens fall back to
 * [TournamentRepository]'s sample data until a real load succeeds here.
 *
 * [load] and [loadAllDivisions] run in the repository's own scope rather than the caller's, since
 * the caller (e.g. Tournament Search's ViewModel) is often about to be navigated away from and
 * torn down — the load must keep running and update the shared state regardless.
 */
interface LiveRosterRepository {
    /** Real rosters loaded so far, keyed by division code. */
    val rosters: StateFlow<Map<String, LiveRoster>>
    /** The real division list for whichever event [loadAllDivisions] last ran for — empty until
     *  that succeeds, so Dashboard can show real divisions (with real player counts) instead of
     *  the demo list once a TD picks a real tournament. */
    val eventDivisions: StateFlow<List<PdgaDivisionMeta>>
    val loading: StateFlow<Boolean>
    /** Human-readable progress for a multi-division load, e.g. "Loading FPO (2/4)…". Null when
     *  not loading or when a single-division [load] is in progress. */
    val loadingStatus: StateFlow<String?>
    val error: StateFlow<String?>

    /** Loads one division/round on demand — used for a manual reload, or a round the TD picks
     *  themselves that differs from whatever [loadAllDivisions] auto-loaded. */
    fun load(tournamentId: String, division: String, round: Int)

    /** Discovers every real division in the event and loads each one's current round in one
     *  go — the normal path, run right after a TD picks a real tournament. */
    fun loadAllDivisions(tournamentId: String)

    /** Drops everything cached — call when the TD switches to a different tournament (or back to
     *  the demo one), so a previous event's real data can't leak into the new selection. */
    fun clear()
}

class RealLiveRosterRepository(
    private val pdgaRepository: PdgaRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : LiveRosterRepository {
    private val _rosters = MutableStateFlow<Map<String, LiveRoster>>(emptyMap())
    override val rosters: StateFlow<Map<String, LiveRoster>> = _rosters.asStateFlow()
    private val _eventDivisions = MutableStateFlow<List<PdgaDivisionMeta>>(emptyList())
    override val eventDivisions: StateFlow<List<PdgaDivisionMeta>> = _eventDivisions.asStateFlow()
    private val _loading = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _loadingStatus = MutableStateFlow<String?>(null)
    override val loadingStatus: StateFlow<String?> = _loadingStatus.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    override fun load(tournamentId: String, division: String, round: Int) {
        scope.launch {
            _loading.value = true
            _error.value = null
            fetchOneDivision(tournamentId, division, round)
                .onSuccess { roster ->
                    _rosters.update { it + (division to roster) }
                    if (roster.groups.isEmpty()) _error.value = "No tee times published yet for this round"
                }
                .onFailure { e -> _error.value = e.message ?: "Live lookup failed" }
            _loading.value = false
        }
    }

    override fun loadAllDivisions(tournamentId: String) {
        scope.launch {
            _loading.value = true
            _error.value = null
            _loadingStatus.value = "Finding real divisions…"
            pdgaRepository.fetchEventMeta(tournamentId)
                .onSuccess { meta ->
                    _eventDivisions.value = meta.divisions
                    val loaded = mutableMapOf<String, LiveRoster>()
                    meta.divisions.forEachIndexed { index, division ->
                        _loadingStatus.value = "Loading ${division.code} (${index + 1}/${meta.divisions.size})…"
                        fetchOneDivision(tournamentId, division.code, meta.latestRound)
                            .onSuccess { roster -> loaded[division.code] = roster }
                            .onFailure { e -> _error.value = "${division.code}: ${e.message ?: "lookup failed"}" }
                    }
                    _rosters.value = loaded
                }
                .onFailure { e -> _error.value = e.message ?: "Couldn't load this event's divisions" }
            _loadingStatus.value = null
            _loading.value = false
        }
    }

    override fun clear() {
        _rosters.value = emptyMap()
        _eventDivisions.value = emptyList()
        _error.value = null
    }

    private suspend fun fetchOneDivision(tournamentId: String, division: String, round: Int): Result<LiveRoster> =
        pdgaRepository.fetchLiveResults(tournamentId, division, round).map { results ->
            // Real foursomes group by TeeTime, not the API's CardNum (which turned out to be
            // a ~30-player wave/pod id, not a playing group — confirmed against live data).
            // Groups with no published tee time yet sort last, not first.
            val groups = results
                .groupBy { it.teeTime }
                .entries
                .sortedWith(compareBy({ it.key.isEmpty() }, { it.key }))
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
            LiveRoster(tournamentId, division, round, groups)
        }
}
