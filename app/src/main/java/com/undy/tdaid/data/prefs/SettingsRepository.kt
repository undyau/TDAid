package com.undy.tdaid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "td_settings")

data class AppSettings(
    val announceIntervalMin: Int = 3,
    val pdgaConnected: Boolean = true,
    val syncRatings: Boolean = true,
    val syncResults: Boolean = true,
    val syncMembership: Boolean = true,
    val syncBios: Boolean = true,
    val syncFrequencyMin: Int = 15,
    val adgConnected: Boolean = true,
    val adgShowRank: Boolean = true,
    val lastSyncedAtMillis: Long = System.currentTimeMillis() - 2 * 60 * 1000L,
)

private object Keys {
    val ANNOUNCE_INTERVAL = intPreferencesKey("announce_interval_min")
    val PDGA_CONNECTED = booleanPreferencesKey("pdga_connected")
    val SYNC_RATINGS = booleanPreferencesKey("sync_ratings")
    val SYNC_RESULTS = booleanPreferencesKey("sync_results")
    val SYNC_MEMBERSHIP = booleanPreferencesKey("sync_membership")
    val SYNC_BIOS = booleanPreferencesKey("sync_bios")
    val SYNC_FREQUENCY = intPreferencesKey("sync_frequency_min")
    val ADG_CONNECTED = booleanPreferencesKey("adg_connected")
    val ADG_SHOW_RANK = booleanPreferencesKey("adg_show_rank")
    val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setAnnounceInterval(minutes: Int)
    suspend fun setPdgaConnected(connected: Boolean)
    suspend fun setSyncToggle(ratings: Boolean? = null, results: Boolean? = null, membership: Boolean? = null, bios: Boolean? = null)
    suspend fun setSyncFrequency(minutes: Int)
    suspend fun setAdgConnected(connected: Boolean)
    suspend fun setAdgShowRank(show: Boolean)
    suspend fun markSyncedNow()
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            announceIntervalMin = prefs[Keys.ANNOUNCE_INTERVAL] ?: 3,
            pdgaConnected = prefs[Keys.PDGA_CONNECTED] ?: true,
            syncRatings = prefs[Keys.SYNC_RATINGS] ?: true,
            syncResults = prefs[Keys.SYNC_RESULTS] ?: true,
            syncMembership = prefs[Keys.SYNC_MEMBERSHIP] ?: true,
            syncBios = prefs[Keys.SYNC_BIOS] ?: true,
            syncFrequencyMin = prefs[Keys.SYNC_FREQUENCY] ?: 15,
            adgConnected = prefs[Keys.ADG_CONNECTED] ?: true,
            adgShowRank = prefs[Keys.ADG_SHOW_RANK] ?: true,
            lastSyncedAtMillis = prefs[Keys.LAST_SYNCED_AT] ?: (System.currentTimeMillis() - 2 * 60 * 1000L),
        )
    }

    override suspend fun setAnnounceInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.ANNOUNCE_INTERVAL] = minutes.coerceIn(1, 10) }
    }

    override suspend fun setPdgaConnected(connected: Boolean) {
        context.dataStore.edit { it[Keys.PDGA_CONNECTED] = connected }
    }

    override suspend fun setSyncToggle(ratings: Boolean?, results: Boolean?, membership: Boolean?, bios: Boolean?) {
        context.dataStore.edit { prefs ->
            ratings?.let { prefs[Keys.SYNC_RATINGS] = it }
            results?.let { prefs[Keys.SYNC_RESULTS] = it }
            membership?.let { prefs[Keys.SYNC_MEMBERSHIP] = it }
            bios?.let { prefs[Keys.SYNC_BIOS] = it }
        }
    }

    override suspend fun setSyncFrequency(minutes: Int) {
        context.dataStore.edit { it[Keys.SYNC_FREQUENCY] = minutes.coerceIn(5, 60) }
    }

    override suspend fun setAdgConnected(connected: Boolean) {
        context.dataStore.edit { it[Keys.ADG_CONNECTED] = connected }
    }

    override suspend fun setAdgShowRank(show: Boolean) {
        context.dataStore.edit { it[Keys.ADG_SHOW_RANK] = show }
    }

    override suspend fun markSyncedNow() {
        context.dataStore.edit { it[Keys.LAST_SYNCED_AT] = System.currentTimeMillis() }
    }
}
