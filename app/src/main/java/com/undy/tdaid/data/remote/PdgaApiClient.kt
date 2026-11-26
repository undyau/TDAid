package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

private const val BASE_URL = "https://api.pdga.com"
private const val SITE_ROOT = "https://www.pdga.com"
private const val LIVE_BASE_URL = "$SITE_ROOT/apps/tournament/live-api"
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

data class PdgaSession(
    val cookieName: String,
    val cookieValue: String,
    val username: String,
)

data class PdgaEventResult(
    val tournamentId: String,
    val tournamentName: String,
    val city: String?,
    val stateProv: String?,
    val country: String?,
    val startDate: String,
    val endDate: String,
    val tier: String?,
)

/** One player's real, live result/tee-time record for a specific tournament round, as published
 *  by PDGA Live — this is what actually carries the exact tee time, unlike the documented REST
 *  API (which only exposes season-level stats, not per-round pairings). */
data class PdgaLiveResult(
    val teeTime: String,
    val firstName: String,
    val lastName: String,
    /** Null for a real starter PDGA Live doesn't have a member number for — an amateur or junior
     *  in a mixed local event, not necessarily a data error — rather than the API's own default
     *  of 0, which would otherwise collide every such player onto the same fake number. */
    val pdgaNumber: Int?,
    val rating: Int?,
    val city: String?,
    val country: String?,
    val toPar: Int?,
)

/** One real division running at a tournament, as published by PDGA Live. */
data class PdgaDivisionMeta(
    val code: String,
    val name: String,
    val playerCount: Int,
)

/** One real course a tournament is played on, as published by PDGA Live. An event can publish
 *  more than one layout (different tee pads per division, a sudden-death layout, etc.) but
 *  they're usually all the same physical course — [courseId] is how those collapse to one. */
data class PdgaCourseMeta(
    val courseId: Int,
    val name: String,
)

/** A tournament's real division list, current round, and course(s), as published by PDGA Live —
 *  lets the app discover what to load right after a TD picks a real event, instead of guessing. */
data class PdgaEventMeta(
    val divisions: List<PdgaDivisionMeta>,
    val latestRound: Int,
    val courses: List<PdgaCourseMeta>,
    /** The event's country as PDGA Live names it, e.g. "Australia" — null if not published. */
    val country: String? = null,
)

/** One division/round's real tee times plus the specific course(s) that division actually played —
 *  a multi-course event (e.g. separate Am/Pro sites) publishes a different `layouts` list per
 *  division/round fetch, which is the only reliable way to tie a course to a division; the
 *  event-level layout list has no division field to match against. */
data class PdgaLiveDivisionResult(
    val results: List<PdgaLiveResult>,
    val courses: List<PdgaCourseMeta>,
)

/** A player's real membership record from the official API's `players` service. */
data class PdgaApiPlayer(
    val pdgaNumber: String,
    val city: String?,
    val stateProv: String?,
    val country: String?,
    /** "P" (pro) or "A" (amateur). */
    val classification: String?,
)

/** One division's worth of a player's real season stats from the official API's
 *  `player-statistics` service — a player who played more than one division that year gets one
 *  of these per division. */
data class PdgaApiPlayerStats(
    val year: Int,
    val divisionCode: String,
    val tournaments: Int,
    val prize: Double,
)

/** Everything the official API fallback knows about one player — what gets cached per
 *  tournament, so the per-player stats requests aren't repeated on every reload. */
data class PdgaApiProfile(
    /** Their membership record's location, for a player PDGA Live has none for. */
    val memberLocation: String?,
    val stats: List<PdgaApiPlayerStats>,
)

class PdgaApiException(message: String) : IOException(message)

/**
 * Talks to the real, official PDGA REST API (https://www.pdga.com/dev) — a member-gated service,
 * not an app-level API key: every call needs a session obtained by logging in with an actual
 * PDGA membership username/password, exactly as a person would on pdga.com.
 */
class PdgaApiClient(private val client: OkHttpClient = OkHttpClient()) {

    suspend fun login(username: String, password: String): PdgaSession = withContext(Dispatchers.IO) {
        val body = JSONObject().apply {
            put("username", username)
            put("password", password)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val request = Request.Builder()
            .url("$BASE_URL/services/json/user/login")
            .post(body)
            .build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw PdgaApiException("Login failed (HTTP ${response.code}): ${text.take(200)}")
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw PdgaApiException("Unexpected login response: ${text.take(200)}")
            }
            val sessionName = json.optString("session_name").ifEmpty {
                throw PdgaApiException("Login response missing session_name — wrong username/password?")
            }
            val sessid = json.optString("sessid")
            PdgaSession(cookieName = sessionName, cookieValue = sessid, username = username)
        }
    }

    suspend fun searchEvents(session: PdgaSession, eventName: String): List<PdgaEventResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(eventName, "UTF-8")
            val url = "$BASE_URL/services/json/event?event_name=$encoded&limit=25"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", "${session.cookieName}=${session.cookieValue}")
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw PdgaApiException("Event search failed (HTTP ${response.code}): ${text.take(200)}")
                }
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw PdgaApiException("Unexpected event search response: ${text.take(200)}")
                }
                parseEvents(json)
            }
        }

    /** Every real event in [countryCode] (two-letter code) that starts or ends between [startDate]
     *  and [endDate] (both YYYY-MM-DD), paged through the API's 200-per-request cap — at most
     *  [maxEvents], so a country with a huge calendar can't turn this into hundreds of requests. */
    suspend fun fetchEventsInCountry(
        session: PdgaSession,
        countryCode: String,
        startDate: String,
        endDate: String,
        maxEvents: Int,
    ): List<PdgaEventResult> = withContext(Dispatchers.IO) {
        val events = mutableListOf<PdgaEventResult>()
        while (events.size < maxEvents) {
            val url = "$BASE_URL/services/json/event?country=$countryCode&start_date=$startDate&end_date=$endDate" +
                "&limit=200&offset=${events.size}"
            val page = parseEvents(getAuthed(session, url, "Event listing"))
            events += page
            if (page.size < 200) break
        }
        events.take(maxEvents)
    }

    /** Real, unauthenticated tee-time/pairings data for one division/round of a tournament —
     *  found via the browser Network tab (`live_results_fetch_round`), since it's not part of
     *  PDGA's documented REST API and isn't discoverable from the Live scoring page's raw HTML
     *  (that page loads its data client-side, from this same endpoint, after the page renders). */
    suspend fun fetchLiveResults(tournamentId: String, division: String, round: Int): PdgaLiveDivisionResult =
        withContext(Dispatchers.IO) {
            val url = "$LIVE_BASE_URL/live_results_fetch_round?TournID=$tournamentId&Division=$division&Round=$round"
            val request = Request.Builder().url(url).build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw PdgaApiException("Live results failed (HTTP ${response.code}): ${text.take(200)}")
                }
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw PdgaApiException("Unexpected live results response: ${text.take(200)}")
                }
                val data = json.optJSONObject("data")
                val courses = parseCourses(data?.optJSONArray("layouts"))
                val scores = data?.optJSONArray("scores")
                    ?: return@use PdgaLiveDivisionResult(results = emptyList(), courses = courses)
                val results = (0 until scores.length()).map { i ->
                    val s = scores.getJSONObject(i)
                    PdgaLiveResult(
                        teeTime = s.optString("TeeTime").trim(),
                        firstName = s.optString("FirstName").trim(),
                        lastName = s.optString("LastName").trim(),
                        pdgaNumber = s.optInt("PDGANum", 0).takeIf { it > 0 },
                        rating = s.optInt("Rating").takeIf { s.has("Rating") && !s.isNull("Rating") },
                        city = s.optString("City").trim().ifEmpty { null },
                        country = s.optString("Country").trim().ifEmpty { null },
                        toPar = if (s.has("ToPar") && !s.isNull("ToPar")) s.optInt("ToPar") else null,
                    )
                }
                PdgaLiveDivisionResult(results = results, courses = courses)
            }
        }

    /** Real, unauthenticated division list + current round for a tournament — found the same way
     *  as [fetchLiveResults] (`live_results_fetch_event`), so the app can discover what to load
     *  right after a TD picks a real event instead of guessing division codes or a round number. */
    suspend fun fetchEventMeta(tournamentId: String): PdgaEventMeta = withContext(Dispatchers.IO) {
        val url = "$LIVE_BASE_URL/live_results_fetch_event?TournID=$tournamentId"
        val request = Request.Builder().url(url).build()

        client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw PdgaApiException("Event lookup failed (HTTP ${response.code}): ${text.take(200)}")
            }
            val json = try {
                JSONObject(text)
            } catch (e: Exception) {
                throw PdgaApiException("Unexpected event response: ${text.take(200)}")
            }
            val data = json.optJSONObject("data") ?: throw PdgaApiException("Event lookup returned no data")
            val divisionsJson = data.optJSONArray("Divisions")
            val divisions = if (divisionsJson == null) emptyList() else (0 until divisionsJson.length()).map { i ->
                val d = divisionsJson.getJSONObject(i)
                PdgaDivisionMeta(
                    code = d.optString("Division").trim(),
                    name = d.optString("DivisionName").trim(),
                    playerCount = d.optInt("Players"),
                )
            }
            val courses = parseCourses(data.optJSONArray("Layouts"))
            PdgaEventMeta(
                divisions = divisions,
                latestRound = data.optInt("LatestRound", 1).coerceAtLeast(1),
                courses = courses,
                country = if (data.has("Country") && !data.isNull("Country")) data.optString("Country").trim().ifEmpty { null } else null,
            )
        }
    }

    /** Real membership records for up to 200 players per request (the API's own cap) — the
     *  fallback source for a player's bio when pdga.com's profile pages are blocked (see
     *  [PdgaProfileBlockedException]). Member-gated like [searchEvents], but on api.pdga.com,
     *  which isn't behind that site's bot check. */
    suspend fun fetchPlayers(session: PdgaSession, pdgaNumbers: List<String>): List<PdgaApiPlayer> =
        withContext(Dispatchers.IO) {
            pdgaNumbers.distinct().chunked(200).flatMap { chunk ->
                val numbers = URLEncoder.encode(chunk.joinToString(","), "UTF-8")
                parsePlayers(getAuthed(session, "$BASE_URL/services/json/players?pdga_number=$numbers&limit=200", "Player lookup"))
            }
        }

    /** One player's real season stats for [year], one row per division they played that year —
     *  empty if they weren't current or didn't play any events that year. */
    suspend fun fetchPlayerStatistics(session: PdgaSession, pdgaNumber: String, year: Int): List<PdgaApiPlayerStats> =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/services/json/player-statistics?pdga_number=${URLEncoder.encode(pdgaNumber, "UTF-8")}&year=$year"
            parsePlayerStatistics(getAuthed(session, url, "Player statistics"))
        }

    private fun getAuthed(session: PdgaSession, url: String, what: String): JSONObject {
        val request = Request.Builder()
            .url(url)
            .header("Cookie", "${session.cookieName}=${session.cookieValue}")
            .build()
        return client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw PdgaApiException("$what failed (HTTP ${response.code}): ${text.take(200)}")
            }
            try {
                JSONObject(text)
            } catch (e: Exception) {
                throw PdgaApiException("Unexpected ${what.lowercase()} response: ${text.take(200)}")
            }
        }
    }

    /** Shared by [fetchEventMeta] (event-wide, key `"Layouts"`) and [fetchLiveResults]
     *  (one division/round, key `"layouts"`) — same shape either way. Real events sometimes
     *  publish a layout with no course attached (e.g. a placeholder "sudden death" entry); those
     *  have no CourseID/CourseName and are skipped. */
    private fun parseCourses(layoutsJson: JSONArray?): List<PdgaCourseMeta> =
        (if (layoutsJson == null) emptyList() else (0 until layoutsJson.length()).mapNotNull { i ->
            val l = layoutsJson.getJSONObject(i)
            val courseId = if (l.has("CourseID") && !l.isNull("CourseID")) l.optInt("CourseID") else null
            // optString() stringifies a JSON null value to the literal text "null" instead of
            // treating it as absent — has to be checked explicitly, same as CourseID above.
            val name = if (l.has("CourseName") && !l.isNull("CourseName")) l.optString("CourseName").trim() else ""
            if (courseId == null || name.isEmpty()) null else PdgaCourseMeta(courseId, name)
        }).distinctBy { it.courseId }

    companion object {
        private fun parseEvents(json: JSONObject): List<PdgaEventResult> {
            val events = json.optJSONArray("events") ?: return emptyList()
            return (0 until events.length()).map { i ->
                val e = events.getJSONObject(i)
                PdgaEventResult(
                    tournamentId = e.optString("tournament_id").trim(),
                    tournamentName = e.optString("tournament_name").trim(),
                    city = e.optString("city").trim().ifEmpty { null },
                    stateProv = e.optString("state_prov").trim().ifEmpty { null },
                    country = e.optString("country").trim().ifEmpty { null },
                    startDate = e.optString("start_date").trim(),
                    endDate = e.optString("end_date").trim(),
                    tier = e.optString("tier").trim().ifEmpty { null },
                )
            }
        }

        /** Internal (rather than private) so it can be unit tested against a documented response
         *  without a network call. */
        internal fun parsePlayers(json: JSONObject): List<PdgaApiPlayer> {
            val players = json.optJSONArray("players") ?: return emptyList()
            return (0 until players.length()).mapNotNull { i ->
                val p = players.getJSONObject(i)
                val pdgaNumber = p.cleanString("pdga_number") ?: return@mapNotNull null
                PdgaApiPlayer(
                    pdgaNumber = pdgaNumber,
                    city = p.cleanString("city"),
                    stateProv = p.cleanString("state_prov"),
                    country = p.cleanString("country"),
                    classification = p.cleanString("classification"),
                )
            }
        }

        /** Internal (rather than private) so it can be unit tested against a documented response
         *  without a network call. */
        internal fun parsePlayerStatistics(json: JSONObject): List<PdgaApiPlayerStats> {
            val rows = json.optJSONArray("players") ?: return emptyList()
            return (0 until rows.length()).mapNotNull { i ->
                val r = rows.getJSONObject(i)
                PdgaApiPlayerStats(
                    year = r.cleanString("year")?.toIntOrNull() ?: return@mapNotNull null,
                    divisionCode = r.cleanString("division_code").orEmpty(),
                    tournaments = r.cleanString("tournaments")?.toIntOrNull() ?: 0,
                    prize = r.cleanString("prize")?.toDoubleOrNull() ?: 0.0,
                )
            }
        }

        // The API pads values with whitespace in its own docs' examples, and — like PDGA Live —
        // optString() turns a JSON null into the literal text "null".
        private fun JSONObject.cleanString(key: String): String? =
            if (!has(key) || isNull(key)) null else optString(key).trim().ifEmpty { null }
    }
}
