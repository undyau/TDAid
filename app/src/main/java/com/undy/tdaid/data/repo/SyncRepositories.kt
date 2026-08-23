package com.undy.tdaid.data.repo

import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.remote.AdgLeaderboardRow
import com.undy.tdaid.data.remote.AdgScraper
import com.undy.tdaid.data.remote.PdgaApiClient
import com.undy.tdaid.data.remote.PdgaEventMeta
import com.undy.tdaid.data.remote.PdgaEventResult
import com.undy.tdaid.data.remote.PdgaLiveResult
import com.undy.tdaid.data.remote.PdgaPlayerResult
import com.undy.tdaid.data.remote.PdgaSession
import kotlinx.coroutines.flow.first

/**
 * Connects to the real, official PDGA REST API (api.pdga.com). Unlike an app-level API key,
 * this is member-gated: every call needs a session obtained by logging in with an actual PDGA
 * membership username/password, exactly as a person would on pdga.com.
 */
interface PdgaRepository {
    val isLoggedIn: Boolean
    val loggedInAs: String?
    suspend fun login(username: String, password: String): Result<Unit>
    suspend fun logout()
    suspend fun lookupPlayer(pdgaNumber: String): Result<PdgaPlayerResult?>
    suspend fun searchEvents(query: String): Result<List<PdgaEventResult>>
    /** Real per-round tee times/pairings — unlike everything else here, this endpoint needs
     *  no PDGA login at all. */
    suspend fun fetchLiveResults(tournamentId: String, division: String, round: Int): Result<List<PdgaLiveResult>>
    /** Real division list + current round for a tournament — also needs no login. */
    suspend fun fetchEventMeta(tournamentId: String): Result<PdgaEventMeta>
    suspend fun syncNow(): Long
}

class RealPdgaRepository(
    private val client: PdgaApiClient = PdgaApiClient(),
    private val settingsRepository: SettingsRepository,
) : PdgaRepository {
    private var session: PdgaSession? = null

    override val isLoggedIn: Boolean get() = session != null
    override val loggedInAs: String? get() = session?.username

    /** Restores a session saved from a previous run, called once at app startup before any
     *  screen reads [isLoggedIn] — so a TD isn't forced to log back in to PDGA on every launch. */
    suspend fun restoreSession() {
        val settings = settingsRepository.settings.first()
        val cookieName = settings.pdgaSessionCookieName
        val cookieValue = settings.pdgaSessionCookieValue
        val username = settings.pdgaUsername
        if (cookieName != null && cookieValue != null && username != null) {
            session = PdgaSession(cookieName, cookieValue, username)
        }
    }

    override suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        val newSession = client.login(username, password)
        session = newSession
        settingsRepository.savePdgaSession(newSession.cookieName, newSession.cookieValue, newSession.username)
    }

    override suspend fun logout() {
        session = null
        settingsRepository.clearPdgaSession()
    }

    override suspend fun lookupPlayer(pdgaNumber: String): Result<PdgaPlayerResult?> = runCatching {
        val activeSession = session ?: error("Not logged in to PDGA")
        client.searchPlayer(activeSession, pdgaNumber)
    }

    override suspend fun searchEvents(query: String): Result<List<PdgaEventResult>> = runCatching {
        val activeSession = session ?: error("Not logged in to PDGA")
        client.searchEvents(activeSession, query)
    }

    override suspend fun fetchLiveResults(tournamentId: String, division: String, round: Int): Result<List<PdgaLiveResult>> =
        runCatching { client.fetchLiveResults(tournamentId, division, round) }

    override suspend fun fetchEventMeta(tournamentId: String): Result<PdgaEventMeta> =
        runCatching { client.fetchEventMeta(tournamentId) }

    override suspend fun syncNow(): Long = System.currentTimeMillis()
}

/**
 * Connects to the Australian Disc Golf (ADG) Tour Leaderboard — scraped from the real public
 * page via [AdgScraper] (no login required, unlike PDGA), layered on top of PDGA data as a
 * supplementary, optional source.
 */
interface AdgRepository {
    suspend fun lookupPlayerByName(name: String): Result<AdgLeaderboardRow?>
    /** The whole leaderboard in one request — used to enrich a real roster in bulk instead of
     *  looking up every player one at a time. */
    suspend fun fetchLeaderboard(): Result<List<AdgLeaderboardRow>>
    suspend fun syncNow(): Long
}

class RealAdgRepository(private val scraper: AdgScraper = AdgScraper()) : AdgRepository {
    override suspend fun lookupPlayerByName(name: String): Result<AdgLeaderboardRow?> = runCatching {
        val rows = scraper.fetchLeaderboard()
        rows.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun fetchLeaderboard(): Result<List<AdgLeaderboardRow>> = runCatching { scraper.fetchLeaderboard() }

    override suspend fun syncNow(): Long = System.currentTimeMillis()
}
