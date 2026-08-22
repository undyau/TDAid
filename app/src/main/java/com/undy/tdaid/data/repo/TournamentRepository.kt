package com.undy.tdaid.data.repo

import com.undy.tdaid.data.model.AdgRanking
import com.undy.tdaid.data.model.Division
import com.undy.tdaid.data.model.PdgaProfile
import com.undy.tdaid.data.model.Player
import com.undy.tdaid.data.model.RoundResult
import com.undy.tdaid.data.model.RowStatus
import com.undy.tdaid.data.model.ScheduleRow
import com.undy.tdaid.data.model.TeeGroup
import com.undy.tdaid.data.model.TournamentStanding

/**
 * Everything about the current round: who's playing, when, and how the tournament stands so far.
 * Backed by fake sample data for now — swap the implementation for one backed by a real PDGA
 * integration without touching any screen code.
 */
interface TournamentRepository {
    val tournamentName: String
    val roundLabel: String
    val roundDate: String

    fun divisions(): List<Division>

    /** The starting groups for the division being run, in tee-off order. Shared by Setup's
     *  roster view and Field Mode's live announce queue, so both always agree. */
    fun teeGroups(): List<TeeGroup>

    fun fullSchedule(): List<ScheduleRow>
}

class FakeTournamentRepository : TournamentRepository {

    override val tournamentName = "Ridge Valley Open"
    override val roundLabel = "Round 2"
    override val roundDate = "Saturday, Aug 22"

    private val jordanKessler = Player(
        id = "kessler",
        name = "Jordan Kessler",
        pdga = PdgaProfile(pdgaNumber = "89417", rating = 1018, memberSince = "2014"),
        recentResult = "1st · Maple Ridge Classic",
        bio = "Local favorite, three-time club champion. Smooth forehand and clutch putting under " +
            "pressure — the gallery loves him on the final few holes.",
        hasCustomNotes = true,
        round1 = RoundResult(scoreToPar = -4, strokes = 50),
        overall = TournamentStanding(scoreToPar = -4, position = "T2nd"),
        adg = AdgRanking(rank = 8, division = "MPO", points = 1845),
    )
    private val caseyWhitfield = Player(
        id = "whitfield",
        name = "Casey Whitfield",
        pdga = PdgaProfile(pdgaNumber = "112843", rating = 996, memberSince = "2017"),
        recentResult = "T-4 · Spring Kickoff Am Tour",
        bio = "Long-range putter with a patient, conservative approach off the tee.",
        round1 = RoundResult(scoreToPar = -1, strokes = 53),
        overall = TournamentStanding(scoreToPar = -1, position = "T7th"),
        adg = null,
    )
    private val marcusDoyle = Player(
        id = "doyle",
        name = "Marcus Doyle",
        pdga = PdgaProfile(pdgaNumber = "71205", rating = 1004, memberSince = "2011"),
        recentResult = "2nd · Highland Classic",
        bio = "Veteran competitor known for steady scrambling under pressure.",
        round1 = RoundResult(scoreToPar = -3, strokes = 51),
        overall = TournamentStanding(scoreToPar = -3, position = "T4th"),
        adg = AdgRanking(rank = 22, division = "MPO", points = 1210),
    )
    private val reeseAlden = Player(
        id = "alden",
        name = "Reese Alden",
        pdga = PdgaProfile(pdgaNumber = "134590", rating = 972, memberSince = "2019"),
        recentResult = "1st · Riverside Am Championship",
        bio = "Rising junior talent with an aggressive distance-driver approach.",
        round1 = RoundResult(scoreToPar = 0, strokes = 54),
        overall = TournamentStanding(scoreToPar = 0, position = "T11th"),
        adg = null,
    )
    private val tatumIbarra = Player(
        id = "ibarra",
        name = "Tatum Ibarra",
        pdga = PdgaProfile(pdgaNumber = "98221", rating = 988, memberSince = "2016"),
        recentResult = "3rd · Ridge Valley Open (2025)",
        bio = "Consistent card runner who rarely misses circle-1 putts.",
        round1 = RoundResult(scoreToPar = -2, strokes = 52),
        overall = TournamentStanding(scoreToPar = -2, position = "T6th"),
        adg = AdgRanking(rank = 35, division = "MPO", points = 980),
    )
    private val devonMarsh = Player(
        id = "marsh",
        name = "Devon Marsh",
        pdga = PdgaProfile(pdgaNumber = "145002", rating = 961, memberSince = "2020"),
        recentResult = "5th · Lakeside Am Series",
        bio = "Newer competitor with a big-arm backhand and an improving upshot game.",
        round1 = RoundResult(scoreToPar = 3, strokes = 57),
        overall = TournamentStanding(scoreToPar = 3, position = "8th"),
        adg = null,
    )
    private val kaiSullivan = Player(
        id = "sullivan",
        name = "Kai Sullivan",
        pdga = PdgaProfile(pdgaNumber = "67012", rating = 1009, memberSince = "2009"),
        recentResult = "1st · Autumn Gold Am Championship",
        bio = "Course-management specialist who rarely takes an unnecessary risk off the tee.",
        round1 = RoundResult(scoreToPar = -5, strokes = 49),
        overall = TournamentStanding(scoreToPar = -5, position = "1st"),
        adg = AdgRanking(rank = 3, division = "MPO", points = 2210),
    )
    private val averyLindqvist = Player(
        id = "lindqvist",
        name = "Avery Lindqvist",
        pdga = PdgaProfile(pdgaNumber = "103377", rating = 979, memberSince = "2018"),
        recentResult = "2nd · Riverside Am Championship",
        bio = "Aggressive putter from range and a strong closer in tight final rounds.",
        round1 = RoundResult(scoreToPar = 2, strokes = 56),
        overall = TournamentStanding(scoreToPar = 2, position = "7th"),
        adg = AdgRanking(rank = 47, division = "MPO", points = 705),
    )

    private val groups = listOf(
        TeeGroup("8:30 AM", listOf(jordanKessler, caseyWhitfield, marcusDoyle)),
        TeeGroup("8:41 AM", listOf(reeseAlden, tatumIbarra)),
        TeeGroup("8:52 AM", listOf(devonMarsh, kaiSullivan)),
        TeeGroup("9:03 AM", listOf(averyLindqvist)),
    )

    override fun divisions(): List<Division> = listOf(
        Division("MPO", "Mixed Pro Open", starterCount = 8, matchedCount = 8),
        Division("FPO", "Female Pro Open", starterCount = 9, matchedCount = 9),
        Division("MA40", "Mixed Am 40+", starterCount = 14, matchedCount = 12),
        Division("MA1", "Mixed Am 1", starterCount = 11, matchedCount = 11),
    )

    override fun teeGroups(): List<TeeGroup> = groups

    override fun fullSchedule(): List<ScheduleRow> = listOf(
        ScheduleRow("7:58 AM", "MPO", "Rowe / Finch / Bardot", RowStatus.DONE),
        ScheduleRow("8:15 AM", "FPO", "Nguyen / Park / Costa", RowStatus.DONE),
        ScheduleRow("8:30 AM", "MPO", "Kessler / Whitfield / Doyle", RowStatus.CURRENT),
        ScheduleRow("8:41 AM", "MPO", "Alden / Ibarra", RowStatus.UPCOMING),
        ScheduleRow("8:52 AM", "MPO", "Marsh / Sullivan", RowStatus.UPCOMING),
        ScheduleRow("9:03 AM", "MPO", "Lindqvist", RowStatus.UPCOMING),
        ScheduleRow("9:20 AM", "MA40", "Farrow / Quinn / Delgado", RowStatus.UPCOMING),
        ScheduleRow("9:35 AM", "MA1", "Boone / Vance", RowStatus.UPCOMING),
    )
}
