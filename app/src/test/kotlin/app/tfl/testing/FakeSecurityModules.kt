package app.tfl.testing

import android.content.Context
import app.tfl.core.crypto.di.CryptoBindings
import app.tfl.core.crypto.di.CryptoModule
import app.tfl.core.crypto.inbox.LockedInboxStore
import app.tfl.core.crypto.keystore.HardwareKeys
import app.tfl.core.crypto.lock.Argon2idHasher
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.lock.LockStateStore
import app.tfl.core.crypto.lock.PasswordHasher
import app.tfl.core.crypto.lock.PinCostPolicy
import app.tfl.core.crypto.phrase.Bip39Wordlist
import app.tfl.core.crypto.sodium.SodiumApi
import app.tfl.core.database.DatabaseFactory
import app.tfl.core.database.di.DatabaseModule
import app.tfl.core.session.di.SessionBindings
import app.tfl.core.session.restart.ProcessRestarter
import app.tfl.core.testing.crypto.FAST_KDF
import app.tfl.core.testing.crypto.FakeDeviceClock
import app.tfl.core.testing.crypto.FakeHardwareKeys
import app.tfl.core.testing.crypto.JvmSodium
import app.tfl.core.testing.database.PlainDatabaseFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

/**
 * App tests run the real session on the JVM: libsodium from lazysodium-java, a fake Keystore,
 * unencrypted Room files (SQLCipher's native library can't load here) and a cheap Argon2id cost.
 */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [CryptoModule::class, CryptoBindings::class])
object FakeCryptoModule {
    @Provides
    @Singleton
    fun sodium(): SodiumApi = JvmSodium.api

    @Provides
    @Singleton
    fun wordlist(sodium: SodiumApi): Bip39Wordlist = Bip39Wordlist.load(sodium)

    @Provides
    @Singleton
    fun hardwareKeys(): HardwareKeys = FakeHardwareKeys()

    @Provides
    @Singleton
    fun lockStateStore(@ApplicationContext context: Context, keys: HardwareKeys): LockStateStore =
        LockStateStore(context.noBackupFilesDir, keys)

    @Provides
    @Singleton
    fun lockedInboxStore(@ApplicationContext context: Context, keys: HardwareKeys): LockedInboxStore =
        LockedInboxStore(context.noBackupFilesDir, keys)

    @Provides
    fun passwordHasher(sodium: SodiumApi): PasswordHasher = Argon2idHasher(sodium)

    @Provides
    @Singleton
    fun deviceClock(): DeviceClock = FakeDeviceClock()

    @Provides
    fun pinCost(): PinCostPolicy = PinCostPolicy { FAST_KDF }
}

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [DatabaseModule::class])
object FakeDatabaseModule {
    @Provides
    @Singleton
    fun databaseFactory(@ApplicationContext context: Context): DatabaseFactory = PlainDatabaseFactory(context)
}

/** A wipe must not kill the test JVM. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SessionBindings::class])
object NoRestartModule {
    @Provides
    fun processRestarter(): ProcessRestarter = ProcessRestarter {}
}
