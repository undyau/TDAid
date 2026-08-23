package com.undy.tdaid.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
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
    val pdgaNumber: Int,
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
                val events = json.optJSONArray("events") ?: return@use emptyList()
                (0 until events.length()).map { i ->
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
        }

    /** Real, unauthenticated tee-time/pairings data for one division/round of a tournament —
     *  found via the browser Network tab (`live_results_fetch_round`), since it's not part of
     *  PDGA's documented REST API and isn't discoverable from the Live scoring page's raw HTML
     *  (that page loads its data client-side, from this same endpoint, after the page renders). */
    suspend fun fetchLiveResults(tournamentId: String, division: String, round: Int): List<PdgaLiveResult> =
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
                val scores = json.optJSONObject("data")?.optJSONArray("scores") ?: return@use emptyList()
                (0 until scores.length()).map { i ->
                    val s = scores.getJSONObject(i)
                    PdgaLiveResult(
                        teeTime = s.optString("TeeTime").trim(),
                        firstName = s.optString("FirstName").trim(),
                        lastName = s.optString("LastName").trim(),
                        pdgaNumber = s.optInt("PDGANum"),
                        rating = s.optInt("Rating").takeIf { s.has("Rating") && !s.isNull("Rating") },
                        city = s.optString("City").trim().ifEmpty { null },
                        country = s.optString("Country").trim().ifEmpty { null },
                        toPar = if (s.has("ToPar") && !s.isNull("ToPar")) s.optInt("ToPar") else null,
                    )
                }
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
            val layoutsJson = data.optJSONArray("Layouts")
            // Real events sometimes publish a layout with no course attached (e.g. a placeholder
            // "sudden death" entry) — those have no CourseID/CourseName and are skipped.
            val courses = (if (layoutsJson == null) emptyList() else (0 until layoutsJson.length()).mapNotNull { i ->
                val l = layoutsJson.getJSONObject(i)
                val courseId = if (l.has("CourseID") && !l.isNull("CourseID")) l.optInt("CourseID") else null
                val name = l.optString("CourseName").trim()
                if (courseId == null || name.isEmpty()) null else PdgaCourseMeta(courseId, name)
            }).distinctBy { it.courseId }
            PdgaEventMeta(divisions = divisions, latestRound = data.optInt("LatestRound", 1).coerceAtLeast(1), courses = courses)
        }
    }
}
