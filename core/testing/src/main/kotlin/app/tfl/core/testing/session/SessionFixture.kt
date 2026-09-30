package app.tfl.core.testing.session

import android.content.Context
import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.lock.Argon2idHasher
import app.tfl.core.crypto.lock.LockStateStore
import app.tfl.core.crypto.lock.PinVault
import app.tfl.core.crypto.phrase.Bip39Wordlist
import app.tfl.core.crypto.phrase.RecoveryPhraseCodec
import app.tfl.core.crypto.sodium.SealedBox
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.AppSession
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.session.IdentityTools
import app.tfl.core.session.LockSettings
import app.tfl.core.session.OnboardingDraft
import app.tfl.core.session.ProfileCreator
import app.tfl.core.session.WipeController
import app.tfl.core.testing.crypto.FAST_KDF
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.crypto.FakeHardwareKeys
import app.tfl.core.testing.crypto.JvmSodium
import app.tfl.core.testing.database.PlainDatabaseFactory
import kotlinx.coroutines.Dispatchers

/** The real session stack over JVM libsodium, a fake Keystore and plain (unencrypted) Room files. */
class SessionFixture(val context: Context) {
    val sodium = JvmSodium.api
    val keys = FakeHardwareKeys()
    val clock = FakeDeviceClock()
    val vault = PinVault(sodium, SealedBox(sodium), Argon2idHasher(sodium), LockStateStore(context.noBackupFilesDir, keys), keys, clock)
    val factory = PlainDatabaseFactory(context)
    val database = DatabaseHolder(factory)
    val settings = SettingsRepository(database)
    val identities = IdentityRepository(database)
    private val derivation = IdentityKeyDerivation(sodium)
    private val fingerprints = Fingerprints(sodium)
    private val wordlist = Bip39Wordlist.load(sodium)
    val tools = IdentityTools(sodium, derivation, fingerprints, RecoveryPhraseCodec(sodium, wordlist), wordlist)
    private val creator = ProfileCreator(sodium, factory, derivation, fingerprints)
    val disguise = CalculatorDisguise(context, vault)
    var restarts = 0
        private set
    val wipe = WipeController(context, vault, database, disguise) { restarts++ }
    val session = AppSession(vault, database, settings, creator, { FAST_KDF }, sodium, wipe, tools, Dispatchers.Unconfined)
    val lockSettings = LockSettings(session, vault, settings, identities, creator, disguise, wipe, sodium, Dispatchers.Unconfined)

    fun draft(
        seed: ByteArray,
        restored: Boolean = false,
        duressPin: String? = null,
        duressMode: DuressMode = DuressMode.NONE,
    ) = OnboardingDraft(
        seed = seed,
        restored = restored,
        displayName = "Valkyrie-7",
        pin = PIN.encodeToByteArray(),
        duressPin = duressPin?.encodeToByteArray(),
        duressMode = duressMode,
        autoLock = AutoLockTimeout.SECONDS_30,
    )

    companion object {
        const val PIN = "246810"
        const val DURESS_PIN = "135790"
    }
}
