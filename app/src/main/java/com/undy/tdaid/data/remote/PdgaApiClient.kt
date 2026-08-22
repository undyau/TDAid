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
private val JSON_MEDIA_TYPE = "application/json".toMediaType()

data class PdgaSession(
    val cookieName: String,
    val cookieValue: String,
    val username: String,
)

data class PdgaPlayerResult(
    val firstName: String,
    val lastName: String,
    val pdgaNumber: String,
    val membershipStatus: String,
    val rating: Int?,
    val classification: String?,
    val city: String?,
    val stateProv: String?,
    val country: String?,
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

    suspend fun searchPlayer(session: PdgaSession, pdgaNumber: String): PdgaPlayerResult? =
        withContext(Dispatchers.IO) {
            val url = "$BASE_URL/services/json/players?pdga_number=$pdgaNumber"
            val request = Request.Builder()
                .url(url)
                .header("Cookie", "${session.cookieName}=${session.cookieValue}")
                .build()

            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw PdgaApiException("Player search failed (HTTP ${response.code}): ${text.take(200)}")
                }
                val json = try {
                    JSONObject(text)
                } catch (e: Exception) {
                    throw PdgaApiException("Unexpected player search response: ${text.take(200)}")
                }
                val players = json.optJSONArray("players") ?: return@use null
                if (players.length() == 0) return@use null
                val p = players.getJSONObject(0)
                PdgaPlayerResult(
                    firstName = p.optString("first_name").trim(),
                    lastName = p.optString("last_name").trim(),
                    pdgaNumber = p.optString("pdga_number").trim(),
                    membershipStatus = p.optString("membership_status").trim(),
                    rating = p.optString("rating").trim().toIntOrNull(),
                    classification = p.optString("classification").trim().ifEmpty { null },
                    city = p.optString("city").trim().ifEmpty { null },
                    stateProv = p.optString("state_prov").trim().ifEmpty { null },
                    country = p.optString("country").trim().ifEmpty { null },
                )
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
}
