package app.tfl.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.session.GateState
import app.tfl.feature.onboarding.finish.OnboardingFinish
import app.tfl.feature.onboarding.lock.KeysUnavailableRoute
import app.tfl.feature.onboarding.lock.LockRoute
import app.tfl.feature.onboarding.setup.OnboardingSetup

/**
 * Switches the whole window on the lock gate. The app shell exists only while unlocked, so locking
 * discards every screen along with the keys.
 */
@Composable
fun TflGate(gate: GateState, appVersion: String) {
    when (gate) {
        // The splash covers Starting; a wipe restarts the process within moments. Neither shows anything.
        GateState.Starting, GateState.Wiping -> Box(Modifier.fillMaxSize().background(TflTheme.colors.canvas))
        GateState.NeedsOnboarding -> OnboardingSetup()
        GateState.Locked -> LockRoute()
        GateState.KeysUnavailable -> KeysUnavailableRoute()
        is GateState.Unlocked -> if (gate.onboardingStep == OnboardingStep.DONE) {
            TflApp(appState = rememberTflAppState(), appVersion = appVersion)
        } else {
            OnboardingFinish(step = gate.onboardingStep)
        }
    }
}
