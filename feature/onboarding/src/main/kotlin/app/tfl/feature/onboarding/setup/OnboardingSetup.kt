package app.tfl.feature.onboarding.setup

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** Onboarding up to the commit point: welcome → identity (or restore) → PIN → duress PIN → commit. */
@Composable
fun OnboardingSetup(viewModel: SetupViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    BackHandler(enabled = state.step != SetupStep.WELCOME) { viewModel.back() }
    AnimatedContent(
        targetState = state.step,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding-setup",
    ) { step ->
        when (step) {
            SetupStep.WELCOME -> WelcomeScreen(onCreate = viewModel::createIdentity, onRestore = viewModel::startRestore)
            SetupStep.IDENTITY -> IdentityScreen(
                state = state,
                displayName = viewModel.displayName,
                onRegenerate = viewModel::regenerate,
                onContinue = viewModel::continueFromIdentity,
                onBack = { viewModel.back() },
            )
            SetupStep.RESTORE -> RestoreScreen(
                state = state,
                words = viewModel.restoreWords,
                suggestions = viewModel::suggestions,
                isWord = viewModel::isWord,
                onSubmit = viewModel::submitRestore,
                onBack = { viewModel.back() },
            )
            SetupStep.APP_LOCK -> AppLockSetupScreen(
                state = state,
                onDigit = viewModel::pinDigit,
                onDelete = viewModel::pinDelete,
                onReset = viewModel::resetPin,
                onBiometric = viewModel::setBiometric,
                onAutoLock = viewModel::setAutoLock,
                onContinue = viewModel::continueFromAppLock,
                onBack = { viewModel.back() },
            )
            SetupStep.DURESS -> DuressSetupScreen(
                state = state,
                onStartPin = viewModel::startDuressPin,
                onDigit = viewModel::duressDigit,
                onDelete = viewModel::duressDelete,
                onMode = viewModel::setDuressMode,
                onConfirm = viewModel::confirmDuress,
                onSkip = viewModel::skipDuress,
                onBack = { viewModel.back() },
            )
            SetupStep.COMMITTING -> CommitScreen(failed = state.commitFailed, onRetry = viewModel::retryCommit)
        }
    }
}
