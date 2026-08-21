package com.undy.tdaid.data.repo

import kotlinx.coroutines.delay

/**
 * Connects to the PDGA data source. Fake implementation simulates a network round trip;
 * a real implementation would call PDGA's tournament/player services here without any
 * screen or ViewModel needing to change.
 */
interface PdgaRepository {
    suspend fun syncNow(): Long
}

class FakePdgaRepository : PdgaRepository {
    override suspend fun syncNow(): Long {
        delay(600)
        return System.currentTimeMillis()
    }
}

/**
 * Connects to the Australian Disc Golf (ADG) Tour Leaderboard — a supplementary, optional
 * source layered on top of PDGA data.
 */
interface AdgRepository {
    suspend fun syncNow(): Long
}

class FakeAdgRepository : AdgRepository {
    override suspend fun syncNow(): Long {
        delay(500)
        return System.currentTimeMillis()
    }
}
