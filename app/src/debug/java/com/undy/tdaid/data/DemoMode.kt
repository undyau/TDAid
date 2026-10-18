package com.undy.tdaid.data

import android.content.Context
import com.undy.tdaid.data.model.AdgRanking
import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.RoundResult
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.model.TournamentStanding
import com.undy.tdaid.data.model.asOrdinal
import com.undy.tdaid.data.prefs.SettingsRepository
import com.undy.tdaid.data.remote.PdgaCourseMeta
import com.undy.tdaid.data.remote.PdgaDivisionMeta
import com.undy.tdaid.data.repo.LiveRoster
import com.undy.tdaid.data.repo.LiveRosterRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/**
 * A fictional event for Play Store screenshots, so no real player's name ends up in the listing.
 * Debug builds only, and only once switched on:
 *
 *     adb shell run-as com.undy.tdaid touch files/demo_mode
 *
 * then force-stop and relaunch. Tee times are laid out relative to launch time, so Field Mode
 * opens on a group that's about to tee off. Delete the file to go back to real data.
 */
object DemoMode {
    private const val TOURNAMENT_ID = "demo-riverbend-open"

    fun liveRosterRepositoryOrNull(context: Context, settingsRepository: SettingsRepository): LiveRosterRepository? {
        if (!File(context.filesDir, "demo_mode").exists()) return null
        runBlocking {
            settingsRepository.setSelectedTournament(
                name = "Riverbend Disc Golf Open",
                dates = "Sat 18 – Sun 19 Oct",
                location = "Brisbane, QLD",
                tournamentId = TOURNAMENT_ID,
            )
            settingsRepository.setLastDataLoadedTournamentId(TOURNAMENT_ID)
        }
        return DemoLiveRosterRepository(TOURNAMENT_ID)
    }
}

private class DemoPlayer(
    val name: String,
    val pdgaNumber: Int,
    val rating: Int,
    val memberSince: String,
    val home: String,
    val recent: String,
    val round1: Int,
    val lastWin: String? = null,
    val adgRank: Int? = null,
    val pronunciation: String = "",
    val sponsor: String = "",
    val walkOnSong: String = "",
    val note: String? = null,
    val checkedIn: Boolean = false,
)

private class DemoCard(val division: String, val players: List<DemoPlayer>)

private val CARDS = listOf(
    DemoCard(
        "MPO",
        listOf(
            DemoPlayer("Liam Harcourt", 210417, 1012, "2015", "Brisbane, Australia", "1st · Gold Coast Classic", -9,
                lastWin = "Gold Coast Classic", adgRank = 2, sponsor = "Riverside Disc Co.", walkOnSong = "Thunderstruck – AC/DC",
                note = "Defending champion. Shot a course-record 52 here last year.", checkedIn = true),
            DemoPlayer("Ryan Takeda", 188234, 1006, "2013", "Sydney, Australia", "3rd · Harbour City Open", -8,
                adgRank = 4, checkedIn = true),
            DemoPlayer("Mitch Lindqvist", 231905, 998, "2017", "Toowoomba, Australia", "2nd · Darling Downs Shootout", -7,
                lastWin = "Granite Belt Challenge", adgRank = 7, checkedIn = true),
            DemoPlayer("Sam Oduya", 245660, 993, "2018", "Brisbane, Australia", "5th · Gold Coast Classic", -6,
                adgRank = 11, pronunciation = "oh-DOO-yah", walkOnSong = "Lose Yourself – Eminem"),
        ),
    ),
    DemoCard(
        "MPO",
        listOf(
            DemoPlayer("Josh Whitfield", 199872, 989, "2014", "Ipswich, Australia", "4th · Harbour City Open", -5, adgRank = 13),
            DemoPlayer("Ethan Calloway", 256118, 984, "2019", "Logan, Australia", "7th · Gold Coast Classic", -4, adgRank = 18),
            DemoPlayer("Ben Moretti", 238770, 977, "2018", "Sunshine Coast, Australia", "6th · Noosa Spring Fling", -3,
                pronunciation = "moh-RET-ee"),
            DemoPlayer("Tom Ashby", 271443, 971, "2020", "Brisbane, Australia", "9th · Gold Coast Classic", -2),
        ),
    ),
    DemoCard(
        "FPO",
        listOf(
            DemoPlayer("Chloe Vance", 203391, 948, "2015", "Brisbane, Australia", "1st · Harbour City Open", -6,
                lastWin = "Harbour City Open", adgRank = 1, sponsor = "Southern Cross Discs", walkOnSong = "Shake It Off – Taylor Swift",
                note = "Three-time Queensland state champion."),
            DemoPlayer("Priya Natarajan", 228804, 931, "2017", "Gold Coast, Australia", "2nd · Gold Coast Classic", -4,
                adgRank = 3, pronunciation = "nah-tah-RAH-jun"),
            DemoPlayer("Mia Rennick", 249017, 915, "2019", "Toowoomba, Australia", "3rd · Darling Downs Shootout", -1, adgRank = 6),
            DemoPlayer("Hannah Kowalski", 262580, 902, "2020", "Brisbane, Australia", "4th · Noosa Spring Fling", 1,
                pronunciation = "koh-WAHL-skee"),
        ),
    ),
    DemoCard(
        "MP40",
        listOf(
            DemoPlayer("Grant McAllister", 98765, 961, "2009", "Brisbane, Australia", "1st · Masters Cup", -4,
                lastWin = "Masters Cup", adgRank = 1),
            DemoPlayer("Craig Nakamura", 121337, 954, "2011", "Redcliffe, Australia", "2nd · Masters Cup", -3, adgRank = 2),
            DemoPlayer("Steve Pemberton", 143208, 940, "2012", "Ipswich, Australia", "4th · Darling Downs Shootout", 0),
            DemoPlayer("Paul Brennan", 167451, 932, "2013", "Logan, Australia", "5th · Masters Cup", 2),
        ),
    ),
    DemoCard(
        "MA1",
        listOf(
            DemoPlayer("Daniel Reyes", 281774, 935, "2021", "Brisbane, Australia", "1st · Noosa Spring Fling", -3, lastWin = "Noosa Spring Fling"),
            DemoPlayer("Kieran Doyle", 290215, 921, "2022", "Gold Coast, Australia", "2nd · Gold Coast Classic", -1),
            DemoPlayer("Aiden Park", 297602, 914, "2022", "Brisbane, Australia", "3rd · Harbour City Open", 0),
            DemoPlayer("Luke Fenwick", 302118, 903, "2023", "Sunshine Coast, Australia", "6th · Noosa Spring Fling", 3),
        ),
    ),
)

private const val PAR = 54

private fun buildRosters(tournamentId: String): Map<String, LiveRoster> {
    // First card tees off ~6 minutes after launch, then every 10 minutes.
    val start = Calendar.getInstance().apply {
        add(Calendar.MINUTE, 6)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    val format = SimpleDateFormat("h:mm a", Locale.US)
    val timed = CARDS.mapIndexed { index, card ->
        val time = (start.clone() as Calendar).apply { add(Calendar.MINUTE, index * 10) }
        format.format(time.time) to card
    }

    val course = PdgaCourseMeta(courseId = 1, name = "Riverbend Park – Gold Layout")
    return timed.groupBy({ it.second.division }) { it }.mapValues { (division, cards) ->
        val all = cards.flatMap { it.second.players }
        val sortedScores = all.map { it.round1 }.sorted()
        fun position(score: Int): String {
            val rank = sortedScores.indexOf(score) + 1
            return (if (sortedScores.count { it == score } > 1) "T" else "") + rank.asOrdinal()
        }
        LiveRoster(
            tournamentId = tournamentId,
            division = division,
            round = 2,
            courses = listOf(course),
            groups = cards.map { (time, card) ->
                TeeGroup(time = time, division = division, players = card.players.map { it.toPlayer(division, ::position) })
            },
        )
    }
}

private fun DemoPlayer.toPlayer(division: String, position: (Int) -> String) = Player(
    id = "pdga-$pdgaNumber",
    name = name,
    pdga = PdgaProfile(pdgaNumber = pdgaNumber.toString(), rating = rating, memberSince = memberSince, homeLocation = home),
    recentResult = recent,
    lastWin = lastWin,
    bio = note ?: listOf("From $home", "PDGA member since $memberSince", "last event: $recent").joinToString(" · ") + ".",
    hasCustomNotes = note != null,
    sponsor = sponsor,
    walkOnSong = walkOnSong,
    pronunciation = pronunciation,
    round1 = RoundResult(scoreToPar = round1, strokes = PAR + round1),
    overall = TournamentStanding(scoreToPar = round1, position = position(round1)),
    adg = listOfNotNull(adgRank?.let { AdgRanking(rank = it, division = division, points = 1200 - it * 37) }),
    division = division,
    checkedIn = checkedIn,
)

private class DemoLiveRosterRepository(private val tournamentId: String) : LiveRosterRepository {
    private val _rosters = MutableStateFlow(buildRosters(tournamentId))
    override val rosters: StateFlow<Map<String, LiveRoster>> = _rosters.asStateFlow()
    override val eventDivisions: StateFlow<List<PdgaDivisionMeta>> = MutableStateFlow(
        listOf("MPO" to "Mixed Pro Open", "FPO" to "Female Pro Open", "MP40" to "Mixed Pro 40+", "MA1" to "Mixed Amateur 1")
            .map { (code, name) ->
                PdgaDivisionMeta(code, name, _rosters.value[code]?.groups?.sumOf { it.players.size } ?: 0)
            },
    )
    override val eventCourses: StateFlow<List<PdgaCourseMeta>> =
        MutableStateFlow(listOf(PdgaCourseMeta(courseId = 1, name = "Riverbend Park – Gold Layout")))
    override val loading: StateFlow<Boolean> = MutableStateFlow(false)
    override val loadingStatus: StateFlow<String?> = MutableStateFlow(null)
    override val profilePrefetchStatus: StateFlow<String?> = MutableStateFlow(null)
    override val lastLoadedAtMillis: StateFlow<Long?> = MutableStateFlow(System.currentTimeMillis() - 2 * 60_000L)
    override val loadedTournamentId: StateFlow<String?> = MutableStateFlow(tournamentId)
    override val error: StateFlow<String?> = MutableStateFlow(null)

    override fun load(tournamentId: String, division: String, round: Int) = Unit
    override fun loadAllDivisions(tournamentId: String) = Unit
    override fun clear() = Unit

    override fun setCheckedIn(playerId: String, checkedIn: Boolean) {
        _rosters.update { byDivision ->
            byDivision.mapValues { (_, roster) ->
                roster.copy(groups = roster.groups.map { group ->
                    group.copy(players = group.players.map { if (it.id == playerId) it.copy(checkedIn = checkedIn) else it })
                })
            }
        }
    }
}
