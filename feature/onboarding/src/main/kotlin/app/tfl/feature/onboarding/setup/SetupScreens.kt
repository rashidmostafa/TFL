package app.tfl.feature.onboarding.setup

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.ChoiceCard
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.IdentityGlyph
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.PinDots
import app.tfl.core.designsystem.component.PinPad
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.StatusPill
import app.tfl.core.designsystem.component.StepHeader
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflFilterChip
import app.tfl.core.designsystem.component.TflLogo
import app.tfl.core.designsystem.component.TflTextField
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.component.glow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.session.PinEntry
import app.tfl.feature.onboarding.OnboardingPage
import app.tfl.feature.onboarding.PageTitle
import app.tfl.feature.onboarding.R
import app.tfl.feature.onboarding.WordField
import app.tfl.feature.onboarding.WordSuggestions

internal const val CREATE_STEPS = 7
internal const val RESTORE_STEPS = 6

internal fun totalSteps(restored: Boolean) = if (restored) RESTORE_STEPS else CREATE_STEPS

@Composable
internal fun WelcomeScreen(onCreate: () -> Unit, onRestore: () -> Unit) {
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = null,
        bottom = {
            PrimaryButton(stringResource(R.string.welcome_create), onClick = onCreate, icon = MaterialSymbols.Fingerprint, glow = true, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onRestore) {
                TflIcon(MaterialSymbols.SettingsBackupRestore, contentDescription = null, size = 20.dp, tint = colors.mesh)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.welcome_restore), style = TflTheme.typography.labelLg, color = colors.mesh)
            }
            Text(stringResource(R.string.welcome_footer), style = TflTheme.typography.codeSm, color = colors.textDim, textAlign = TextAlign.Center)
        },
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            StatusPill(stringResource(R.string.welcome_status), modifier = Modifier.padding(top = 8.dp))
        }
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(132.dp)
                    .glow(colors.primary, CircleShape, radius = 28.dp, alpha = 0.25f)
                    .background(colors.surface, CircleShape)
                    .border(1.dp, colors.primary.copy(alpha = 0.3f), CircleShape),
                contentAlignment = Alignment.Center,
            ) { TflLogo(size = 96.dp) }
        }
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("TFL", style = TflTheme.typography.headlineLg, color = colors.textPrimary)
            Text(stringResource(R.string.welcome_tagline), style = TflTheme.typography.headlineSm, color = colors.textMuted)
        }
        FeatureRow(MaterialSymbols.Memory, R.string.welcome_keys_title, R.string.welcome_keys_body, colors.primary)
        FeatureRow(MaterialSymbols.Hub, R.string.welcome_mesh_title, R.string.welcome_mesh_body, colors.mesh)
        FeatureRow(MaterialSymbols.VpnLock, R.string.welcome_tor_title, R.string.welcome_tor_body, colors.tor)
        FeatureRow(MaterialSymbols.NoAccounts, R.string.welcome_accounts_title, R.string.welcome_accounts_body, colors.primary)
    }
}

@Composable
private fun FeatureRow(icon: String, title: Int, body: Int, tint: androidx.compose.ui.graphics.Color) {
    TflCard(contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(icon, tint = tint)
            Column(Modifier.weight(1f)) {
                Text(stringResource(title), style = TflTheme.typography.labelLg, color = TflTheme.colors.textPrimary)
                Text(stringResource(body), style = TflTheme.typography.bodyMd, color = TflTheme.colors.textMuted)
            }
        }
    }
}

@Composable
internal fun IdentityScreen(
    state: SetupUiState,
    displayName: TextFieldState,
    onRegenerate: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = onBack,
        bottom = {
            PrimaryButton(stringResource(R.string.onboarding_continue), onClick = onContinue, icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth())
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_identity), 1, totalSteps(state.restored))
        PageTitle(
            title = stringResource(if (state.restored) R.string.identity_title_restored else R.string.identity_title),
            body = stringResource(R.string.identity_body),
        )
        if (!state.restored) {
            Callout(stringResource(R.string.identity_callout_body), title = stringResource(R.string.identity_callout_title))
        }
        TflCard(verticalSpacing = 12.dp) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                IdentityGlyph(state.fingerprint, size = 150.dp)
                if (!state.restored) {
                    PillButton(stringResource(R.string.identity_regenerate), onClick = onRegenerate, icon = MaterialSymbols.Casino, contentColor = colors.textPrimary)
                }
            }
        }
        Text(stringResource(R.string.identity_name_label), style = TflTheme.typography.labelLg, color = colors.textPrimary)
        TflTextField(
            state = displayName,
            placeholder = stringResource(R.string.identity_name_placeholder),
            leadingIcon = MaterialSymbols.Badge,
            supportingText = stringResource(if (state.nameError) R.string.identity_name_error else R.string.identity_name_help),
            isError = state.nameError,
        )
        FingerprintBlock(state.fingerprint.chunked(4), label = stringResource(R.string.identity_fingerprint))
        Text(
            stringResource(if (state.restored) R.string.identity_restored_note else R.string.identity_generated),
            style = TflTheme.typography.codeSm,
            color = colors.textMuted,
        )
    }
}

@Composable
internal fun RestoreScreen(
    state: SetupUiState,
    words: List<TextFieldState>,
    suggestions: (String) -> List<String>,
    isWord: (String) -> Boolean,
    onSubmit: () -> Unit,
    onBack: () -> Unit,
) {
    val focusRequesters = remember { List(words.size) { FocusRequester() } }
    var focused by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    val unknown = (state.restoreError as? RestoreError.UnknownWord)?.number
    fun moveTo(index: Int) {
        if (index < words.size) focusRequesters[index].requestFocus() else focusManager.clearFocus()
    }
    OnboardingPage(
        onBack = onBack,
        bottom = {
            WordSuggestions(words[focused].text.toString(), suggestions, isWord, onPick = { word ->
                words[focused].setTextAndPlaceCursorAtEnd(word)
                moveTo(focused + 1)
            })
            when (val error = state.restoreError) {
                is RestoreError.UnknownWord -> Callout(stringResource(R.string.restore_error_unknown, error.number), tone = CalloutTone.Danger)
                RestoreError.Checksum -> Callout(stringResource(R.string.restore_error_checksum), tone = CalloutTone.Danger)
                RestoreError.Incomplete -> Callout(stringResource(R.string.restore_error_incomplete), tone = CalloutTone.Warning)
                null -> Unit
            }
            PrimaryButton(stringResource(R.string.restore_submit), onClick = onSubmit, icon = MaterialSymbols.SettingsBackupRestore, modifier = Modifier.fillMaxWidth())
        },
    ) {
        PageTitle(stringResource(R.string.restore_title), stringResource(R.string.restore_body))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(0 until 12, 12 until 24).forEach { range ->
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    range.forEach { index ->
                        WordField(
                            state = words[index],
                            number = index + 1,
                            focusRequester = focusRequesters[index],
                            onFocused = { focused = index },
                            onNext = { moveTo(index + 1) },
                            isError = unknown == index + 1,
                            isLast = index == words.lastIndex,
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun AppLockSetupScreen(
    state: SetupUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onReset: () -> Unit,
    onBiometric: (Boolean) -> Unit,
    onAutoLock: (AutoLockTimeout) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = TflTheme.colors
    val complete = state.pin.stage == PinEntry.Stage.COMPLETE
    OnboardingPage(
        onBack = onBack,
        bottom = {
            PrimaryButton(
                stringResource(R.string.applock_continue), onClick = onContinue, enabled = complete,
                icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth(),
            )
            if (complete || state.pin.stage == PinEntry.Stage.CONFIRM) {
                TextButton(onClick = onReset) { Text(stringResource(R.string.applock_reset), color = colors.textMuted) }
            }
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_security), 2, totalSteps(state.restored))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(MaterialSymbols.ShieldLock, size = 64.dp, iconSize = 32.dp)
            Text(
                stringResource(
                    when {
                        complete -> R.string.applock_done_title
                        state.pin.stage == PinEntry.Stage.CONFIRM -> R.string.applock_confirm_title
                        else -> R.string.applock_title
                    },
                ),
                style = TflTheme.typography.headlineMd, color = colors.textPrimary, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (complete) R.string.applock_done_body else R.string.applock_body),
                style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center,
            )
            PinDots(entered = if (complete) 6 else state.pin.entered, error = state.pin.mismatch)
            if (state.pin.mismatch) Text(stringResource(R.string.applock_mismatch), style = TflTheme.typography.bodySm, color = colors.danger)
        }
        // The pad while typing, so it fits on short screens; the lock options once the PIN is set.
        if (complete) {
            TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
                ToggleRow(
                    title = stringResource(R.string.applock_biometric_title),
                    subtitle = stringResource(if (state.biometricAvailable) R.string.applock_biometric_body else R.string.applock_biometric_unavailable),
                    icon = MaterialSymbols.Fingerprint,
                    checked = state.biometricWanted,
                    onCheckedChange = onBiometric,
                    enabled = state.biometricAvailable,
                )
                TflDivider()
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.applock_autolock), style = TflTheme.typography.labelLg, color = colors.textPrimary)
                    AutoLockChips(state.autoLock, onAutoLock)
                }
            }
        } else {
            PinPad(onDigit = onDigit, onDelete = onDelete)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AutoLockChips(selected: AutoLockTimeout, onSelect: (AutoLockTimeout) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        AutoLockTimeout.entries.forEach { timeout ->
            TflFilterChip(label = stringResource(timeout.label()), selected = timeout == selected, onClick = { onSelect(timeout) })
        }
    }
}

internal fun AutoLockTimeout.label(): Int = when (this) {
    AutoLockTimeout.IMMEDIATELY -> R.string.onboarding_autolock_immediately
    AutoLockTimeout.SECONDS_30 -> R.string.onboarding_autolock_30s
    AutoLockTimeout.MINUTE_1 -> R.string.onboarding_autolock_1m
    AutoLockTimeout.MINUTES_5 -> R.string.onboarding_autolock_5m
}

/**
 * Two parts, so the PIN pad always fits on screen: what the duress PIN does, then the PIN itself.
 */
@Composable
internal fun DuressSetupScreen(
    state: SetupUiState,
    onStartPin: () -> Unit,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onMode: (DuressMode) -> Unit,
    onConfirm: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
) {
    if (state.duressEntering) {
        DuressPinEntry(state, onDigit, onDelete, onConfirm, onBack)
        return
    }
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = onBack,
        bottom = {
            PrimaryButton(
                stringResource(R.string.duress_choose_pin), onClick = onStartPin,
                icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth(),
            )
            TextButton(onClick = onSkip) { Text(stringResource(R.string.duress_skip), color = colors.textMuted) }
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_duress), 3, totalSteps(state.restored))
        PageTitle(stringResource(R.string.duress_title), stringResource(R.string.duress_body))
        Text(stringResource(R.string.duress_mode_label), style = TflTheme.typography.labelLg, color = colors.textPrimary)
        ChoiceCard(
            title = stringResource(R.string.duress_decoy_title),
            tag = stringResource(R.string.duress_decoy_tag),
            body = stringResource(R.string.duress_decoy_body),
            selected = state.duressMode == DuressMode.DECOY,
            onSelect = { onMode(DuressMode.DECOY) },
            icon = MaterialSymbols.VisibilityOff,
        )
        ChoiceCard(
            title = stringResource(R.string.duress_wipe_title),
            tag = stringResource(R.string.duress_wipe_tag),
            body = stringResource(R.string.duress_wipe_body),
            selected = state.duressMode == DuressMode.WIPE,
            onSelect = { onMode(DuressMode.WIPE) },
            icon = MaterialSymbols.DeleteForever,
            accent = colors.danger,
        )
        if (state.biometricWanted) Callout(stringResource(R.string.duress_biometric_note), tone = CalloutTone.Warning)
    }
}

@Composable
private fun DuressPinEntry(
    state: SetupUiState,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onConfirm: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = TflTheme.colors
    val complete = state.duressPin.stage == PinEntry.Stage.COMPLETE
    val wipes = state.duressMode == DuressMode.WIPE
    OnboardingPage(
        onBack = onBack,
        bottom = {
            PrimaryButton(
                stringResource(R.string.duress_confirm), onClick = onConfirm, enabled = complete,
                icon = MaterialSymbols.ShieldLock, modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_duress), 3, totalSteps(state.restored))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(
                if (wipes) MaterialSymbols.DeleteForever else MaterialSymbols.VisibilityOff,
                tint = if (wipes) colors.danger else colors.primary,
                size = 64.dp, iconSize = 32.dp,
            )
            Text(
                stringResource(if (state.duressPin.stage == PinEntry.Stage.CONFIRM) R.string.duress_confirm_label else R.string.duress_pin_label),
                style = TflTheme.typography.headlineMd, color = colors.textPrimary, textAlign = TextAlign.Center,
            )
            Text(
                stringResource(if (wipes) R.string.duress_entry_wipes else R.string.duress_entry_decoy),
                style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.duress_pin_differs), style = TflTheme.typography.labelSm, color = colors.danger)
            PinDots(entered = if (complete) 6 else state.duressPin.entered, error = state.duressPin.mismatch || state.duressSameAsPin)
            when {
                state.duressSameAsPin -> Text(stringResource(R.string.duress_same_as_pin), style = TflTheme.typography.bodySm, color = colors.danger)
                state.duressPin.mismatch -> Text(stringResource(R.string.applock_mismatch), style = TflTheme.typography.bodySm, color = colors.danger)
            }
        }
        if (!complete) PinPad(onDigit = onDigit, onDelete = onDelete)
    }
}

@Composable
internal fun CommitScreen(failed: Boolean, onRetry: () -> Unit) {
    val colors = TflTheme.colors
    OnboardingPage(onBack = null) {
        Spacer(Modifier.height(64.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
            TflLogo(size = 88.dp)
            if (!failed) CircularProgressIndicator(color = colors.primary, trackColor = colors.surfaceContainer)
            Text(stringResource(R.string.commit_title), style = TflTheme.typography.headlineMd, color = colors.textPrimary)
            Text(stringResource(R.string.commit_body), style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center)
            if (failed) {
                Callout(stringResource(R.string.commit_failed), tone = CalloutTone.Danger)
                PrimaryButton(stringResource(R.string.commit_retry), onClick = onRetry, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
