package app.tfl

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.AppSession
import app.tfl.core.session.OnboardingDraft
import app.tfl.core.session.WipeController
import app.tfl.core.session.di.SessionBindings
import app.tfl.core.session.restart.ProcessRestarter
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** A wipe must not kill the instrumentation process. */
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [SessionBindings::class])
object NoRestartModule {
    @Provides
    fun processRestarter(): ProcessRestarter = ProcessRestarter {}
}

/**
 * Before the activity launches: wipes anything left by an earlier run, then creates and unlocks an
 * identity with the phone's real Keystore, SQLCipher and Argon2id (this takes a few seconds).
 */
class FreshIdentityRule : ExternalResource() {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun session(): AppSession
        fun wipeController(): WipeController
    }

    override fun before() {
        val dependencies = EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext<Context>(), Dependencies::class.java)
        dependencies.wipeController().wipeEverything()
        runBlocking {
            val session = dependencies.session()
            session.commitOnboarding(
                OnboardingDraft(
                    seed = ByteArray(32) { it.toByte() },
                    restored = false,
                    displayName = "Valkyrie-7",
                    pin = "246810".encodeToByteArray(),
                    duressPin = null,
                    duressMode = DuressMode.NONE,
                    autoLock = AutoLockTimeout.IMMEDIATELY,
                ),
            )
            session.advanceOnboarding(OnboardingStep.DONE)
        }
    }
}
