package app.tfl.feature.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.BarSegment
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.RadioRow
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SegmentedBar
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.settings.PanicTrigger
import app.tfl.feature.settings.R
import app.tfl.feature.settings.SecuritySettingsUiState
import app.tfl.feature.settings.SecuritySettingsViewModel
import app.tfl.feature.settings.SecurityToggle

@Composable
internal fun SecuritySettingsScreen(
    onBack: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    SecuritySettingsContent(
        uiState = uiState,
        onBack = onBack,
        onToggle = viewModel::onToggle,
        onPanicTriggerSelect = viewModel::onPanicTriggerSelect,
        onNotYetAvailable = onNotYetAvailable,
        modifier = modifier,
    )
}

@Composable
internal fun SecuritySettingsContent(
    uiState: SecuritySettingsUiState,
    onBack: () -> Unit,
    onToggle: (SecurityToggle, Boolean) -> Unit,
    onPanicTriggerSelect: (PanicTrigger) -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.security_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Callout(
                text = stringResource(R.string.security_preview_body),
                title = stringResource(R.string.settings_preview_title),
                tone = CalloutTone.Warning,
            )
            TflCard(modifier = Modifier.padding(top = 4.dp), verticalSpacing = 8.dp) {
                Text(stringResource(R.string.security_protections_label), style = TflTheme.typography.labelSm, color = colors.primary)
                Text(
                    text = stringResource(R.string.security_protections_title, uiState.enforcedProtections, uiState.totalProtections),
                    style = TflTheme.typography.headlineSm,
                    color = colors.textPrimary,
                )
                SegmentedBar(
                    segments = List(uiState.totalProtections) { index ->
                        BarSegment(1f, if (index < uiState.enforcedProtections) colors.success else colors.outline)
                    },
                    modifier = Modifier.padding(vertical = 4.dp),
                )
                Text(stringResource(R.string.security_protections_active), style = TflTheme.typography.codeSm, color = colors.success)
            }

            SectionHeader(
                stringResource(R.string.security_section_access),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Fingerprint,
            )
            GroupCard {
                ToggleRow(
                    title = stringResource(R.string.security_app_lock_title),
                    subtitle = stringResource(R.string.security_app_lock_subtitle),
                    checked = uiState.isOn(SecurityToggle.APP_LOCK),
                    onCheckedChange = { onToggle(SecurityToggle.APP_LOCK, it) },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_auto_lock_title),
                    subtitle = stringResource(R.string.security_auto_lock_subtitle),
                    standalone = false,
                    onClick = onNotYetAvailable,
                )
                TflDivider()
                ToggleRow(
                    title = stringResource(R.string.security_face_down_title),
                    subtitle = stringResource(R.string.security_face_down_subtitle),
                    checked = uiState.isOn(SecurityToggle.FACE_DOWN_LOCK),
                    onCheckedChange = { onToggle(SecurityToggle.FACE_DOWN_LOCK, it) },
                )
            }

            SectionHeader(
                stringResource(R.string.security_section_duress),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Warning,
                iconTint = colors.warning,
            )
            GroupCard {
                ListRow(
                    title = stringResource(R.string.security_duress_title),
                    subtitle = stringResource(R.string.security_duress_subtitle),
                    standalone = false,
                    trailing = {
                        PillButton(stringResource(R.string.security_duress_action), onClick = onNotYetAvailable, contentColor = colors.textPrimary)
                    },
                )
                TflDivider()
                Text(
                    text = stringResource(R.string.security_panic_trigger),
                    modifier = Modifier.padding(start = 12.dp, top = 12.dp),
                    style = TflTheme.typography.labelSm,
                    color = colors.textMuted,
                )
                RadioRow(
                    title = stringResource(R.string.security_trigger_shake),
                    selected = uiState.panicTrigger == PanicTrigger.SHAKE,
                    onSelect = { onPanicTriggerSelect(PanicTrigger.SHAKE) },
                )
                RadioRow(
                    title = stringResource(R.string.security_trigger_volume),
                    selected = uiState.panicTrigger == PanicTrigger.VOLUME_DOWN,
                    onSelect = { onPanicTriggerSelect(PanicTrigger.VOLUME_DOWN) },
                )
                Text(
                    text = stringResource(R.string.security_trigger_action),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    style = TflTheme.typography.codeSm,
                    color = colors.primary,
                )
            }

            SectionHeader(
                stringResource(R.string.security_section_camouflage),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.VisibilityOff,
            )
            GroupCard {
                ToggleRow(
                    title = stringResource(R.string.security_calculator_title),
                    subtitle = stringResource(R.string.security_calculator_subtitle),
                    checked = uiState.isOn(SecurityToggle.CALCULATOR_DISGUISE),
                    onCheckedChange = { onToggle(SecurityToggle.CALCULATOR_DISGUISE, it) },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_recents_title),
                    subtitle = stringResource(R.string.security_recents_subtitle),
                    standalone = false,
                    trailing = { Tag(stringResource(R.string.settings_always_on), color = colors.success) },
                )
            }

            SectionHeader(
                stringResource(R.string.security_section_device),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Memory,
            )
            GroupCard {
                ListRow(
                    title = stringResource(R.string.security_screenshots_title),
                    subtitle = stringResource(R.string.security_screenshots_subtitle),
                    standalone = false,
                    trailing = { Tag(stringResource(R.string.settings_always_on), color = colors.success) },
                )
                TflDivider()
                ToggleRow(
                    title = stringResource(R.string.security_memory_title),
                    subtitle = stringResource(R.string.security_memory_subtitle),
                    checked = uiState.isOn(SecurityToggle.CLEAR_KEYS_ON_LOCK),
                    onCheckedChange = { onToggle(SecurityToggle.CLEAR_KEYS_ON_LOCK, it) },
                )
            }

            DestructiveButton(
                text = stringResource(R.string.security_configure_wipe),
                onClick = onNotYetAvailable,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                icon = MaterialSymbols.Skull,
            )
            Text(
                text = stringResource(R.string.security_wipe_caption),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                style = TflTheme.typography.bodySm,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
internal fun GroupCard(content: @Composable () -> Unit) {
    TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) { content() }
}
