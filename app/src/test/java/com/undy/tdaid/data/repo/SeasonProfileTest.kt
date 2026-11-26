package com.undy.tdaid.data.repo

import com.undy.tdaid.data.local.SeasonResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private fun result(eventName: String, endDate: String, place: Int, points: Double = 0.0, prizeUsd: Int = 0) =
    SeasonResult(
        tournamentId = eventName,
        eventName = eventName,
        endDate = endDate,
        pdgaNumber = "161437",
        division = "MPO",
        place = place,
        points = points,
        prizeUsd = prizeUsd,
    )

class SeasonProfileTest {

    @Test
    fun `picks the most recent event, latest win, and most valuable result`() {
        val profile = seasonProfileFrom(
            currentYear = 2026,
            results = listOf(
                result("Canberra Cup", "2026-03-15", place = 1, points = 120.0, prizeUsd = 300),
                result("NSW Open (MPO, MA1, MA40)", "2026-08-31", place = 1, points = 180.0, prizeUsd = 850),
                result("Turkey Shoot", "2026-10-12", place = 4, points = 40.0),
            ),
        )!!
        assertEquals("4th · Turkey Shoot", profile.recentResult)
        assertEquals("1st · NSW Open", profile.lastWin)
        assertEquals("1st · NSW Open", profile.bestResultThisYear)
        assertNull(profile.memberSince)
    }

    @Test
    fun `ranks by rating points when no result paid cash`() {
        val profile = seasonProfileFrom(
            currentYear = 2026,
            results = listOf(
                result("Small League Day", "2026-05-01", place = 4, points = 10.0),
                result("State Champs", "2026-06-01", place = 14, points = 210.0),
            ),
        )!!
        assertEquals("14th · State Champs", profile.bestResultThisYear)
        assertNull(profile.lastWin)
    }

    @Test
    fun `labels a result from last calendar year with its year`() {
        val profile = seasonProfileFrom(
            currentYear = 2027,
            results = listOf(
                result("Summer Shootout", "2026-12-06", place = 1, points = 90.0),
                result("Australia Day Doubles", "2027-01-26", place = 3, points = 30.0),
            ),
        )!!
        assertEquals("3rd · Australia Day Doubles", profile.recentResult)
        assertEquals("1st · Summer Shootout (2026)", profile.lastWin)
    }

    @Test
    fun `nothing stored means no profile`() {
        assertNull(seasonProfileFrom(emptyList()))
    }

    @Test
    fun `maps PDGA Live's country names to the API's codes`() {
        assertEquals("AU", isoCountryCode("Australia"))
        assertEquals("NZ", isoCountryCode("new zealand"))
        assertEquals("AU", isoCountryCode("AU"))
        assertNull(isoCountryCode("Atlantis"))
        assertNull(isoCountryCode(null))
    }
}
