package app.tfl.core.session

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PinEntryTest {

    private fun PinEntry.type(digits: String) = digits.forEach { digit(it.digitToInt()) }

    @Test
    fun `a PIN typed twice the same is complete, as ASCII digits`() {
        val entry = PinEntry()
        entry.type("123456")
        assertEquals(PinEntry.Stage.CONFIRM, entry.stage)
        assertEquals(0, entry.entered)
        entry.type("123456")
        assertEquals(PinEntry.Stage.COMPLETE, entry.stage)
        assertArrayEquals("123456".encodeToByteArray(), entry.value())
    }

    @Test
    fun `a different confirmation starts over and flags the mismatch until the next digit`() {
        val entry = PinEntry()
        entry.type("123456")
        entry.type("123457")
        assertEquals(PinEntry.Stage.ENTER, entry.stage)
        assertTrue(entry.mismatch)
        entry.digit(1)
        assertFalse(entry.mismatch)
        assertEquals(1, entry.entered)
    }

    @Test
    fun `delete removes the last digit and does nothing when empty or finished`() {
        val entry = PinEntry()
        entry.delete()
        assertEquals(0, entry.entered)
        entry.type("12")
        entry.delete()
        entry.type("3456")
        entry.type("7")
        // The first entry is 134567: the 2 was deleted.
        entry.type("134567")
        assertEquals(PinEntry.Stage.COMPLETE, entry.stage)
        entry.delete()
        entry.digit(9)
        assertArrayEquals("134567".encodeToByteArray(), entry.value())
    }

    @Test
    fun `without confirmation one entry completes it`() {
        val entry = PinEntry(length = 4, confirm = false)
        entry.type("2468")
        assertEquals(PinEntry.Stage.COMPLETE, entry.stage)
        assertArrayEquals("2468".encodeToByteArray(), entry.value())
    }

    @Test
    fun `the value is only available once complete`() {
        val entry = PinEntry()
        entry.type("123")
        assertThrows(IllegalStateException::class.java) { entry.value() }
    }

    @Test
    fun `sameAs spots another finished PIN as soon as the first entry is typed`() {
        val pin = PinEntry().apply { type("123456"); type("123456") }
        val other = PinEntry()
        other.type("12345")
        assertFalse(other.sameAs(pin))
        other.digit(6)
        assertTrue(other.sameAs(pin))

        val different = PinEntry().apply { type("654321") }
        assertFalse(different.sameAs(pin))
        assertFalse(pin.sameAs(PinEntry().apply { type("123456") }))
    }

    @Test
    fun `reset clears everything`() {
        val entry = PinEntry()
        entry.type("123456")
        entry.type("12")
        entry.reset()
        assertEquals(PinEntry.Stage.ENTER, entry.stage)
        assertEquals(0, entry.entered)
        assertFalse(entry.mismatch)
    }
}
