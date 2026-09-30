package app.tfl.feature.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.ChoiceCard
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.PinDots
import app.tfl.core.designsystem.component.PinPad
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.security.DuressMode
import app.tfl.feature.settings.PinError
import app.tfl.feature.settings.PinFlowUi
import app.tfl.feature.settings.PinPurpose
import app.tfl.feature.settings.PinStage
import app.tfl.feature.settings.R

/** A PIN prompt from the Security screen: current PIN, then the new PIN or code typed twice. */
@Composable
internal fun PinFlowScreen(
    flow: PinFlowUi,
    onDigit: (Int) -> Unit,
    onDelete: () -> Unit,
    onChooseMode: (DuressMode) -> Unit,
    onContinue: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(
                when (flow.purpose) {
                    PinPurpose.CHANGE_PIN -> R.string.pin_flow_change_title
                    PinPurpose.SET_DURESS -> R.string.pin_flow_duress_title
                    PinPurpose.REMOVE_DURESS -> R.string.pin_flow_remove_duress_title
                    PinPurpose.DISGUISE_CODE -> R.string.pin_flow_disguise_title
                },
            ),
            navigationIcon = { TflBackButton(onClick = onCancel) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (flow.stage) {
                PinStage.CHOOSE_MODE -> DuressModeChoice(flow.duressMode, onChooseMode, onContinue)
                PinStage.INTRO -> DisguiseIntro(onContinue)
                PinStage.CURRENT, PinStage.NEW, PinStage.CONFIRM -> PinPrompt(flow, onDigit, onDelete)
            }
        }
    }
}

@Composable
private fun DuressModeChoice(mode: DuressMode, onChooseMode: (DuressMode) -> Unit, onConfirm: () -> Unit) {
    val colors = TflTheme.colors
    Text(
        stringResource(R.string.pin_flow_mode_title),
        Modifier.fillMaxWidth(),
        style = TflTheme.typography.headlineSm,
        color = colors.textPrimary,
    )
    ChoiceCard(
        title = stringResource(R.string.pin_flow_decoy_title),
        tag = stringResource(R.string.pin_flow_decoy_tag),
        body = stringResource(R.string.pin_flow_decoy_body),
        selected = mode == DuressMode.DECOY,
        onSelect = { onChooseMode(DuressMode.DECOY) },
        icon = MaterialSymbols.VisibilityOff,
    )
    ChoiceCard(
        title = stringResource(R.string.pin_flow_wipe_title),
        tag = stringResource(R.string.pin_flow_wipe_tag),
        body = stringResource(R.string.pin_flow_wipe_body),
        selected = mode == DuressMode.WIPE,
        onSelect = { onChooseMode(DuressMode.WIPE) },
        icon = MaterialSymbols.DeleteForever,
        accent = colors.danger,
    )
    PrimaryButton(stringResource(R.string.pin_flow_continue), onClick = onConfirm, icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth())
}

/** What turning the disguise on does, before the code is chosen: TFL closes straight after. */
@Composable
private fun DisguiseIntro(onContinue: () -> Unit) {
    val colors = TflTheme.colors
    IconTile(MaterialSymbols.Calculate, tint = colors.mesh, size = 56.dp, iconSize = 28.dp)
    Text(
        stringResource(R.string.pin_flow_disguise_intro_title),
        style = TflTheme.typography.headlineMd, color = colors.textPrimary, textAlign = TextAlign.Center,
    )
    listOf(
        R.string.pin_flow_disguise_intro_icon,
        R.string.pin_flow_disguise_intro_open,
        R.string.pin_flow_disguise_intro_close,
    ).forEach { line ->
        Text(stringResource(line), Modifier.fillMaxWidth(), style = TflTheme.typography.bodyLg, color = colors.textMuted)
    }
    PrimaryButton(stringResource(R.string.pin_flow_choose_code), onClick = onContinue, icon = MaterialSymbols.ArrowForward, modifier = Modifier.fillMaxWidth())
}

@Composable
private fun PinPrompt(flow: PinFlowUi, onDigit: (Int) -> Unit, onDelete: () -> Unit) {
    val colors = TflTheme.colors
    IconTile(
        if (flow.purpose == PinPurpose.DISGUISE_CODE) MaterialSymbols.Calculate else MaterialSymbols.ShieldLock,
        size = 56.dp,
        iconSize = 28.dp,
    )
    Text(
        stringResource(
            when (flow.stage) {
                PinStage.CURRENT, PinStage.CHOOSE_MODE, PinStage.INTRO -> R.string.pin_flow_current
                PinStage.CONFIRM -> R.string.pin_flow_confirm
                PinStage.NEW -> when (flow.purpose) {
                    PinPurpose.SET_DURESS -> R.string.pin_flow_new_duress
                    PinPurpose.DISGUISE_CODE -> R.string.pin_flow_new_code
                    PinPurpose.CHANGE_PIN, PinPurpose.REMOVE_DURESS -> R.string.pin_flow_new_pin
                }
            },
        ),
        style = TflTheme.typography.headlineMd,
        color = colors.textPrimary,
        textAlign = TextAlign.Center,
    )
    val help = when {
        flow.stage == PinStage.CURRENT -> null
        flow.purpose == PinPurpose.DISGUISE_CODE -> R.string.pin_flow_code_help
        flow.purpose == PinPurpose.SET_DURESS -> R.string.pin_flow_duress_help
        else -> null
    }
    if (help != null) {
        Text(stringResource(help), style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center)
    }
    PinDots(entered = if (flow.busy) 6 else flow.entered, error = flow.error != null)
    Text(
        text = when {
            flow.busy -> stringResource(R.string.pin_flow_checking)
            flow.error != null -> stringResource(flow.error.text(flow.purpose))
            else -> ""
        },
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .semantics { liveRegion = LiveRegionMode.Polite },
        style = TflTheme.typography.bodyMd,
        color = if (flow.busy) colors.textMuted else colors.danger,
        textAlign = TextAlign.Center,
    )
    PinPad(onDigit = onDigit, onDelete = onDelete, enabled = !flow.busy)
}

private fun PinError.text(purpose: PinPurpose): Int = when (this) {
    PinError.WRONG_PIN -> R.string.pin_flow_wrong
    PinError.MISMATCH -> R.string.pin_flow_mismatch
    PinError.SAME_AS_OTHER_PIN -> if (purpose == PinPurpose.SET_DURESS) R.string.pin_flow_same_as_pin else R.string.pin_flow_same_as_duress
    PinError.CODE_IS_PIN -> R.string.pin_flow_code_is_pin
    PinError.FAILED -> R.string.pin_flow_failed
}
