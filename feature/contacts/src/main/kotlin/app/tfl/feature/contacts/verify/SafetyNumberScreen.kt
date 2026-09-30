package app.tfl.feature.contacts.verify

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.QrCode
import app.tfl.core.designsystem.component.SafetyNumberGrid
import app.tfl.core.designsystem.component.SegmentedTab
import app.tfl.core.designsystem.component.SegmentedTabs
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.cornerMarks
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.components.Trust
import app.tfl.feature.contacts.components.TrustBadge
import app.tfl.feature.contacts.debug.ContactsDebugTools
import app.tfl.feature.contacts.scan.QrScanner

@Composable
internal fun SafetyNumberScreen(
    onBack: () -> Unit,
    onPairAgain: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SafetyNumberViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirming by rememberSaveable { mutableStateOf(false) }
    SafetyNumberContent(
        uiState = uiState,
        onBack = onBack,
        onSelectTab = viewModel::selectTab,
        onMarkVerified = { confirming = true },
        onPairAgain = onPairAgain,
        modifier = modifier,
        scanner = { QrScanner(onCode = viewModel::onScanned, modifier = it) },
        debugTools = { ContactsDebugTools.ScanTools(onCode = viewModel::onScanned) },
    )
    if (confirming) {
        ConfirmSheet(
            name = uiState.name,
            onConfirm = {
                confirming = false
                viewModel.markVerified()
            },
            onDismiss = { confirming = false },
        )
    }
}

@Composable
internal fun SafetyNumberContent(
    uiState: SafetyNumberUiState,
    onBack: () -> Unit,
    onSelectTab: (CompareTab) -> Unit,
    onMarkVerified: () -> Unit,
    onPairAgain: () -> Unit,
    modifier: Modifier = Modifier,
    scanner: @Composable (Modifier) -> Unit,
    debugTools: @Composable () -> Unit = {},
) {
    val colors = TflTheme.colors
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.safety_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        if (uiState.loading) return@Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(uiState.name, style = TflTheme.typography.headlineMd, color = colors.textPrimary)
                TrustBadge(uiState.trust)
            }
            Text(stringResource(R.string.safety_intro, uiState.name), style = TflTheme.typography.bodyMd, color = colors.textMuted)
            SafetyNumberGrid(uiState.groups)
            ResultCallout(uiState)
            SegmentedTabs(
                tabs = listOf(
                    SegmentedTab(stringResource(R.string.safety_tab_show), MaterialSymbols.QrCode2),
                    SegmentedTab(stringResource(R.string.safety_tab_scan), MaterialSymbols.QrCodeScanner),
                ),
                selectedIndex = uiState.tab.ordinal,
                onSelect = { onSelectTab(CompareTab.entries[it]) },
            )
            Box(
                Modifier
                    .fillMaxWidth(if (uiState.tab == CompareTab.SHOW) 0.8f else 1f)
                    .aspectRatio(1f)
                    .align(Alignment.CenterHorizontally)
                    .cornerMarks(colors.primary)
                    .padding(10.dp)
                    .clip(TflTheme.shapes.card),
            ) {
                when (uiState.tab) {
                    CompareTab.SHOW -> uiState.code?.let {
                        QrCode(it, contentDescription = stringResource(R.string.safety_code_description), modifier = Modifier.fillMaxSize())
                    }
                    CompareTab.SCAN -> scanner(Modifier.fillMaxSize())
                }
            }
            Text(
                stringResource(if (uiState.tab == CompareTab.SHOW) R.string.safety_show_hint else R.string.safety_scan_hint, uiState.name),
                style = TflTheme.typography.bodySm,
                color = colors.textMuted,
            )
            if (uiState.tab == CompareTab.SCAN) debugTools()
            if (uiState.trust != Trust.VERIFIED) {
                GhostButton(
                    stringResource(R.string.safety_mark_verified),
                    onClick = onMarkVerified,
                    icon = MaterialSymbols.Verified,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            ListRow(
                title = stringResource(R.string.safety_pair_again),
                subtitle = stringResource(R.string.safety_pair_again_subtitle),
                icon = MaterialSymbols.Handshake,
                onClick = onPairAgain,
            )
        }
    }
}

@Composable
private fun ResultCallout(uiState: SafetyNumberUiState) {
    when (uiState.result) {
        CompareResult.MATCH -> Callout(
            stringResource(R.string.safety_match_body, uiState.name),
            title = stringResource(R.string.safety_match_title),
            tone = CalloutTone.Success,
        )
        CompareResult.MISMATCH -> Callout(
            stringResource(R.string.safety_mismatch_body),
            title = stringResource(R.string.safety_mismatch_title),
            tone = CalloutTone.Danger,
        )
        CompareResult.NOT_A_COMPARISON_CODE -> Callout(
            stringResource(R.string.safety_not_a_code, uiState.name),
            tone = CalloutTone.Warning,
        )
        null -> Unit
    }
}

@Composable
private fun ConfirmSheet(name: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.safety_confirm_title), icon = MaterialSymbols.Verified)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.safety_confirm_body, name),
                style = TflTheme.typography.bodyMd,
                color = TflTheme.colors.textMuted,
            )
            PrimaryButton(stringResource(R.string.safety_confirm), onClick = onConfirm, modifier = Modifier.fillMaxWidth())
            GhostButton(stringResource(R.string.safety_confirm_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
