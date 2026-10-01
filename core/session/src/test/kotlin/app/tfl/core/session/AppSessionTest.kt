package app.tfl.core.session

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.tfl.core.crypto.keystore.HardwareKeyAlias
import app.tfl.core.crypto.lock.PinChangeResult
import app.tfl.core.crypto.phrase.PhraseResult
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.Profile
import app.tfl.core.testing.session.SessionFixture
import app.tfl.core.testing.session.SessionFixture.Companion.DURESS_PIN
import app.tfl.core.testing.session.SessionFixture.Companion.PIN
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class AppSessionTest {

    private val fixture = SessionFixture(ApplicationProvider.getApplicationContext())
    private val session = fixture.session

    @After
    fun tearDown() = fixture.database.close()

    private fun pin(value: String = PIN) = value.encodeToByteArray()

    @Test
    fun `a phone without an identity starts onboarding and clears leftovers`() = runTest {
        val orphan = fixture.context.getDatabasePath("tfl-0badc0ffee00.db").apply {
            parentFile?.mkdirs()
            writeText("left by a crashed onboarding")
        }
        session.start()
        assertEquals(GateState.NeedsOnboarding, session.state.value)
        assertFalse(orphan.exists())
    }

    @Test
    fun `committing a new identity unlocks it at the recovery phrase step`() = runTest {
        session.start()
        val seed = fixture.tools.newSeed()
        val expectedFingerprint = fixture.tools.fingerprintOf(seed)
        session.commitOnboarding(fixture.draft(seed.copyOf()))

        assertEquals(GateState.Unlocked(Profile.REAL, OnboardingStep.RECOVERY_PHRASE), session.state.value)
        val identity = checkNotNull(fixture.identities.get())
        assertEquals("Valkyrie-7", identity.displayName)
        assertEquals(expectedFingerprint, identity.fingerprintHex)
        assertEquals(AutoLockTimeout.SECONDS_30, fixture.settings.get(SettingKeys.AUTO_LOCK))

        val phrase = session.recoveryPhrase()
        assertEquals(24, phrase.size)
        assertArrayEquals(seed, (fixture.tools.decodePhrase(phrase) as PhraseResult.Valid).seed)
    }

    @Test
    fun `a restored identity has the same fingerprint and skips the phrase step`() = runTest {
        session.start()
        val seed = fixture.tools.newSeed()
        val original = fixture.tools.fingerprintOf(seed)
        session.commitOnboarding(fixture.draft(seed, restored = true))
        assertEquals(GateState.Unlocked(Profile.REAL, OnboardingStep.PERMISSIONS), session.state.value)
        assertEquals(original, fixture.identities.get()?.fingerprintHex)
    }

    @Test
    fun `locking closes the database and the PIN reopens it where onboarding stopped`() = runTest {
        session.start()
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))
        session.advanceOnboarding(OnboardingStep.NETWORK)
        session.lock()
        assertEquals(GateState.Locked, session.state.value)
        assertFalse(fixture.database.isOpen)

        assertTrue(session.unlock(pin("000000")) is UnlockOutcome.WrongPin)
        assertEquals(UnlockOutcome.Unlocked, session.unlock(pin()))
        assertEquals(GateState.Unlocked(Profile.REAL, OnboardingStep.NETWORK), session.state.value)
        assertEquals(AutoLockTimeout.SECONDS_30, session.autoLock)
    }

    @Test
    fun `the duress PIN opens an empty decoy with its own identity`() = runTest {
        session.start()
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed(), duressPin = DURESS_PIN, duressMode = DuressMode.DECOY))
        val real = checkNotNull(fixture.identities.get())
        session.lock()

        assertEquals(UnlockOutcome.Unlocked, session.unlock(pin(DURESS_PIN)))
        assertEquals(GateState.Unlocked(Profile.DECOY, OnboardingStep.DONE), session.state.value)
        val decoy = checkNotNull(fixture.identities.get())
        assertEquals(real.displayName, decoy.displayName)
        assertNotEquals(real.fingerprintHex, decoy.fingerprintHex)
    }

    @Test
    fun `nothing done in the decoy changes the real lock configuration`() = runTest {
        session.start()
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed(), duressPin = DURESS_PIN, duressMode = DuressMode.DECOY))
        session.lock()
        session.unlock(pin(DURESS_PIN))

        val lockSettings = fixture.lockSettings
        assertEquals(DuressMode.NONE, lockSettings.state().duressMode) // the decoy shows no duress PIN
        assertEquals(PinChangeResult.CHANGED, lockSettings.setDuress(pin(DURESS_PIN), pin("999999"), DuressMode.WIPE))
        lockSettings.setWipeAfterFailures(5)
        lockSettings.enableBiometric(null)
        assertEquals(DuressMode.WIPE, lockSettings.state().duressMode)
        assertTrue(lockSettings.state().biometricEnabled)

        val real = fixture.vault.status()
        assertEquals(DuressMode.DECOY, real.duressMode)
        assertEquals(0, real.wipeAfterFailures)
        assertFalse(real.biometricEnabled)
        assertFalse(fixture.keys.exists(HardwareKeyAlias.BIOMETRIC))

        // "Change PIN" in the decoy changes the duress PIN; the real PIN still opens the real profile.
        assertEquals(PinChangeResult.CHANGED, lockSettings.changePin(pin(DURESS_PIN), pin("111111")))
        session.lock()
        session.unlock(pin())
        assertEquals(Profile.REAL, session.profile)
        session.lock()
        session.unlock(pin("111111"))
        assertEquals(Profile.DECOY, session.profile)
    }

    @Test
    fun `biometric unlock is not allowed while a duress PIN is set`() = runTest {
        session.start()
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed()))
        assertTrue(fixture.lockSettings.state().biometricAllowed)
        assertEquals(PinChangeResult.CHANGED, fixture.lockSettings.setDuress(pin(), pin(DURESS_PIN), DuressMode.WIPE))
        assertFalse(fixture.lockSettings.state().biometricAllowed)
    }

    @Test
    fun `the duress PIN in wipe mode wipes and restarts`() = runTest {
        session.start()
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed(), duressPin = DURESS_PIN, duressMode = DuressMode.WIPE))
        session.lock()
        assertEquals(UnlockOutcome.Wiped, session.unlock(pin(DURESS_PIN)))
        assertEquals(GateState.Wiping, session.state.value)
        assertEquals(1, fixture.restarts)
        assertFalse(fixture.vault.isSetUp())
        assertTrue(fixture.keys.aliases.isEmpty())
        assertTrue(fixture.context.databaseList().none { it.startsWith("tfl-") })
    }

    @Test
    fun `a wipe leaves nothing behind`() = runTest {
        val context = fixture.context
        session.start()
        session.commitOnboarding(fixture.draft(fixture.tools.newSeed(), duressPin = DURESS_PIN, duressMode = DuressMode.DECOY))
        fixture.lockSettings.enableDisguise(pin("4321"))
        File(context.filesDir, "notes/draft.txt").apply { parentFile?.mkdirs(); writeText("x") }
        File(context.cacheDir, "thumb.bin").writeText("x")
        context.getSharedPreferences("prefs", 0).edit().putString("k", "v").commit()
        context.getExternalFilesDir(null)?.let { File(it, "export.bin").writeText("x") }
        assertTrue(fixture.disguise.isEnabled())
        assertTrue("what's showing can follow the switch", fixture.disguise.enabled.value)

        session.wipe()

        fixture.wipe.appDataDirectories().forEach { directory ->
            assertTrue("$directory is not empty", directory.listFiles().isNullOrEmpty())
        }
        assertTrue(fixture.keys.aliases.isEmpty())
        assertFalse(fixture.vault.isSetUp())
        assertFalse(fixture.disguise.isEnabled())
        assertFalse(fixture.disguise.enabled.value)
        assertEquals(1, fixture.restarts)
    }
}
