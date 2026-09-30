package app.tfl.feature.onboarding.finish

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.TflSnackbarHost
import app.tfl.core.model.identity.OnboardingStep
import app.tfl.core.session.biometric.BiometricAuth
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.feature.onboarding.R

/** Onboarding after the commit point, at the [step] the gate says it has reached. */
@Composable
fun OnboardingFinish(step: OnboardingStep, viewModel: FinishViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val snackbar = remember { SnackbarHostState() }
    val enrolTitle = stringResource(R.string.biometric_enrol_title)
    val enrolSubtitle = stringResource(R.string.biometric_enrol_subtitle)
    val enrolCancel = stringResource(R.string.biometric_cancel)
    val enrolFailed = stringResource(R.string.biometric_enrol_failed)

    LaunchedEffect(step) { viewModel.onStep(step) }
    LaunchedEffect(state.biometricPending, activity) {
        if (!state.biometricPending || activity == null) return@LaunchedEffect
        val cipher = viewModel.biometricEnrolCipher()
        val outcome = if (cipher == null) {
            BiometricOutcome.Failed("")
        } else {
            BiometricAuth.authenticate(activity, enrolTitle, enrolSubtitle, enrolCancel, cipher)
        }
        viewModel.onBiometricOutcome(outcome)
    }
    LaunchedEffect(state.biometricNotice) {
        if (state.biometricNotice) {
            viewModel.biometricNoticeShown()
            snackbar.showSnackbar(enrolFailed)
        }
    }
    val canGoBack = step == OnboardingStep.NETWORK || step == OnboardingStep.DISGUISE ||
        (step == OnboardingStep.RECOVERY_PHRASE && state.phraseStage == PhraseStage.QUIZ)
    BackHandler(enabled = canGoBack) { viewModel.back(step) }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = step,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboarding-finish",
        ) { shown ->
            when (shown) {
                OnboardingStep.RECOVERY_PHRASE -> when (state.phraseStage) {
                    PhraseStage.WORDS -> RecoveryPhraseScreen(
                        state = state,
                        onToggleHidden = viewModel::togglePhraseHidden,
                        onWrittenDown = viewModel::setWrittenDown,
                        onVerify = viewModel::startQuiz,
                    )
                    PhraseStage.QUIZ -> PhraseQuizScreen(
                        state = state,
                        words = viewModel.quizWords,
                        suggestions = viewModel::suggestions,
                        isWord = viewModel::isWord,
                        onSubmit = viewModel::submitQuiz,
                        onShowAgain = viewModel::showPhraseAgain,
                    )
                }
                OnboardingStep.PERMISSIONS -> PermissionsScreen(state = state, onContinue = viewModel::continueFromPermissions)
                OnboardingStep.NETWORK -> NetworkScreen(
                    state = state,
                    onNearby = viewModel::setNearby,
                    onTor = viewModel::setTor,
                    onBridge = viewModel::setBridge,
                    onContinue = viewModel::continueFromNetwork,
                    onBack = { viewModel.back(OnboardingStep.NETWORK) },
                )
                OnboardingStep.DISGUISE -> DisguiseScreen(
                    state = state,
                    onWanted = viewModel::setDisguiseWanted,
                    onDigit = viewModel::disguiseDigit,
                    onDelete = viewModel::disguiseDelete,
                    onResetCode = viewModel::resetDisguiseCode,
                    onFinish = viewModel::finish,
                    onBack = { viewModel.back(OnboardingStep.DISGUISE) },
                )
                OnboardingStep.DONE -> Unit
            }
        }
        TflSnackbarHost(
            snackbar,
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing),
        )
    }
}
