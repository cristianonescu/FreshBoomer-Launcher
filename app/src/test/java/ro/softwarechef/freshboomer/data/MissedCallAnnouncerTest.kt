package ro.softwarechef.freshboomer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MissedCallAnnouncerTest {

    @Test
    fun `no current call yields no announcement and resets state`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = null,
            lastAnnouncedNumber = "0123",
            storedCount = 2,
            maxAnnouncements = 3
        )
        assertFalse(d.shouldAnnounce)
        assertEquals(0, d.newCount)
        assertNull(d.newLastAnnouncedNumber)
    }

    @Test
    fun `first announcement of a new call starts counter at 1`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0711111111",
            lastAnnouncedNumber = null,
            storedCount = 0,
            maxAnnouncements = 3
        )
        assertTrue(d.shouldAnnounce)
        assertEquals(1, d.newCount)
        assertEquals("0711111111", d.newLastAnnouncedNumber)
    }

    @Test
    fun `same call under the cap increments the counter`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0711111111",
            lastAnnouncedNumber = "0711111111",
            storedCount = 1,
            maxAnnouncements = 3
        )
        assertTrue(d.shouldAnnounce)
        assertEquals(2, d.newCount)
        assertEquals("0711111111", d.newLastAnnouncedNumber)
    }

    @Test
    fun `same call at the cap does not announce and preserves count`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0711111111",
            lastAnnouncedNumber = "0711111111",
            storedCount = 3,
            maxAnnouncements = 3
        )
        assertFalse(d.shouldAnnounce)
        assertEquals(3, d.newCount)
        assertEquals("0711111111", d.newLastAnnouncedNumber)
    }

    @Test
    fun `same call above the cap (config shrunk) stays silent`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0711111111",
            lastAnnouncedNumber = "0711111111",
            storedCount = 5,
            maxAnnouncements = 3
        )
        assertFalse(d.shouldAnnounce)
        assertEquals(5, d.newCount)
    }

    @Test
    fun `different caller resets counter and announces once`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0722222222",
            lastAnnouncedNumber = "0711111111",
            storedCount = 3,
            maxAnnouncements = 3
        )
        assertTrue(d.shouldAnnounce)
        assertEquals(1, d.newCount)
        assertEquals("0722222222", d.newLastAnnouncedNumber)
    }

    @Test
    fun `zero maxAnnouncements disables announcements entirely`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0711111111",
            lastAnnouncedNumber = null,
            storedCount = 0,
            maxAnnouncements = 0
        )
        assertFalse(d.shouldAnnounce)
        assertEquals(0, d.newCount)
        assertEquals("0711111111", d.newLastAnnouncedNumber)
    }

    @Test
    fun `negative maxAnnouncements is treated as disabled`() {
        val d = MissedCallAnnouncer.decide(
            currentCallNumber = "0711111111",
            lastAnnouncedNumber = null,
            storedCount = 0,
            maxAnnouncements = -1
        )
        assertFalse(d.shouldAnnounce)
    }

    @Test
    fun `maxAnnouncements of 1 announces exactly once`() {
        val first = MissedCallAnnouncer.decide(
            currentCallNumber = "0700000000",
            lastAnnouncedNumber = null,
            storedCount = 0,
            maxAnnouncements = 1
        )
        assertTrue(first.shouldAnnounce)
        assertEquals(1, first.newCount)

        val second = MissedCallAnnouncer.decide(
            currentCallNumber = "0700000000",
            lastAnnouncedNumber = "0700000000",
            storedCount = first.newCount,
            maxAnnouncements = 1
        )
        assertFalse(second.shouldAnnounce)
        assertEquals(1, second.newCount)
    }
}
