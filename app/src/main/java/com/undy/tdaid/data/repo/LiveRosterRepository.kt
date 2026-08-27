package com.undy.tdaid.data.repo

import android.content.Context
import android.os.PowerManager
import com.undy.tdaid.data.local.BioNotesRepository
import com.undy.tdaid.data.local.PlayerProfileCacheRepository
import com.undy.tdaid.data.model.AdgRanking
import com.undy.tdaid.data.model.asOrdinal
import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.model.playerIdForPdgaNumber
import com.undy.tdaid.data.model.TournamentStanding
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.remote.AdgLeaderboardRow
import com.undy.tdaid.data.remote.PdgaCourseMeta
import com.undy.tdaid.data.remote.PdgaDivisionMeta
import com.undy.tdaid.data.remote.PdgaLiveResult
import com.undy.tdaid.data.remote.PdgaPlayerProfile
import com.undy.tdaid.data.remote.PdgaProfileScraper
import com.undy.tdaid.notify.TeeAlarmScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** One real division/round's worth of starters & tee times, fetched from PDGA Live. [courses] is
 *  specifically what that division/round played — an event with per-division sites (e.g. a
 *  separate Am and Pro course) publishes a different course list per division, unlike the
 *  event-wide list which has no per-division breakdown. */
data class LiveRoster(
    val tournamentId: String,
    val division: String,
    val round: Int,
    val groups: List<TeeGroup>,
    val courses: List<PdgaCourseMeta> = emptyList(),
)

/** Re-buckets every loaded division's players by tee time from scratch, rather than just
 *  interleaving each division's already-built groups — a single physical card can carry players
 *  from more than one division (a shared tee time), and those need to surface as one group, not
 *  fragment into a separate one per division. A group's [TeeGroup.division] is left blank when
 *  its players aren't all the same division, so callers know to fall back to a per-player
 *  division tag instead of one group-level badge. Shared by every screen that needs one combined,
 *  tee-time-ordered view across all of a real event's divisions (Field Mode, the tee-time alert
 *  preview) rather than one division at a time. */
fun mergeGroupsAcrossDivisions(byDivision: Map<String, LiveRoster>): List<TeeGroup> =
    byDivision.values
        .flatMap { roster -> roster.groups.flatMap { group -> group.players.map { group.time to it } } }
        .groupBy({ it.first }, { it.second })
        .map { (time, players) ->
            TeeGroup(time = time, players = players, division = players.map { it.division }.distinct().singleOrNull() ?: "")
        }
        .sortedWith(
            compareBy(
                { TeeAlarmScheduler.parseTodayMillis(it.time) == null },
                { TeeAlarmScheduler.parseTodayMillis(it.time) ?: Long.MAX_VALUE },
            ),
        )

/** Seconds between real per-player profile requests during background prefetch — matches
 *  pdga.com's robots.txt `Crawl-delay: 10`, since that prefetch is automated, bulk fetching
 *  rather than a single human-initiated lookup. */
private const val PROFILE_PREFETCH_DELAY_MS = 10_000L

/**
 * Caches the real roster(s) loaded from PDGA Live's unauthenticated endpoints for whichever
 * tournament the TD is running, and enriches them with each player's rating, member-since date,
 * recent result and ADG Tour rank — all fetched once when the event loads, not on demand — so
 * Field Mode has everything it needs already cached before the TD ever goes offline in the field.
 * Empty until a real tournament is selected and this finishes its first load.
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
    /** The real course(s) for whichever event [loadAllDivisions] last ran for — empty until that
     *  succeeds. Usually a single course; distinct only when an event genuinely spans more than
     *  one physical course. */
    val eventCourses: StateFlow<List<PdgaCourseMeta>>
    val loading: StateFlow<Boolean>
    /** Human-readable progress for a multi-division load, e.g. "Loading FPO (2/4)…". Null when
     *  not loading or when a single-division [load] is in progress. */
    val loadingStatus: StateFlow<String?>
    /** Progress for the slow, throttled per-player profile fetch that runs after the roster
     *  itself is loaded, e.g. "Loading player profiles… (12/137)". Null when not running. */
    val profilePrefetchStatus: StateFlow<String?>
    /** When the roster itself last finished loading (not the slower profile prefetch) — the real
     *  answer to "how stale is this cached data", for screens that used to show a fixed fake
     *  timestamp. Null until the first successful load. */
    val lastLoadedAtMillis: StateFlow<Long?>
    /** Which tournament [rosters]/[eventDivisions] actually belong to — the identity check a
     *  caller should use to decide whether to (re)load, rather than just "is there some data
     *  present". Settings can briefly lag behind a rapid tournament switch; comparing against
     *  the tournament actually loaded (instead of merely "did loading happen") means a stale load
     *  self-corrects on the next check instead of leaving the wrong tournament's data on screen
     *  indefinitely. Null until the first successful load. */
    val loadedTournamentId: StateFlow<String?>
    val error: StateFlow<String?>

    /** Loads one division/round on demand — used for a manual reload, or a round the TD picks
     *  themselves that differs from whatever [loadAllDivisions] auto-loaded. */
    fun load(tournamentId: String, division: String, round: Int)

    /** Discovers every real division in the event and loads each one's current round in one
     *  go — the normal path, run right after a TD picks a real tournament. Once the roster
     *  itself is in, this also enriches every player with their real ADG Tour rank (one bulk
     *  request) and then starts a throttled background fetch of each player's real member-since
     *  date and most recent result, in tee-time order so the soonest groups are ready first. */
    fun loadAllDivisions(tournamentId: String)

    /** Drops everything cached and cancels any in-flight prefetch — call when the TD switches to
     *  a different tournament (or back to the demo one), so a previous event's real data (or a
     *  still-running background fetch for it) can't leak into the new selection. */
    fun clear()
}

class RealLiveRosterRepository(
    private val pdgaRepository: PdgaRepository,
    private val adgRepository: AdgRepository,
    private val profileCacheRepository: PlayerProfileCacheRepository,
    private val settingsRepository: SettingsRepository,
    private val bioNotesRepository: BioNotesRepository,
    private val appContext: Context,
    private val profileScraper: PdgaProfileScraper = PdgaProfileScraper(),
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) : LiveRosterRepository {
    private val _rosters = MutableStateFlow<Map<String, LiveRoster>>(emptyMap())
    override val rosters: StateFlow<Map<String, LiveRoster>> = _rosters.asStateFlow()
    private val _eventDivisions = MutableStateFlow<List<PdgaDivisionMeta>>(emptyList())
    override val eventDivisions: StateFlow<List<PdgaDivisionMeta>> = _eventDivisions.asStateFlow()
    private val _eventCourses = MutableStateFlow<List<PdgaCourseMeta>>(emptyList())
    override val eventCourses: StateFlow<List<PdgaCourseMeta>> = _eventCourses.asStateFlow()
    private val _loading = MutableStateFlow(false)
    override val loading: StateFlow<Boolean> = _loading.asStateFlow()
    private val _loadingStatus = MutableStateFlow<String?>(null)
    override val loadingStatus: StateFlow<String?> = _loadingStatus.asStateFlow()
    private val _profilePrefetchStatus = MutableStateFlow<String?>(null)
    override val profilePrefetchStatus: StateFlow<String?> = _profilePrefetchStatus.asStateFlow()
    private val _lastLoadedAtMillis = MutableStateFlow<Long?>(null)
    override val lastLoadedAtMillis: StateFlow<Long?> = _lastLoadedAtMillis.asStateFlow()
    private val _loadedTournamentId = MutableStateFlow<String?>(null)
    override val loadedTournamentId: StateFlow<String?> = _loadedTournamentId.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    override val error: StateFlow<String?> = _error.asStateFlow()

    // Tracks whichever full-roster fetch is currently running (load() or loadAllDivisions()) so
    // starting a new one always cancels the old — without this, switching tournaments while a
    // slow previous load is still in flight let the stale one "win" the race and silently
    // overwrite the new tournament's roster with the old one's, well after the UI had already
    // moved on and looked correct. Confirmed live: the header correctly showed the newly-picked
    // event, but the roster underneath was still the previous event's.
    private var loadJob: Job? = null
    private var adgJob: Job? = null
    private var prefetchJob: Job? = null

    override fun load(tournamentId: String, division: String, round: Int) {
        loadJob?.cancel()
        loadJob = scope.launch {
            _loading.value = true
            _error.value = null
            fetchOneDivision(tournamentId, division, round)
                .onSuccess { roster ->
                    _rosters.update { it + (division to roster) }
                    _lastLoadedAtMillis.value = System.currentTimeMillis()
                    _loadedTournamentId.value = tournamentId
                    if (roster.groups.isEmpty()) _error.value = "No tee times published yet for this round"
                }
                .onFailure { e -> _error.value = e.message ?: "Live lookup failed" }
            _loading.value = false
        }
    }

    /** Loads (or reloads) every real division for [tournamentId] — a brand-new tournament pick, a
     *  manual "Sync Now"/"Retry", or Field Mode's own refresh all funnel through here, so this is
     *  the one place that needs to honor [AppSettings.clearBioDataOnNewEvent]: wiping TD-entered
     *  bio notes *and* this tournament's cached PDGA profiles whenever this event's real data
     *  (re)loads, not just the first time — otherwise every already-cached player would still be
     *  skipped as a cache hit on the very reload meant to refresh them. */
    override fun loadAllDivisions(tournamentId: String) {
        loadJob?.cancel()
        adgJob?.cancel()
        prefetchJob?.cancel()
        loadJob = scope.launch {
            _loading.value = true
            _error.value = null
            if (settingsRepository.settings.first().clearBioDataOnNewEvent) {
                bioNotesRepository.clearAll()
                // Cached PDGA profiles (member-since, recent results) are the other half of a
                // player's "profile" — clearing bio notes but leaving these behind would still
                // skip every already-cached player as a silent cache hit on this same reload.
                profileCacheRepository.clear(tournamentId)
            }
            _loadingStatus.value = "Finding real divisions…"
            pdgaRepository.fetchEventMeta(tournamentId)
                .onSuccess { meta ->
                    _eventDivisions.value = meta.divisions
                    _eventCourses.value = meta.courses
                    val loaded = mutableMapOf<String, LiveRoster>()
                    meta.divisions.forEachIndexed { index, division ->
                        _loadingStatus.value = "Loading ${division.code} (${index + 1}/${meta.divisions.size})…"
                        fetchOneDivision(tournamentId, division.code, meta.latestRound)
                            .onSuccess { roster -> loaded[division.code] = roster }
                            .onFailure { e -> _error.value = "${division.code}: ${e.message ?: "lookup failed"}" }
                    }
                    _rosters.value = loaded
                    _lastLoadedAtMillis.value = System.currentTimeMillis()
                    _loadedTournamentId.value = tournamentId
                    _loadingStatus.value = null
                    _loading.value = false
                    enrichWithAdg()
                    if (settingsRepository.settings.first().fetchPlayerProfiles) {
                        startProfilePrefetch(tournamentId, loaded)
                    }
                }
                .onFailure { e ->
                    _error.value = e.message ?: "Couldn't load this event's divisions"
                    _loadingStatus.value = null
                    _loading.value = false
                }
        }
    }

    override fun clear() {
        loadJob?.cancel()
        loadJob = null
        adgJob?.cancel()
        adgJob = null
        prefetchJob?.cancel()
        prefetchJob = null
        _rosters.value = emptyMap()
        _eventDivisions.value = emptyList()
        _eventCourses.value = emptyList()
        _error.value = null
        _profilePrefetchStatus.value = null
        _lastLoadedAtMillis.value = null
        _loadedTournamentId.value = null
    }

    private suspend fun fetchOneDivision(tournamentId: String, division: String, round: Int): Result<LiveRoster> =
        pdgaRepository.fetchLiveResults(tournamentId, division, round).map { divisionResult ->
            val results = divisionResult.results
            val positions = standingsFor(results)
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
                        division = division,
                        players = players.mapIndexed { index, r ->
                            // PDGA Live's own API returns the literal string "null" for a missing
                            // city/country rather than omitting the field, so a plain null-filter
                            // isn't enough — confirmed live: a player with neither on file otherwise
                            // shows up as "From null, null".
                            val homeLocation = listOfNotNull(r.city, r.country)
                                .filter { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                                .joinToString(", ")
                                .ifEmpty { null }
                            // A player with no PDGA number (amateur/junior in a mixed local event)
                            // still needs a unique id — falling back to their PDGA number would
                            // collide every such player in the tournament onto "pdga-0" or "".
                            val id = r.pdgaNumber?.let { playerIdForPdgaNumber(it.toString()) }
                                ?: "local-$division-$teeTime-$index"
                            // A TD's saved note carries forward across every reload (see BioNote's
                            // kdoc) — without this overlay, a fresh roster load/refresh would show
                            // the auto-generated blurb below instead, making the note look cleared.
                            val note = bioNotesRepository.getNote(id)
                            val customBio = note?.bio?.takeIf { it.isNotBlank() }
                            Player(
                                id = id,
                                name = "${r.firstName} ${r.lastName}".trim(),
                                pdga = PdgaProfile(pdgaNumber = r.pdgaNumber?.toString().orEmpty(), rating = r.rating, memberSince = "—", homeLocation = homeLocation),
                                recentResult = "—",
                                bio = customBio ?: describePlayer(homeLocation = homeLocation)
                                    ?: "Real PDGA Live starter — full profile loading in the background.",
                                hasCustomNotes = customBio != null,
                                sponsor = note?.sponsor.orEmpty(),
                                walkOnSong = note?.walkOnSong.orEmpty(),
                                pronunciation = note?.pronunciation.orEmpty(),
                                overall = r.toPar?.let { tp -> TournamentStanding(scoreToPar = tp, position = positions[r] ?: "—") },
                                division = division,
                            )
                        },
                    )
                }
            LiveRoster(tournamentId, division, round, groups, courses = divisionResult.courses)
        }

    /** Real standings computed from the same live results already fetched — no extra call. Ties
     *  share a rank with a "T" prefix and the next distinct score skips ahead, same as a real
     *  disc golf leaderboard (two players tied for 2nd are both "T2nd", the next is "4th"). */
    // Keyed by result identity, not PDGA number — several real starters can share no PDGA number
    // (or, previously, the API's 0 default), which would otherwise collide their positions together.
    private fun standingsFor(results: List<PdgaLiveResult>): Map<PdgaLiveResult, String> {
        val scored = results.filter { it.toPar != null }.sortedBy { it.toPar }
        val positions = java.util.IdentityHashMap<PdgaLiveResult, String>()
        var rank = 0
        var previousScore: Int? = null
        scored.forEachIndexed { index, r ->
            if (r.toPar != previousScore) {
                rank = index + 1
                previousScore = r.toPar
            }
            val tied = scored.count { it.toPar == r.toPar } > 1
            positions[r] = (if (tied) "T" else "") + rank.asOrdinal()
        }
        return positions
    }

    /** One request for the whole ADG Tour leaderboard, matched to real starters by name — cheap
     *  enough to just do in bulk, unlike the PDGA per-player profile fetch below. A starter whose
     *  real name doesn't match anyone on the leaderboard (nickname, married name, etc.) falls back
     *  to the real ADG member list, matched by PDGA number instead, to find their real ADG name —
     *  that list is much bigger than the leaderboard, so it's only fetched when a fallback is
     *  actually needed, not on every sync. */
    private fun enrichWithAdg() {
        adgJob = scope.launch {
            val rows = adgRepository.fetchLeaderboard().getOrNull()
            if (rows.isNullOrEmpty()) return@launch

            // A player can be ranked in more than one ADG division (e.g. Open and Masters) — every
            // row under their name is a real ranking, not just the first one found.
            fun nameMatches(name: String) = rows.filter { it.name.equals(name, ignoreCase = true) }

            val allPlayers = _rosters.value.values.flatMap { roster -> roster.groups.flatMap { it.players } }
            val needsMemberLookup = allPlayers.any { nameMatches(it.name).isEmpty() }
            val members = if (needsMemberLookup) adgRepository.fetchMemberList().getOrNull() else null

            fun matchesFor(p: Player): List<AdgLeaderboardRow> {
                nameMatches(p.name).takeIf { it.isNotEmpty() }?.let { return it }
                val member = members?.firstOrNull { it.pdgaNumber == p.pdga.pdgaNumber } ?: return emptyList()
                return nameMatches("${member.firstName} ${member.lastName}".trim())
            }

            _rosters.update { current ->
                current.mapValues { (_, roster) ->
                    roster.copy(
                        groups = roster.groups.map { group ->
                            group.copy(
                                players = group.players.map { p ->
                                    val matches = matchesFor(p)
                                    if (matches.isEmpty()) {
                                        p
                                    } else {
                                        // The division they're actually entered in this tournament
                                        // leads, even if ADG lists other divisions first.
                                        val rankings = matches
                                            .map { AdgRanking(it.rank, it.division, it.points) }
                                            .sortedByDescending { it.division.equals(p.division, ignoreCase = true) }
                                        p.copy(adg = rankings)
                                    }
                                },
                            )
                        },
                    )
                }
            }
        }
    }

    /** Applies any profiles already cached on disk for this tournament instantly, then fetches
     *  the rest from each real starter's public PDGA profile page — one HTTP request per player,
     *  so those are paced at [PROFILE_PREFETCH_DELAY_MS] apart (pdga.com's own stated Crawl-delay)
     *  rather than fired all at once. Works through players in tee-time order across every
     *  division, soonest groups first, so a group's data is ready well before it's announced.
     *  Reloading the same tournament later (e.g. after an app restart) is then a pure cache hit —
     *  nothing gets re-fetched unless the tournament itself changes. */
    private fun startProfilePrefetch(tournamentId: String, loaded: Map<String, LiveRoster>) {
        prefetchJob = scope.launch {
            data class Target(val teeTime: String, val division: String, val player: Player)

            // Real chronological order, not string order — a plain string compare sorts "10:00 AM"
            // before "9:00 AM", which silently starved whichever division's tee times happened to
            // sort late: this queue is long and throttled, so a division stuck at the tail could
            // go the entire load without its players' details ever being fetched.
            val allTargets = loaded.entries
                .flatMap { (division, roster) -> roster.groups.flatMap { g -> g.players.map { Target(g.time, division, it) } } }
                // A player with no PDGA number (amateur/junior in a mixed local event) has no
                // pdga.com profile to fetch — leaving them in would collide them all onto one
                // cache entry (see PlayerProfileCacheRepository key) after the first such fetch.
                .filter { it.player.pdga.hasPdgaNumber }
                .sortedWith(
                    compareBy(
                        { TeeAlarmScheduler.parseTodayMillis(it.teeTime) == null },
                        { TeeAlarmScheduler.parseTodayMillis(it.teeTime) ?: Long.MAX_VALUE },
                    ),
                )
            if (allTargets.isEmpty()) return@launch

            val cached = profileCacheRepository.get(tournamentId)
            val toFetch = allTargets.filter { target ->
                val hit = cached[target.player.pdga.pdgaNumber]
                if (hit != null) mergeProfile(target.division, target.player.id, hit)
                hit == null
            }
            if (toFetch.isEmpty()) return@launch

            // Doze throttles a background app's CPU/network once the screen is off, which is
            // exactly when a TD is most likely to have this running — a partial wake lock keeps
            // this specific loop's requests and delays on schedule. Bounded by an explicit
            // timeout (Android best practice: never risk an indefinitely-held lock if release()
            // is somehow skipped) sized generously for the real request pace plus network time.
            val powerManager = appContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
            val wakeLock = powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TDAid:ProfilePrefetch")
            val timeoutMillis = toFetch.size * (PROFILE_PREFETCH_DELAY_MS + 20_000L) + 30_000L
            try {
                wakeLock?.acquire(timeoutMillis)
                toFetch.forEachIndexed { index, target ->
                    _profilePrefetchStatus.value = "Loading player profiles… (${index + 1}/${toFetch.size})"
                    runCatching { profileScraper.fetchProfile(target.player.pdga.pdgaNumber, target.division) }
                        .onSuccess { profile ->
                            mergeProfile(target.division, target.player.id, profile)
                            profileCacheRepository.save(tournamentId, target.player.pdga.pdgaNumber, profile)
                        }
                    if (index < toFetch.size - 1) delay(PROFILE_PREFETCH_DELAY_MS)
                }
            } finally {
                if (wakeLock?.isHeld == true) wakeLock.release()
            }
            _profilePrefetchStatus.value = null
        }
    }

    private suspend fun mergeProfile(division: String, playerId: String, profile: PdgaPlayerProfile) {
        // A TD's saved note always wins over the auto-generated blurb below — otherwise this
        // prefetch (which runs after every load) would overwrite it right back out from under them.
        val note = bioNotesRepository.getNote(playerId)
        val customBio = note?.bio?.takeIf { it.isNotBlank() }
        _rosters.update { current ->
            val roster = current[division] ?: return@update current
            val updatedGroups = roster.groups.map { group ->
                group.copy(
                    players = group.players.map { p ->
                        if (p.id != playerId) {
                            p
                        } else {
                            p.copy(
                                pdga = p.pdga.copy(memberSince = profile.memberSince ?: p.pdga.memberSince),
                                recentResult = profile.recentResult ?: p.recentResult,
                                bio = customBio ?: describePlayer(profile, p.pdga.homeLocation) ?: p.bio,
                                hasCustomNotes = customBio != null,
                                sponsor = note?.sponsor.orEmpty(),
                                walkOnSong = note?.walkOnSong.orEmpty(),
                                pronunciation = note?.pronunciation.orEmpty(),
                            )
                        }
                    },
                )
            }
            current + (division to roster.copy(groups = updatedGroups))
        }
    }

    private fun describePlayer(profile: PdgaPlayerProfile? = null, homeLocation: String? = null): String? {
        val facts = mutableListOf<String>()
        homeLocation?.let { facts.add("From $it") }
        profile?.memberSince?.let { facts.add("PDGA member since $it") }
        profile?.recentResult?.let { facts.add("last event: $it") }
        // Skip a fact that's already covered by one already listed — most players' best or most
        // recent win this year IS their most recent event, and repeating it reads oddly.
        if (profile?.lastWin != null && profile.lastWin != profile.recentResult) {
            facts.add("last win: ${profile.lastWin}")
        }
        if (profile?.bestResultThisYear != null &&
            profile.bestResultThisYear != profile.recentResult &&
            profile.bestResultThisYear != profile.lastWin
        ) {
            facts.add("best result this year: ${profile.bestResultThisYear}")
        }
        return facts.takeIf { it.isNotEmpty() }?.joinToString(" · ")?.plus(".")
    }
}
