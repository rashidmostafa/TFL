package app.tfl.feature.onboarding.lock

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.PinDots
import app.tfl.core.designsystem.component.PinPad
import app.tfl.core.designsystem.component.PinPadIconKey
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflLogo
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.glow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.session.biometric.BiometricAuth
import app.tfl.feature.onboarding.R
import kotlinx.coroutines.launch

/** Shown on every cold start and after auto-lock. */
@Composable
fun LockRoute(viewModel: LockViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.lock_biometric_title)
    val usePin = stringResource(R.string.lock_use_pin)

    fun promptBiometric() {
        if (activity == null) return
        scope.launch {
            val cipher = viewModel.biometricCipher() ?: return@launch
            viewModel.onBiometricOutcome(BiometricAuth.authenticate(activity, title, subtitle = null, cancelLabel = usePin, cipher = cipher))
        }
    }

    LaunchedEffect(viewModel) { viewModel.onShown() }
    LaunchedEffect(state.biometricAutoPrompt) { if (state.biometricAutoPrompt) promptBiometric() }

    LockScreen(
        state = state,
        onDigit = viewModel::digit,
        onDelete = viewModel::delete,
        onBiometric = ::promptBiometric,
        onForgot = { viewModel.showForgot(true) },
    )
    if (state.forgotDialog) {
        ForgotPinSheet(onDismiss = { viewModel.showForgot(false) }, onWipe = viewModel::wipe)
    }
}

@Composable
internal fun LockScreen(
    state: LockUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onBiometric: () -> Unit,
    onForgot: () -> Unit,
) {
    val colors = TflTheme.colors
    val fingerprint = stringResource(R.string.lock_biometric)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Spacer(Modifier.heightIn(min = 16.dp))
        Box(
            Modifier
                .size(112.dp)
                .glow(colors.primary, CircleShape, radius = 24.dp, alpha = 0.2f)
                .background(colors.surface, CircleShape)
                .border(1.dp, colors.primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) { TflLogo(size = 80.dp) }
        Text(stringResource(R.string.lock_title), style = TflTheme.typography.headlineMd, color = colors.textPrimary)
        PinDots(entered = state.entered, error = state.status is LockStatus.WrongPin || state.status is LockStatus.Failed)
        Text(
            text = statusText(state.status),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 40.dp)
                .semantics { liveRegion = LiveRegionMode.Polite },
            style = TflTheme.typography.bodyMd,
            color = if (state.status is LockStatus.Checking) colors.textMuted else colors.danger,
            textAlign = TextAlign.Center,
        )
        PinPad(
            onDigit = onDigit,
            onDelete = onDelete,
            enabled = state.canType,
            bottomStart = if (state.biometricEnabled) {
                { PinPadIconKey(MaterialSymbols.Fingerprint, fingerprint, onClick = onBiometric, enabled = state.canType) }
            } else {
                null
            },
        )
        TextButton(onClick = onForgot) {
            Text(stringResource(R.string.lock_forgot), style = TflTheme.typography.labelLg, color = colors.textMuted)
        }
    }
}

@Composable
private fun statusText(status: LockStatus): String = when (status) {
    LockStatus.Idle -> ""
    LockStatus.Checking -> stringResource(R.string.lock_checking)
    // The wipe warning appears once few attempts remain; before that, a wrong PIN is just wrong.
    is LockStatus.WrongPin -> status.attemptsBeforeWipe?.takeIf { it <= WIPE_WARNING_ATTEMPTS }?.let {
        pluralStringResource(R.plurals.lock_attempts_before_wipe, it, it)
    } ?: stringResource(R.string.lock_wrong)
    is LockStatus.Waiting -> stringResource(R.string.lock_wait, formatWait(status.remainingMillis))
    LockStatus.Failed -> stringResource(R.string.lock_failed)
}

private const val WIPE_WARNING_ATTEMPTS = 3

/** m:ss, rounded up so "0:00" never shows while still waiting. */
internal fun formatWait(millis: Long): String {
    val seconds = (millis + 999) / 1000
    return "%d:%02d".format(seconds / 60, seconds % 60)
}

@Composable
private fun ForgotPinSheet(onDismiss: () -> Unit, onWipe: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.lock_forgot_title), icon = MaterialSymbols.LockReset)
        Text(stringResource(R.string.lock_forgot_body), style = TflTheme.typography.bodyMd, color = TflTheme.colors.textMuted)
        DestructiveButton(stringResource(R.string.lock_forgot_confirm), onClick = onWipe, icon = MaterialSymbols.DeleteForever, modifier = Modifier.fillMaxWidth())
        GhostButton(stringResource(R.string.lock_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
    }
}
