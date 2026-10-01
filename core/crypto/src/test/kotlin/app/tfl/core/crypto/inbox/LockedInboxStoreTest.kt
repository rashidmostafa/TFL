package app.tfl.core.crypto.inbox

import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.testing.crypto.FakeHardwareKeys
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class LockedInboxStoreTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val keys = FakeHardwareKeys()
    private val store by lazy { LockedInboxStore(folder.root, keys) }
    private val file get() = File(folder.root, LockedInboxStore.FILE_NAME)

    private fun records(vararg sizes: Int) = sizes.mapIndexed { i, size -> ByteArray(size) { (i + 1).toByte() } }

    @Test
    fun `records come back in order, and the file never holds them in the clear`() {
        val written = records(300, 1, 16_500)
        written.forEach { assertTrue(store.append(it)) }
        val read = store.readAll()
        assertEquals(written.size, read.size)
        written.zip(read).forEach { (expected, actual) -> assertArrayEquals(expected, actual) }

        val raw = file.readBytes()
        assertFalse("a run of plaintext bytes", raw.toList().windowed(32).any { window -> window.all { it == 3.toByte() } })
        assertTrue(keys.exists(HardwareKeyAlias.INBOX))
    }

    @Test
    fun `a record cut short by a crash is ignored`() {
        records(10, 20).forEach { store.append(it) }
        val whole = file.readBytes()
        file.writeBytes(whole.copyOf(whole.size - 5))
        assertEquals(1, store.readAll().size)
    }

    @Test
    fun `a changed record is skipped, the rest still read`() {
        records(10, 20, 30).forEach { store.append(it) }
        val bytes = file.readBytes()
        bytes[4 + 12 + 2] = (bytes[4 + 12 + 2].toInt() xor 1).toByte() // inside the first record's ciphertext
        file.writeBytes(bytes)
        assertEquals(listOf(20, 30), store.readAll().map { it.size })
    }

    @Test
    fun `without its hardware key the inbox is unreadable`() {
        store.append(ByteArray(10))
        keys.delete(HardwareKeyAlias.INBOX)
        keys.ensureKey(HardwareKeyAlias.INBOX) // a new key can't open the old records
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `keeping some records and clearing`() {
        val written = records(10, 20, 30)
        written.forEach { store.append(it) }
        store.replaceAll(listOf(written[1]))
        assertEquals(listOf(20), store.readAll().map { it.size })
        store.replaceAll(emptyList())
        assertFalse(file.exists())
        store.append(ByteArray(5))
        store.clear()
        assertTrue(store.readAll().isEmpty())
    }

    @Test
    fun `a full inbox refuses more`() {
        val big = ByteArray(1_000_000)
        var accepted = 0
        while (store.append(big)) accepted++
        assertEquals(8, accepted)
        assertTrue(file.length() <= LockedInboxStore.MAX_BYTES)
        assertTrue("small ones may still fit", store.append(ByteArray(10)))
    }
}
