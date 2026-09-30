package app.tfl.feature.onboarding.finish

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.PhraseWordGrid
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.PinDots
import app.tfl.core.designsystem.component.PinPad
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.RadioRow
import app.tfl.core.designsystem.component.StepHeader
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflSwitch
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.network.BridgeMode
import app.tfl.core.session.PinEntry
import app.tfl.feature.onboarding.OnboardingPage
import app.tfl.feature.onboarding.PageTitle
import app.tfl.feature.onboarding.R
import app.tfl.feature.onboarding.WordField
import app.tfl.feature.onboarding.WordSuggestions
import app.tfl.feature.onboarding.setup.CREATE_STEPS
import app.tfl.feature.onboarding.setup.totalSteps
import app.tfl.feature.onboarding.stealth.CalculatorDisplay
import app.tfl.feature.onboarding.stealth.CalculatorKey
import app.tfl.feature.onboarding.stealth.CalculatorKeys
import app.tfl.feature.onboarding.stealth.CalculatorReducer
import app.tfl.feature.onboarding.stealth.CalculatorUiState

/** Step numbers after the commit point; a restored identity has no phrase step. */
private fun FinishUiState.stepNumber(createStep: Int) = if (restored) createStep - 1 else createStep

@Composable
internal fun RecoveryPhraseScreen(
    state: FinishUiState,
    onToggleHidden: () -> Unit,
    onWrittenDown: (Boolean) -> Unit,
    onVerify: () -> Unit,
) {
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = null,
        bottom = {
            PrimaryButton(
                stringResource(R.string.phrase_verify), onClick = onVerify,
                enabled = state.writtenDown && state.phrase.isNotEmpty(),
                icon = MaterialSymbols.ArrowForward, glow = true, modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_phrase), 4, CREATE_STEPS)
        PageTitle(stringResource(R.string.phrase_title))
        Callout(stringResource(R.string.phrase_warning_body), title = stringResource(R.string.phrase_warning_title), tone = CalloutTone.Danger)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.phrase_words_label), Modifier.weight(1f), style = TflTheme.typography.headlineSm, color = colors.textPrimary)
            PillButton(
                stringResource(if (state.phraseHidden) R.string.phrase_show else R.string.phrase_hide),
                onClick = onToggleHidden,
                icon = if (state.phraseHidden) MaterialSymbols.Visibility else MaterialSymbols.VisibilityOff,
                contentColor = colors.textPrimary,
            )
        }
        if (state.phrase.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = colors.primary, trackColor = colors.surfaceContainer)
            }
        } else {
            PhraseWordGrid(state.phrase, hidden = state.phraseHidden)
        }
        Text(stringResource(R.string.phrase_standard), style = TflTheme.typography.codeSm, color = colors.textMuted)
        TflCard(contentPadding = PaddingValues(14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                IconTile(MaterialSymbols.EditNote, tint = colors.mesh)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.phrase_paper_title), style = TflTheme.typography.labelLg, color = colors.textPrimary)
                    Text(stringResource(R.string.phrase_paper_body), style = TflTheme.typography.bodyMd, color = colors.textMuted)
                }
            }
        }
        CheckCard(stringResource(R.string.phrase_written), checked = state.writtenDown, onCheckedChange = onWrittenDown)
    }
}

@Composable
private fun CheckCard(text: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = TflTheme.colors
    TflCard(
        modifier = Modifier.toggleable(value = checked, role = Role.Checkbox, onValueChange = onCheckedChange),
        borderColor = if (checked) colors.primary else colors.border,
        contentPadding = PaddingValues(14.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            TflIcon(
                if (checked) MaterialSymbols.CheckBox else MaterialSymbols.CheckBoxOutlineBlank,
                contentDescription = null, size = 28.dp,
                tint = if (checked) colors.primary else colors.textMuted,
                filled = checked,
            )
            Text(text, Modifier.weight(1f), style = TflTheme.typography.labelLg, color = colors.textPrimary)
        }
    }
}

@Composable
internal fun PhraseQuizScreen(
    state: FinishUiState,
    words: List<TextFieldState>,
    suggestions: (String) -> List<String>,
    isWord: (String) -> Boolean,
    onSubmit: () -> Unit,
    onShowAgain: () -> Unit,
) {
    val focusRequesters = remember { List(words.size) { FocusRequester() } }
    var focused by remember { mutableIntStateOf(0) }
    val focusManager = LocalFocusManager.current
    fun moveTo(index: Int) {
        if (index < words.size) focusRequesters[index].requestFocus() else focusManager.clearFocus()
    }
    OnboardingPage(
        onBack = onShowAgain,
        bottom = {
            WordSuggestions(words[focused].text.toString(), suggestions, isWord, onPick = { word ->
                words[focused].setTextAndPlaceCursorAtEnd(word)
                moveTo(focused + 1)
            })
            state.quizWrong?.let { Callout(stringResource(R.string.quiz_wrong, it), tone = CalloutTone.Danger) }
            PrimaryButton(stringResource(R.string.quiz_submit), onClick = onSubmit, enabled = !state.busy, icon = MaterialSymbols.Check, modifier = Modifier.fillMaxWidth())
            TextButton(onClick = onShowAgain) { Text(stringResource(R.string.quiz_show_again), color = TflTheme.colors.textMuted) }
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_phrase), 4, CREATE_STEPS)
        PageTitle(stringResource(R.string.quiz_title), stringResource(R.string.quiz_body))
        state.quizPositions.forEachIndexed { index, position ->
            WordField(
                state = words[index],
                number = position + 1,
                focusRequester = focusRequesters[index],
                onFocused = { focused = index },
                onNext = { moveTo(index + 1) },
                isError = state.quizWrong == position + 1,
                isLast = index == state.quizPositions.lastIndex,
            )
        }
    }
}

@Composable
internal fun PermissionsScreen(
    state: FinishUiState,
    onContinue: () -> Unit,
    showLocation: Boolean = Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2,
) {
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = null,
        bottom = {
            PrimaryButton(
                stringResource(R.string.onboarding_continue), onClick = onContinue, enabled = !state.busy,
                icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_permissions), state.stepNumber(5), totalSteps(state.restored))
        PageTitle(stringResource(R.string.permissions_title), stringResource(R.string.permissions_body))
        PermissionCard(MaterialSymbols.Bluetooth, R.string.permission_nearby_title, R.string.permission_nearby_body, colors.mesh)
        if (showLocation) PermissionCard(MaterialSymbols.NearMe, R.string.permission_location_title, R.string.permission_location_body, colors.warning)
        PermissionCard(MaterialSymbols.Notifications, R.string.permission_notifications_title, R.string.permission_notifications_body, colors.primary)
        PermissionCard(MaterialSymbols.BatteryChargingFull, R.string.permission_battery_title, R.string.permission_battery_body, colors.warning)
        PermissionCard(MaterialSymbols.PhotoCamera, R.string.permission_camera_title, R.string.permission_camera_body, colors.tor)
        Callout(stringResource(R.string.permissions_zero_telemetry), tone = CalloutTone.Success)
    }
}

@Composable
private fun PermissionCard(icon: String, title: Int, body: Int, tint: Color) {
    val colors = TflTheme.colors
    TflCard(contentPadding = PaddingValues(14.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            IconTile(icon, tint = tint)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(title), style = TflTheme.typography.labelLg, color = colors.textPrimary)
                Text(stringResource(body), style = TflTheme.typography.bodyMd, color = colors.textMuted)
                Tag(stringResource(R.string.permission_later), color = colors.textMuted)
            }
        }
    }
}

@Composable
internal fun NetworkScreen(
    state: FinishUiState,
    onNearby: (Boolean) -> Unit,
    onTor: (Boolean) -> Unit,
    onBridge: (BridgeMode) -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = onBack,
        bottom = {
            PrimaryButton(
                stringResource(R.string.onboarding_continue), onClick = onContinue, enabled = !state.busy,
                icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_network), state.stepNumber(6), totalSteps(state.restored))
        PageTitle(stringResource(R.string.onboarding_network_title), stringResource(R.string.onboarding_network_body))
        Tag(stringResource(R.string.onboarding_network_later), color = colors.warning)
        TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            SwitchBlock(MaterialSymbols.Hub, R.string.onboarding_network_mesh_title, R.string.onboarding_network_mesh_body, colors.mesh, state.nearbyEnabled, onNearby)
        }
        TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            SwitchBlock(MaterialSymbols.VpnLock, R.string.onboarding_network_tor_title, R.string.onboarding_network_tor_body, colors.tor, state.torEnabled, onTor)
            if (state.torEnabled) {
                TflDivider()
                Column(Modifier.padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.onboarding_network_bridge_label),
                        Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                        style = TflTheme.typography.labelLg, color = colors.textPrimary,
                    )
                    BridgeMode.entries.forEach { mode ->
                        val (title, body) = when (mode) {
                            BridgeMode.DIRECT -> R.string.onboarding_network_bridge_direct to R.string.onboarding_network_bridge_direct_body
                            BridgeMode.OBFS4 -> R.string.onboarding_network_bridge_obfs4 to R.string.onboarding_network_bridge_obfs4_body
                            BridgeMode.SNOWFLAKE -> R.string.onboarding_network_bridge_snowflake to R.string.onboarding_network_bridge_snowflake_body
                        }
                        RadioRow(stringResource(title), selected = state.bridgeMode == mode, onSelect = { onBridge(mode) }, subtitle = stringResource(body))
                    }
                }
            }
        }
    }
}

/** An icon, a title with a paragraph (not cut to two lines like a settings row), and a switch. */
@Composable
private fun SwitchBlock(icon: String, title: Int, body: Int, tint: Color, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val colors = TflTheme.colors
    Row(
        modifier = Modifier
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        IconTile(icon, tint = tint)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(stringResource(title), style = TflTheme.typography.labelLg, color = colors.textPrimary)
            Text(stringResource(body), style = TflTheme.typography.bodyMd, color = colors.textMuted)
        }
        TflSwitch(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun DisguiseScreen(
    state: FinishUiState,
    onWanted: (Boolean) -> Unit,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onResetCode: () -> Unit,
    onFinish: () -> Unit,
    onBack: () -> Unit,
) {
    val colors = TflTheme.colors
    val code = state.disguiseCode
    val complete = code.stage == PinEntry.Stage.COMPLETE
    if (state.disguiseWanted && !complete) {
        DisguiseCodeEntry(state, onDigit, onDelete, onCancel = { onWanted(false) })
        return
    }
    var preview by rememberSaveable { mutableStateOf(false) }
    OnboardingPage(
        onBack = onBack,
        bottom = {
            if (state.disguiseWanted) Callout(stringResource(R.string.disguise_closes_note), tone = CalloutTone.Warning)
            PrimaryButton(
                stringResource(R.string.disguise_finish), onClick = onFinish, enabled = !state.busy,
                icon = MaterialSymbols.Check, glow = true, modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_disguise), state.stepNumber(7), totalSteps(state.restored))
        PageTitle(stringResource(R.string.disguise_title), stringResource(R.string.disguise_body))
        PillButton(
            stringResource(if (preview) R.string.disguise_preview_hide else R.string.disguise_preview),
            onClick = { preview = !preview },
            icon = MaterialSymbols.Calculate,
            contentColor = colors.mesh,
        )
        if (preview) CalculatorPreview()
        TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) {
            SwitchBlock(MaterialSymbols.Calculate, R.string.disguise_toggle, R.string.disguise_code_help, colors.mesh, state.disguiseWanted, onWanted)
        }
        if (state.disguiseWanted) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                TflIcon(MaterialSymbols.CheckCircle, contentDescription = null, size = 22.dp, tint = colors.success)
                Text(stringResource(R.string.disguise_code_set), Modifier.weight(1f), style = TflTheme.typography.labelLg, color = colors.textPrimary)
                TextButton(onClick = onResetCode) { Text(stringResource(R.string.disguise_code_change), color = colors.textMuted) }
            }
        }
    }
}

/** The secret code on a screen of its own, so the pad fits; back cancels the disguise. */
@Composable
private fun DisguiseCodeEntry(state: FinishUiState, onDigit: (Int) -> Unit, onDelete: () -> Unit, onCancel: () -> Unit) {
    val colors = TflTheme.colors
    val code = state.disguiseCode
    OnboardingPage(
        onBack = onCancel,
        bottom = {
            TextButton(onClick = onCancel) { Text(stringResource(R.string.disguise_cancel), color = colors.textMuted) }
        },
    ) {
        StepHeader(stringResource(R.string.onboarding_step_disguise), state.stepNumber(7), totalSteps(state.restored))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            IconTile(MaterialSymbols.Calculate, tint = colors.mesh, size = 64.dp, iconSize = 32.dp)
            Text(
                stringResource(if (code.stage == PinEntry.Stage.CONFIRM) R.string.disguise_code_confirm else R.string.disguise_code_label),
                style = TflTheme.typography.headlineMd, color = colors.textPrimary, textAlign = TextAlign.Center,
            )
            Text(stringResource(R.string.disguise_code_help), style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center)
            PinDots(entered = code.entered, error = code.mismatch || state.disguiseCodeIsPin)
            when {
                code.mismatch -> Text(stringResource(R.string.disguise_code_mismatch), style = TflTheme.typography.bodySm, color = colors.danger)
                state.disguiseCodeIsPin -> Text(stringResource(R.string.disguise_code_is_pin), style = TflTheme.typography.bodySm, color = colors.danger)
            }
        }
        PinPad(onDigit = onDigit, onDelete = onDelete)
    }
}

/** A working calculator, exactly as the disguise shows it. Its "=" only ever calculates. */
@Composable
private fun CalculatorPreview() {
    var calculator by remember { mutableStateOf(CalculatorUiState()) }
    TflCard(containerColor = TflTheme.colors.canvas, verticalSpacing = 12.dp) {
        CalculatorDisplay(calculator, onDelete = { calculator = CalculatorReducer.press(calculator, CalculatorKey.Delete) })
        CalculatorKeys(
            onKey = { calculator = CalculatorReducer.press(calculator, it) },
            onEquals = { calculator = CalculatorReducer.equals(calculator) },
        )
    }
}
