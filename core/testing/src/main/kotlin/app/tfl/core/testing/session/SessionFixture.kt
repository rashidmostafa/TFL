package app.tfl.core.testing.session

import android.content.Context
import app.tfl.core.crypto.identity.Fingerprints
import app.tfl.core.crypto.identity.IdentityKeyDerivation
import app.tfl.core.crypto.lock.Argon2idHasher
import app.tfl.core.crypto.lock.LockStateStore
import app.tfl.core.crypto.lock.PinVault
import app.tfl.core.crypto.pairing.PairingCodes
import app.tfl.core.crypto.pairing.SafetyNumbers
import app.tfl.core.crypto.phrase.Bip39Wordlist
import app.tfl.core.crypto.phrase.RecoveryPhraseCodec
import app.tfl.core.crypto.sodium.SealedBox
import app.tfl.core.database.DatabaseHolder
import app.tfl.core.database.repository.ContactRepository
import app.tfl.core.database.repository.IdentityRepository
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.AppSession
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.session.IdentityTools
import app.tfl.core.session.LockSettings
import app.tfl.core.session.OnboardingDraft
import app.tfl.core.session.PairingIdentity
import app.tfl.core.session.ProfileCreator
import app.tfl.core.session.TransportKeyring
import app.tfl.core.session.WipeController
import app.tfl.core.testing.crypto.FAST_KDF
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.crypto.FakeHardwareKeys
import app.tfl.core.testing.crypto.JvmSodium
import app.tfl.core.testing.database.PlainDatabaseFactory
import kotlinx.coroutines.Dispatchers
import java.io.File

/**
 * The real session stack over JVM libsodium, a fake Keystore and plain (unencrypted) Room files.
 * Two simulated phones in one test each take a [phone] name, so their lock states are kept apart
 * (database files already have random names), and can share one [clock].
 */
class SessionFixture(val context: Context, phone: String? = null, val clock: FakeDeviceClock = FakeDeviceClock()) {
    val sodium = JvmSodium.api
    val keys = FakeHardwareKeys()
    private val lockStateDir = phone?.let { File(context.noBackupFilesDir, it).apply { mkdirs() } } ?: context.noBackupFilesDir
    val vault = PinVault(sodium, SealedBox(sodium), Argon2idHasher(sodium), LockStateStore(lockStateDir, keys), keys, clock)
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
    val keyring = TransportKeyring()
    val wipe = WipeController(context, vault, database, disguise, keyring) { restarts++ }
    val session = AppSession(vault, database, settings, creator, { FAST_KDF }, sodium, wipe, tools, derivation, keyring, Dispatchers.Unconfined)
    val lockSettings = LockSettings(session, vault, settings, identities, creator, disguise, wipe, sodium, Dispatchers.Unconfined)
    val contacts = ContactRepository(database)
    val pairingCodes = PairingCodes(sodium, fingerprints)
    val safetyNumbers = SafetyNumbers(sodium)
    val pairing = PairingIdentity(session, vault, derivation, pairingCodes, identities, clock, sodium, Dispatchers.Unconfined)

    fun draft(
        seed: ByteArray,
        restored: Boolean = false,
        duressPin: String? = null,
        duressMode: DuressMode = DuressMode.NONE,
        displayName: String = "Valkyrie-7",
    ) = OnboardingDraft(
        seed = seed,
        restored = restored,
        displayName = displayName,
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
