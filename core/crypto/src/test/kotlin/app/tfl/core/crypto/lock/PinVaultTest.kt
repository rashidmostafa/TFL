package app.tfl.core.crypto.lock

import app.tfl.core.crypto.CryptoException
import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.sodium.SealedBox
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.Profile
import app.tfl.core.testing.crypto.CountingHasher
import app.tfl.core.testing.crypto.FAST_KDF
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.crypto.FakeHardwareKeys
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PinVaultTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val sodium = JvmSodium.api
    private val keys = FakeHardwareKeys()
    private val clock = FakeDeviceClock()
    private val hasher = CountingHasher(Argon2idHasher(sodium))
    private val store by lazy { LockStateStore(folder.root, keys) }
    private val vault by lazy { PinVault(sodium, SealedBox(sodium), hasher, store, keys, clock) }

    private val pin = pin("246810")
    private val duressPin = pin("135790")
    private val wrongPin = pin("000000")

    private val unlockKey = sodium.randomBytes(32)
    private val realSecrets = ProfileSecrets("tfl-real.db", sodium.randomBytes(32), sodium.randomBytes(32))
    private val decoyUnlockKey = sodium.randomBytes(32)
    private val decoySecrets = ProfileSecrets("tfl-decoy.db", sodium.randomBytes(32), sodium.randomBytes(32))

    private fun pin(digits: String) = digits.encodeToByteArray()

    private fun create(duress: DuressSetup? = null) {
        vault.create(VaultSetup(pin, FAST_KDF, unlockKey, realSecrets, duress))
        hasher.reset()
    }

    private fun decoyDuress() = DuressSetup(duressPin, DuressMode.DECOY, decoyUnlockKey, decoySecrets)

    private fun opened(result: UnlockResult): OpenedProfile = (result as UnlockResult.Opened).profile

    @Test
    fun `the real PIN opens the real profile`() {
        create()
        val profile = opened(vault.unlock(pin))
        assertEquals(Profile.REAL, profile.profile)
        assertEquals("tfl-real.db", profile.databaseName)
        assertArrayEquals(unlockKey, profile.unlockKey)
        assertArrayEquals(realSecrets.databaseKey, profile.databaseKey)
        assertArrayEquals(realSecrets.seed, vault.openSeed(Profile.REAL, profile.unlockKey))
    }

    @Test
    fun `a wrong PIN is counted and reported`() {
        create()
        assertEquals(UnlockResult.WrongPin(failures = 1, retryAfterMillis = 0, attemptsBeforeWipe = null), vault.unlock(wrongPin))
        assertEquals(1, vault.status().failures)
    }

    @Test
    fun `the attempt is recorded before the PIN is checked`() {
        create()
        hasher.failNext = true // the process "dies" during the key derivation
        assertThrows(CryptoException::class.java) { vault.unlock(pin) }
        assertEquals(1, vault.status().failures)
    }

    @Test
    fun `success resets the counter`() {
        create()
        repeat(3) { vault.unlock(wrongPin) }
        vault.unlock(pin)
        assertEquals(0, vault.status().failures)
    }

    @Test
    fun `both derivations always run, whichever PIN is entered`() {
        create()
        vault.unlock(wrongPin)
        assertEquals("wrong PIN, no duress PIN set", 2, hasher.calls)
        hasher.reset()
        vault.unlock(pin)
        assertEquals("real PIN, no duress PIN set", 2, hasher.calls)
    }

    @Test
    fun `both derivations run with a duress PIN configured too`() {
        create(decoyDuress())
        for (entered in listOf(pin, duressPin, wrongPin)) {
            hasher.reset()
            vault.unlock(entered)
            assertEquals(2, hasher.calls)
        }
    }

    @Test
    fun `the duress PIN opens the decoy profile in decoy mode`() {
        create(decoyDuress())
        val profile = opened(vault.unlock(duressPin))
        assertEquals(Profile.DECOY, profile.profile)
        assertEquals("tfl-decoy.db", profile.databaseName)
        assertArrayEquals(decoySecrets.databaseKey, profile.databaseKey)
        assertArrayEquals(decoySecrets.seed, vault.openSeed(Profile.DECOY, profile.unlockKey))
        // the real PIN still opens the real profile
        assertEquals(Profile.REAL, opened(vault.unlock(pin)).profile)
    }

    @Test
    fun `the duress PIN asks for a wipe in wipe mode`() {
        create(DuressSetup(duressPin, DuressMode.WIPE))
        assertEquals(UnlockResult.WipeRequired(WipeReason.DURESS_PIN), vault.unlock(duressPin))
    }

    @Test
    fun `the duress PIN must differ from the PIN`() {
        assertThrows(IllegalArgumentException::class.java) {
            vault.create(VaultSetup(pin, FAST_KDF, unlockKey, realSecrets, DuressSetup(pin.copyOf(), DuressMode.WIPE)))
        }
    }

    @Test
    fun `delays start at the fifth failure and block checking`() {
        create()
        repeat(4) { vault.unlock(wrongPin) }
        assertEquals(30_000L, (vault.unlock(wrongPin) as UnlockResult.WrongPin).retryAfterMillis)
        hasher.reset()
        assertEquals(UnlockResult.Throttled(30_000), vault.unlock(pin))
        assertEquals("no derivation while throttled", 0, hasher.calls)
        clock.advance(30_000)
        assertEquals(Profile.REAL, opened(vault.unlock(pin)).profile)
    }

    @Test
    fun `rebooting doesn't skip the delay`() {
        create()
        repeat(5) { vault.unlock(wrongPin) }
        clock.reboot(elapsedAfterBoot = 10_000)
        assertEquals(UnlockResult.Throttled(20_000), vault.unlock(pin))
    }

    @Test
    fun `wipe after N failures`() {
        create()
        vault.setWipeAfterFailures(3)
        assertEquals(2, (vault.unlock(wrongPin) as UnlockResult.WrongPin).attemptsBeforeWipe)
        assertEquals(1, (vault.unlock(wrongPin) as UnlockResult.WrongPin).attemptsBeforeWipe)
        assertEquals(UnlockResult.WipeRequired(WipeReason.TOO_MANY_ATTEMPTS), vault.unlock(wrongPin))
    }

    @Test
    fun `changing the PIN`() {
        create()
        assertEquals(PinChangeResult.WRONG_PIN, vault.changePin(Profile.REAL, wrongPin, pin("111111")))
        assertEquals(PinChangeResult.CHANGED, vault.changePin(Profile.REAL, pin, pin("111111")))
        assertTrue(vault.unlock(pin) is UnlockResult.WrongPin)
        assertArrayEquals(unlockKey, opened(vault.unlock(pin("111111"))).unlockKey)
    }

    @Test
    fun `the new PIN can't be the duress PIN`() {
        create(decoyDuress())
        assertEquals(PinChangeResult.SAME_AS_OTHER_PIN, vault.changePin(Profile.REAL, pin, duressPin))
    }

    @Test
    fun `changing the PIN inside the decoy only changes the duress PIN`() {
        create(decoyDuress())
        assertEquals(PinChangeResult.CHANGED, vault.changePin(Profile.DECOY, duressPin, pin("222222")))
        assertEquals(Profile.DECOY, opened(vault.unlock(pin("222222"))).profile)
        assertEquals(Profile.REAL, opened(vault.unlock(pin)).profile)
        assertTrue(vault.unlock(duressPin) is UnlockResult.WrongPin)
    }

    @Test
    fun `setting and removing a duress PIN needs the real PIN`() {
        create()
        assertEquals(PinChangeResult.WRONG_PIN, vault.setDuress(wrongPin, DuressSetup(duressPin, DuressMode.WIPE)))
        assertEquals(PinChangeResult.SAME_AS_OTHER_PIN, vault.setDuress(pin, DuressSetup(pin.copyOf(), DuressMode.WIPE)))
        assertEquals(PinChangeResult.CHANGED, vault.setDuress(pin, DuressSetup(duressPin, DuressMode.WIPE)))
        assertEquals(DuressMode.WIPE, vault.status().duressMode)
        assertFalse(vault.removeDuress(wrongPin))
        assertTrue(vault.removeDuress(pin))
        assertEquals(DuressMode.NONE, vault.status().duressMode)
        assertTrue(vault.unlock(duressPin) is UnlockResult.WrongPin)
    }

    @Test
    fun `biometric unlock opens the real profile`() {
        create()
        vault.enableBiometric(unlockKey, vault.biometricEnrolCipher())
        assertTrue(vault.status().biometricEnabled)
        val profile = vault.unlockWithBiometric(checkNotNull(vault.biometricUnlockCipher()))
        assertEquals(Profile.REAL, profile.profile)
        assertArrayEquals(realSecrets.databaseKey, profile.databaseKey)
    }

    @Test
    fun `setting a duress PIN switches biometric unlock off`() {
        create()
        vault.enableBiometric(unlockKey, vault.biometricEnrolCipher())
        vault.setDuress(pin, DuressSetup(duressPin, DuressMode.WIPE))
        assertFalse(vault.status().biometricEnabled)
        assertFalse(keys.exists(HardwareKeyAlias.BIOMETRIC))
        assertNull(vault.biometricUnlockCipher())
        assertThrows(IllegalStateException::class.java) { vault.biometricEnrolCipher() }
    }

    @Test
    fun `disguise code`() {
        create()
        assertFalse(vault.matchesDisguiseCode(pin("1234")))
        vault.setDisguiseCode(pin("1234"))
        assertTrue(vault.matchesDisguiseCode(pin("1234")))
        assertFalse(vault.matchesDisguiseCode(pin("12345")))
        vault.clearDisguiseCode()
        assertFalse(vault.matchesDisguiseCode(pin("1234")))
    }

    @Test
    fun `a tampered lock state is refused`() {
        create()
        val file = File(folder.root, LockStateStore.FILE_NAME)
        val bytes = file.readBytes()
        bytes[bytes.size - 5] = (bytes[bytes.size - 5].toInt() xor 1).toByte()
        file.writeBytes(bytes)
        assertThrows(CryptoException::class.java) { vault.unlock(pin) }
    }

    @Test
    fun `a lock state is useless without its hardware key`() {
        create()
        keys.delete(HardwareKeyAlias.STATE)
        keys.ensureKey(HardwareKeyAlias.STATE)
        assertThrows(CryptoException::class.java) { vault.unlock(pin) }
    }

    @Test
    fun `destroy deletes the hardware keys and the lock state`() {
        create()
        vault.enableBiometric(unlockKey, vault.biometricEnrolCipher())
        vault.destroy()
        assertTrue(keys.aliases.isEmpty())
        assertFalse(vault.isSetUp())
        assertTrue(folder.root.listFiles().isNullOrEmpty())
    }
}
