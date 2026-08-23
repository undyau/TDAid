package com.undy.tdaid.notify

import java.util.Calendar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TeeAlarmSchedulerTest {

    private fun hourAndMinuteOf(millis: Long): Pair<Int, Int> {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return cal.get(Calendar.HOUR_OF_DAY) to cal.get(Calendar.MINUTE)
    }

    @Test
    fun `parses a morning time correctly`() {
        val millis = TeeAlarmScheduler.parseTodayMillis("8:30 AM")
        assertEquals(8 to 30, millis?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `parses noon as hour 12, not 0`() {
        val millis = TeeAlarmScheduler.parseTodayMillis("12:00 PM")
        assertEquals(12 to 0, millis?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `parses midnight as hour 0, not 12`() {
        val millis = TeeAlarmScheduler.parseTodayMillis("12:00 AM")
        assertEquals(0 to 0, millis?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `parses a late evening time correctly`() {
        val millis = TeeAlarmScheduler.parseTodayMillis("11:59 PM")
        assertEquals(23 to 59, millis?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `zeroes out seconds and milliseconds`() {
        val millis = requireNotNull(TeeAlarmScheduler.parseTodayMillis("9:03 AM"))
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `returns null for unparsable input rather than throwing`() {
        assertNull(TeeAlarmScheduler.parseTodayMillis("not a time"))
        assertNull(TeeAlarmScheduler.parseTodayMillis(""))
    }

    @Test
    fun `parses a real PDGA Live 24-hour time with seconds`() {
        val millis = TeeAlarmScheduler.parseTodayMillis("14:30:00")
        assertEquals(14 to 30, millis?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `parses a real PDGA Live 24-hour time before noon`() {
        val millis = TeeAlarmScheduler.parseTodayMillis("09:03:00")
        assertEquals(9 to 3, millis?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `parses 24-hour midnight and last minute of the day correctly`() {
        assertEquals(0 to 0, TeeAlarmScheduler.parseTodayMillis("00:00:00")?.let { hourAndMinuteOf(it) })
        assertEquals(23 to 59, TeeAlarmScheduler.parseTodayMillis("23:59:00")?.let { hourAndMinuteOf(it) })
    }

    @Test
    fun `rejects an out-of-range 24-hour time rather than misreading it`() {
        assertNull(TeeAlarmScheduler.parseTodayMillis("24:00:00"))
        assertNull(TeeAlarmScheduler.parseTodayMillis("12:60:00"))
    }
}
