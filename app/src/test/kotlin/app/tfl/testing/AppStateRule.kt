package app.tfl.testing

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.AppSession
import app.tfl.core.session.OnboardingDraft
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/**
 * Puts TFL in [state] before the activity launches, so the gate opens straight on it. Order it after
 * HiltAndroidRule and before the compose rule.
 */
class AppStateRule(private val state: State) : ExternalResource() {

    enum class State {
        /** No identity yet: onboarding. */
        NEW_PHONE,

        /** Committed but stopped at the recovery phrase. */
        ONBOARDING,

        /** Set up and locked, as after a restart. */
        LOCKED,

        /** Set up and unlocked with onboarding finished: the app shell. */
        UNLOCKED,
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SessionEntryPoint {
        fun session(): AppSession
    }

    val session: AppSession by lazy {
        EntryPointAccessors.fromApplication(ApplicationProvider.getApplicationContext<Context>(), SessionEntryPoint::class.java).session()
    }

    override fun before() {
        if (state == State.NEW_PHONE) return
        runBlocking {
            session.commitOnboarding(
                OnboardingDraft(
                    seed = ByteArray(32) { it.toByte() },
                    restored = false,
                    displayName = DISPLAY_NAME,
                    pin = PIN.encodeToByteArray(),
                    duressPin = null,
                    duressMode = DuressMode.NONE,
                    autoLock = AutoLockTimeout.IMMEDIATELY,
                ),
            )
            when (state) {
                State.UNLOCKED -> session.advanceOnboarding(OnboardingStep.DONE)
                State.LOCKED -> {
                    session.advanceOnboarding(OnboardingStep.DONE)
                    session.lock()
                }
                State.ONBOARDING, State.NEW_PHONE -> Unit
            }
        }
    }

    companion object {
        const val DISPLAY_NAME = "Valkyrie-7"
        const val PIN = "246810"
    }
}
