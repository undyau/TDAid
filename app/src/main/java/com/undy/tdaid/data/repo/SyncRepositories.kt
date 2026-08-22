package com.undy.tdaid.data.repo

import com.undy.tdaid.data.remote.AdgLeaderboardRow
import com.undy.tdaid.data.remote.AdgScraper
import com.undy.tdaid.data.remote.PdgaApiClient
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
