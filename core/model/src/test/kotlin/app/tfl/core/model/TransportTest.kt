package app.tfl.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class TransportTest {

    @Test
    fun `entries follow routing priority`() {
        assertEquals(
            listOf(Transport.NEARBY, Transport.MESH, Transport.TOR, Transport.QUEUED),
            Transport.entries,
        )
    }

    @Test
    fun `preferred picks the highest-priority reachable transport`() {
        assertEquals(Transport.NEARBY, Transport.preferred(setOf(Transport.TOR, Transport.NEARBY, Transport.MESH)))
        assertEquals(Transport.MESH, Transport.preferred(setOf(Transport.TOR, Transport.MESH)))
        assertEquals(Transport.TOR, Transport.preferred(setOf(Transport.TOR)))
    }

    @Test
    fun `preferred falls back to queued when nothing is reachable`() {
        assertEquals(Transport.QUEUED, Transport.preferred(emptySet()))
        assertEquals(Transport.QUEUED, Transport.preferred(setOf(Transport.QUEUED)))
    }
}
