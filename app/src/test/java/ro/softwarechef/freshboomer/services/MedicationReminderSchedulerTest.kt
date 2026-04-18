package ro.softwarechef.freshboomer.services

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * Unit tests for [MedicationReminderScheduler.calculateNextFireTime].
 *
 * Tests are written to be timezone-independent: we assert structural
 * properties (validity, day-of-week membership, zeroed seconds/millis) rather
 * than absolute timestamps, since the scheduler uses the JVM's default
 * [Calendar] and therefore depends on the local timezone.
 */
class MedicationReminderSchedulerTest {

    private val allDays = listOf(1, 2, 3, 4, 5, 6, 7)

    private var savedTz: TimeZone? = null

    @Before
    fun pinTimeZone() {
        savedTz = TimeZone.getDefault()
        // Fix TZ so absolute-timestamp tests are deterministic across machines.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    }

    @After
    fun restoreTimeZone() {
        savedTz?.let { TimeZone.setDefault(it) }
    }

    /**
     * Build a UTC epoch-ms for a given calendar date/time. Used by the
     * absolute-timestamp tests to pin both "now" and the expected fire time.
     */
    private fun utcMs(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun `empty daysOfWeek returns null`() {
        assertNull(MedicationReminderScheduler.calculateNextFireTime("08:00", emptyList()))
    }

    @Test
    fun `malformed time - no colon - returns null`() {
        assertNull(MedicationReminderScheduler.calculateNextFireTime("0800", allDays))
    }

    @Test
    fun `malformed time - empty string - returns null`() {
        assertNull(MedicationReminderScheduler.calculateNextFireTime("", allDays))
    }

    @Test
    fun `malformed time - too many parts - returns null`() {
        assertNull(MedicationReminderScheduler.calculateNextFireTime("08:00:30", allDays))
    }

    @Test
    fun `non-numeric hour returns null`() {
        assertNull(MedicationReminderScheduler.calculateNextFireTime("ab:00", allDays))
    }

    @Test
    fun `non-numeric minute returns null`() {
        assertNull(MedicationReminderScheduler.calculateNextFireTime("08:cd", allDays))
    }

    @Test
    fun `all-days schedule returns a time strictly in the future`() {
        val now = System.currentTimeMillis()
        val fire = MedicationReminderScheduler.calculateNextFireTime("08:00", allDays)
        assertNotNull(fire)
        assertTrue(
            "Fire time must be strictly after now",
            fire!! > now
        )
    }

    @Test
    fun `returned time has seconds and millis zeroed`() {
        val fire = MedicationReminderScheduler.calculateNextFireTime("08:00", allDays)
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        assertEquals(0, cal.get(Calendar.SECOND))
        assertEquals(0, cal.get(Calendar.MILLISECOND))
    }

    @Test
    fun `returned time hour and minute match requested`() {
        val fire = MedicationReminderScheduler.calculateNextFireTime("14:37", allDays)
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        assertEquals(14, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(37, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `single-day schedule lands on that ISO day-of-week`() {
        // Schedule for Wednesdays only (ISO 3)
        val fire = MedicationReminderScheduler.calculateNextFireTime("09:00", listOf(3))
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        val calDow = cal.get(Calendar.DAY_OF_WEEK)
        // ISO 3 = Wednesday
        assertEquals(Calendar.WEDNESDAY, calDow)
    }

    @Test
    fun `weekend-only schedule lands on Saturday or Sunday`() {
        // ISO 6=Saturday, 7=Sunday
        val fire = MedicationReminderScheduler.calculateNextFireTime("10:00", listOf(6, 7))
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        val calDow = cal.get(Calendar.DAY_OF_WEEK)
        assertTrue(
            "Expected Saturday or Sunday, got $calDow",
            calDow == Calendar.SATURDAY || calDow == Calendar.SUNDAY
        )
    }

    @Test
    fun `weekday-only schedule lands on a weekday`() {
        // ISO 1..5 = Mon..Fri
        val fire = MedicationReminderScheduler.calculateNextFireTime("07:30", listOf(1, 2, 3, 4, 5))
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        val calDow = cal.get(Calendar.DAY_OF_WEEK)
        assertTrue(
            "Expected Monday..Friday, got $calDow",
            calDow in listOf(
                Calendar.MONDAY, Calendar.TUESDAY, Calendar.WEDNESDAY,
                Calendar.THURSDAY, Calendar.FRIDAY
            )
        )
    }

    @Test
    fun `hour at boundary 0 is accepted`() {
        val fire = MedicationReminderScheduler.calculateNextFireTime("00:00", allDays)
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        assertEquals(0, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(0, cal.get(Calendar.MINUTE))
    }

    @Test
    fun `hour at boundary 23 is accepted`() {
        val fire = MedicationReminderScheduler.calculateNextFireTime("23:59", allDays)
        assertNotNull(fire)
        val cal = Calendar.getInstance().apply { timeInMillis = fire!! }
        assertEquals(23, cal.get(Calendar.HOUR_OF_DAY))
        assertEquals(59, cal.get(Calendar.MINUTE))
    }

    // ----- Absolute-timestamp tests (injected clock + pinned UTC timezone) -----

    /** 2026-04-20 10:00 UTC is a Monday. ISO day-of-week: 1 (Monday). */
    private val mondayMorning = utcMs(2026, 4, 20, 10, 0)

    @Test
    fun `later today same day - returns today`() {
        // "now" is Monday 10:00 UTC, target 14:30 any-day => today 14:30
        val expected = utcMs(2026, 4, 20, 14, 30)
        val actual = MedicationReminderScheduler.calculateNextFireTime("14:30", allDays, mondayMorning)
        assertEquals(expected, actual)
    }

    @Test
    fun `earlier today same day - returns tomorrow`() {
        // "now" is Monday 10:00 UTC, target 08:00 any-day => Tuesday 08:00
        val expected = utcMs(2026, 4, 21, 8, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime("08:00", allDays, mondayMorning)
        assertEquals(expected, actual)
    }

    @Test
    fun `exact now - returns next day at same time`() {
        // "now" is Monday 10:00 UTC, target 10:00 => Tuesday 10:00 (strictly-after rule)
        val expected = utcMs(2026, 4, 21, 10, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime("10:00", allDays, mondayMorning)
        assertEquals(expected, actual)
    }

    @Test
    fun `wednesday-only from monday - jumps to wednesday`() {
        // "now" is Monday 10:00, Wednesday is ISO 3, two days later
        val expected = utcMs(2026, 4, 22, 9, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime("09:00", listOf(3), mondayMorning)
        assertEquals(expected, actual)
    }

    @Test
    fun `sunday-only from monday - wraps to next sunday`() {
        // "now" is Mon 2026-04-20, Sunday is ISO 7 => next Sun 2026-04-26
        val expected = utcMs(2026, 4, 26, 12, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime("12:00", listOf(7), mondayMorning)
        assertEquals(expected, actual)
    }

    @Test
    fun `weekday-only from friday evening - returns monday`() {
        // Friday 2026-04-24 20:00 UTC, weekdays Mon..Fri (ISO 1..5),
        // target 09:00 => next Monday 2026-04-27 09:00
        val fridayEvening = utcMs(2026, 4, 24, 20, 0)
        val expected = utcMs(2026, 4, 27, 9, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime(
            "09:00", listOf(1, 2, 3, 4, 5), fridayEvening
        )
        assertEquals(expected, actual)
    }

    @Test
    fun `month boundary crossing`() {
        // Thursday 2026-04-30 23:00 UTC, any day, target 08:00 =>
        // May 1, 2026 08:00 (Friday)
        val aprilLastNight = utcMs(2026, 4, 30, 23, 0)
        val expected = utcMs(2026, 5, 1, 8, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime("08:00", allDays, aprilLastNight)
        assertEquals(expected, actual)
    }

    @Test
    fun `year boundary crossing`() {
        // Thursday 2026-12-31 23:00 UTC, any day, target 08:00 =>
        // 2027-01-01 08:00
        val nye = utcMs(2026, 12, 31, 23, 0)
        val expected = utcMs(2027, 1, 1, 8, 0)
        val actual = MedicationReminderScheduler.calculateNextFireTime("08:00", allDays, nye)
        assertEquals(expected, actual)
    }
}
