package com.undy.tdaid.data.repo

import com.undy.tdaid.data.remote.AdgLeaderboardRow
import com.undy.tdaid.data.remote.AdgScraper
import com.undy.tdaid.data.remote.PdgaApiClient
import com.undy.tdaid.data.remote.PdgaEventResult
import com.undy.tdaid.data.remote.PdgaLiveResult
import com.undy.tdaid.data.remote.PdgaPlayerResult
import com.undy.tdaid.data.remote.PdgaSession

/**
 * Connects to the real, official PDGA REST API (api.pdga.com). Unlike an app-level API key,
 * this is member-gated: every call needs a session obtained by logging in with an actual PDGA
 * membership username/password, exactly as a person would on pdga.com.
 */
interface PdgaRepository {
    val isLoggedIn: Boolean
    val loggedInAs: String?
    suspend fun login(username: String, password: String): Result<Unit>
    fun logout()
    suspend fun lookupPlayer(pdgaNumber: String): Result<PdgaPlayerResult?>
    suspend fun searchEvents(query: String): Result<List<PdgaEventResult>>
    /** Real per-round tee times/pairings — unlike everything else here, this endpoint needs
     *  no PDGA login at all. */
    suspend fun fetchLiveResults(tournamentId: String, division: String, round: Int): Result<List<PdgaLiveResult>>
    suspend fun syncNow(): Long
}

class RealPdgaRepository(private val client: PdgaApiClient = PdgaApiClient()) : PdgaRepository {
    private var session: PdgaSession? = null

    override val isLoggedIn: Boolean get() = session != null
    override val loggedInAs: String? get() = session?.username

    override suspend fun login(username: String, password: String): Result<Unit> = runCatching {
        session = client.login(username, password)
    }

    override fun logout() {
        session = null
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

    override suspend fun syncNow(): Long = System.currentTimeMillis()
}

/**
 * Connects to the Australian Disc Golf (ADG) Tour Leaderboard — scraped from the real public
 * page via [AdgScraper] (no login required, unlike PDGA), layered on top of PDGA data as a
 * supplementary, optional source.
 */
interface AdgRepository {
    suspend fun lookupPlayerByName(name: String): Result<AdgLeaderboardRow?>
    suspend fun syncNow(): Long
}

class RealAdgRepository(private val scraper: AdgScraper = AdgScraper()) : AdgRepository {
    override suspend fun lookupPlayerByName(name: String): Result<AdgLeaderboardRow?> = runCatching {
        val rows = scraper.fetchLeaderboard()
        rows.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }

    override suspend fun syncNow(): Long = System.currentTimeMillis()
}
