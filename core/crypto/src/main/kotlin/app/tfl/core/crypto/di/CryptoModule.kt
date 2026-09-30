package app.tfl.core.crypto.di

import android.content.Context
import app.tfl.core.crypto.keystore.AndroidHardwareKeys
import app.tfl.core.crypto.keystore.HardwareKeys
import app.tfl.core.crypto.lock.AndroidDeviceClock
import app.tfl.core.crypto.lock.Argon2idHasher
import app.tfl.core.crypto.lock.BenchmarkedPinCost
import app.tfl.core.crypto.lock.DeviceClock
import app.tfl.core.crypto.lock.LockStateStore
import app.tfl.core.crypto.lock.PasswordHasher
import app.tfl.core.crypto.lock.PinCostPolicy
import app.tfl.core.crypto.phrase.Bip39Wordlist
import app.tfl.core.crypto.sodium.SodiumApi
import com.goterl.lazysodium.SodiumAndroid
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CryptoModule {

    /** Loads the bundled libsodium on first use. */
    @Provides
    @Singleton
    fun sodium(): SodiumApi = SodiumApi(SodiumAndroid())

    @Provides
    @Singleton
    fun wordlist(sodium: SodiumApi): Bip39Wordlist = Bip39Wordlist.load(sodium)

    /** In `no_backup`: never part of any backup, whatever the backup rules say. */
    @Provides
    @Singleton
    fun lockStateStore(@ApplicationContext context: Context, keys: HardwareKeys): LockStateStore =
        LockStateStore(context.noBackupFilesDir, keys)
}

@Module
@InstallIn(SingletonComponent::class)
abstract class CryptoBindings {
    @Binds
    abstract fun hardwareKeys(impl: AndroidHardwareKeys): HardwareKeys

    @Binds
    abstract fun passwordHasher(impl: Argon2idHasher): PasswordHasher

    @Binds
    abstract fun deviceClock(impl: AndroidDeviceClock): DeviceClock

    @Binds
    abstract fun pinCostPolicy(impl: BenchmarkedPinCost): PinCostPolicy
}
