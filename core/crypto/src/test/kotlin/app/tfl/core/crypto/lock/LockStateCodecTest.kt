package app.tfl.core.crypto.lock

import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class LockStateCodecTest {

    private fun bytes(size: Int, value: Int) = ByteArray(size) { value.toByte() }

    private val full = LockState(
        kdf = KdfParams.MODERATE,
        keyStorage = KeyStorageLevel.STRONGBOX,
        real = PinSlot(bytes(16, 1), bytes(32, 2), bytes(73, 3)),
        realKeys = ProfileKeys("tfl-a1b2c3d4.db", bytes(73, 4), bytes(73, 5)),
        duressMode = DuressMode.DECOY,
        duress = PinSlot(bytes(16, 6), bytes(32, 7), bytes(73, 8)),
        decoyKeys = ProfileKeys("tfl-e5f6a7b8.db", bytes(73, 9), bytes(73, 10)),
        biometricUnlockKey = bytes(60, 11),
        attempts = AttemptRecord(failures = 3, bootCount = 42, elapsedAtLastFailure = 123_456_789),
        wipeAfterFailures = 10,
        disguise = DisguiseCodeHash(bytes(32, 12), bytes(32, 13)),
    )

    @Test
    fun `every field survives a round trip`() {
        val decoded = LockStateCodec.decode(LockStateCodec.encode(full))
        assertEquals(full.kdf, decoded.kdf)
        assertEquals(full.keyStorage, decoded.keyStorage)
        assertArrayEquals(full.real.salt, decoded.real.salt)
        assertArrayEquals(full.real.verifier, decoded.real.verifier)
        assertArrayEquals(full.real.wrappedUnlockKey, decoded.real.wrappedUnlockKey)
        assertEquals(full.realKeys.databaseName, decoded.realKeys.databaseName)
        assertArrayEquals(full.realKeys.wrappedDatabaseKey, decoded.realKeys.wrappedDatabaseKey)
        assertArrayEquals(full.realKeys.wrappedSeed, decoded.realKeys.wrappedSeed)
        assertEquals(full.duressMode, decoded.duressMode)
        assertArrayEquals(full.duress.wrappedUnlockKey, decoded.duress.wrappedUnlockKey)
        assertEquals("tfl-e5f6a7b8.db", decoded.decoyKeys?.databaseName)
        assertArrayEquals(full.biometricUnlockKey, decoded.biometricUnlockKey)
        assertEquals(full.attempts, decoded.attempts)
        assertEquals(10, decoded.wipeAfterFailures)
        assertArrayEquals(full.disguise?.hash, decoded.disguise?.hash)
    }

    @Test
    fun `optional fields can be absent`() {
        val minimal = full.copy(
            duressMode = DuressMode.NONE,
            duress = PinSlot(bytes(16, 6), bytes(32, 7), null),
            decoyKeys = null,
            biometricUnlockKey = null,
            disguise = null,
        )
        val decoded = LockStateCodec.decode(LockStateCodec.encode(minimal))
        assertNull(decoded.duress.wrappedUnlockKey)
        assertNull(decoded.decoyKeys)
        assertNull(decoded.biometricUnlockKey)
        assertNull(decoded.disguise)
    }

    @Test
    fun `truncated, foreign or padded data is rejected`() {
        val encoded = LockStateCodec.encode(full)
        assertThrows(LockStateFormatException::class.java) { LockStateCodec.decode(encoded.copyOf(encoded.size - 1)) }
        assertThrows(LockStateFormatException::class.java) { LockStateCodec.decode(encoded + 0) }
        assertThrows(LockStateFormatException::class.java) { LockStateCodec.decode(byteArrayOf(1, 2, 3, 4, 5)) }
        val futureVersion = encoded.copyOf().also { it[4] = 9 }
        assertThrows(LockStateFormatException::class.java) { LockStateCodec.decode(futureVersion) }
    }
}
