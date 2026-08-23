package com.undy.tdaid.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "td_settings")

data class AppSettings(
    val announceIntervalMin: Int = 3,
    /** A previously saved PDGA login session — lets the TD skip re-entering credentials on every
     *  app restart. Null means logged out. If the saved cookie has since expired, the next real
     *  PDGA API call just fails and the login form reappears, same as any expired web session. */
    val pdgaSessionCookieName: String? = null,
    val pdgaSessionCookieValue: String? = null,
    val pdgaUsername: String? = null,
    /** Whether to run the slow, throttled per-player profile fetch (member-since, recent
     *  results, auto-bio) after a real roster loads — off skips straight to a bare roster
     *  (name, PDGA #, rating, tee time), useful on a slow connection or in a hurry. */
    val fetchPlayerProfiles: Boolean = true,
    val adgConnected: Boolean = true,
    val adgShowRank: Boolean = true,
    val lastSyncedAtMillis: Long = System.currentTimeMillis() - 2 * 60 * 1000L,
    /** A real event picked via PDGA Event Search, if any — null means the built-in demo
     *  tournament. Only the name/dates come from PDGA; the roster/schedule stays demo data,
     *  since PDGA doesn't expose start lists through this API. */
    val selectedTournamentName: String? = null,
    val selectedTournamentDates: String? = null,
    val selectedTournamentLocation: String? = null,
    /** The real PDGA tournament_id from Event Search, if a real event is selected — lets other
     *  real-data tools (like live tee-time lookup) default to it instead of re-typing an ID. */
    val selectedTournamentId: String? = null,
)

private object Keys {
    val ANNOUNCE_INTERVAL = intPreferencesKey("announce_interval_min")
    val PDGA_SESSION_COOKIE_NAME = stringPreferencesKey("pdga_session_cookie_name")
    val PDGA_SESSION_COOKIE_VALUE = stringPreferencesKey("pdga_session_cookie_value")
    val PDGA_USERNAME = stringPreferencesKey("pdga_username")
    val FETCH_PLAYER_PROFILES = booleanPreferencesKey("fetch_player_profiles")
    val ADG_CONNECTED = booleanPreferencesKey("adg_connected")
    val ADG_SHOW_RANK = booleanPreferencesKey("adg_show_rank")
    val LAST_SYNCED_AT = longPreferencesKey("last_synced_at")
    val SELECTED_TOURNAMENT_NAME = stringPreferencesKey("selected_tournament_name")
    val SELECTED_TOURNAMENT_DATES = stringPreferencesKey("selected_tournament_dates")
    val SELECTED_TOURNAMENT_LOCATION = stringPreferencesKey("selected_tournament_location")
    val SELECTED_TOURNAMENT_ID = stringPreferencesKey("selected_tournament_id")
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun setAnnounceInterval(minutes: Int)
    suspend fun savePdgaSession(cookieName: String, cookieValue: String, username: String)
    suspend fun clearPdgaSession()
    suspend fun setFetchPlayerProfiles(enabled: Boolean)
    suspend fun setAdgConnected(connected: Boolean)
    suspend fun setAdgShowRank(show: Boolean)
    suspend fun markSyncedNow()
    suspend fun setSelectedTournament(name: String, dates: String, location: String?, tournamentId: String?)
    suspend fun clearSelectedTournament()
}

class DataStoreSettingsRepository(private val context: Context) : SettingsRepository {

    override val settings: Flow<AppSettings> = context.dataStore.data.map { prefs ->
        AppSettings(
            announceIntervalMin = prefs[Keys.ANNOUNCE_INTERVAL] ?: 3,
            pdgaSessionCookieName = prefs[Keys.PDGA_SESSION_COOKIE_NAME],
            pdgaSessionCookieValue = prefs[Keys.PDGA_SESSION_COOKIE_VALUE],
            pdgaUsername = prefs[Keys.PDGA_USERNAME],
            fetchPlayerProfiles = prefs[Keys.FETCH_PLAYER_PROFILES] ?: true,
            adgConnected = prefs[Keys.ADG_CONNECTED] ?: true,
            adgShowRank = prefs[Keys.ADG_SHOW_RANK] ?: true,
            lastSyncedAtMillis = prefs[Keys.LAST_SYNCED_AT] ?: (System.currentTimeMillis() - 2 * 60 * 1000L),
            selectedTournamentName = prefs[Keys.SELECTED_TOURNAMENT_NAME],
            selectedTournamentDates = prefs[Keys.SELECTED_TOURNAMENT_DATES],
            selectedTournamentLocation = prefs[Keys.SELECTED_TOURNAMENT_LOCATION],
            selectedTournamentId = prefs[Keys.SELECTED_TOURNAMENT_ID],
        )
    }

    override suspend fun setAnnounceInterval(minutes: Int) {
        context.dataStore.edit { it[Keys.ANNOUNCE_INTERVAL] = minutes.coerceIn(1, 10) }
    }

    override suspend fun savePdgaSession(cookieName: String, cookieValue: String, username: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PDGA_SESSION_COOKIE_NAME] = cookieName
            prefs[Keys.PDGA_SESSION_COOKIE_VALUE] = cookieValue
            prefs[Keys.PDGA_USERNAME] = username
        }
    }

    override suspend fun clearPdgaSession() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.PDGA_SESSION_COOKIE_NAME)
            prefs.remove(Keys.PDGA_SESSION_COOKIE_VALUE)
            prefs.remove(Keys.PDGA_USERNAME)
        }
    }

    override suspend fun setFetchPlayerProfiles(enabled: Boolean) {
        context.dataStore.edit { it[Keys.FETCH_PLAYER_PROFILES] = enabled }
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

    override suspend fun setSelectedTournament(name: String, dates: String, location: String?, tournamentId: String?) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_TOURNAMENT_NAME] = name
            prefs[Keys.SELECTED_TOURNAMENT_DATES] = dates
            if (location != null) prefs[Keys.SELECTED_TOURNAMENT_LOCATION] = location else prefs.remove(Keys.SELECTED_TOURNAMENT_LOCATION)
            if (tournamentId != null) prefs[Keys.SELECTED_TOURNAMENT_ID] = tournamentId else prefs.remove(Keys.SELECTED_TOURNAMENT_ID)
        }
    }

    override suspend fun clearSelectedTournament() {
        context.dataStore.edit { prefs ->
            prefs.remove(Keys.SELECTED_TOURNAMENT_NAME)
            prefs.remove(Keys.SELECTED_TOURNAMENT_DATES)
            prefs.remove(Keys.SELECTED_TOURNAMENT_LOCATION)
            prefs.remove(Keys.SELECTED_TOURNAMENT_ID)
        }
    }
}
