package app.tfl.feature.contacts.add

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleStartEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.crypto.pairing.InvalidCode
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.QrCode
import app.tfl.core.designsystem.component.SegmentedTab
import app.tfl.core.designsystem.component.SegmentedTabs
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflProgressBar
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.cornerMarks
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.contacts.R
import app.tfl.feature.contacts.debug.ContactsDebugTools
import app.tfl.feature.contacts.scan.QrScanner

@Composable
internal fun AddFriendScreen(
    onBack: () -> Unit,
    onAdded: (contactId: Long) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AddFriendViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val currentOnAdded by rememberUpdatedState(onAdded)
    LaunchedEffect(viewModel) { viewModel.added.collect { currentOnAdded(it) } }
    LifecycleStartEffect(viewModel) {
        viewModel.start()
        onStopOrDispose { viewModel.stop() }
    }
    AddFriendContent(
        uiState = uiState,
        onBack = onBack,
        onSelectTab = viewModel::selectTab,
        onDone = viewModel::done,
        modifier = modifier,
        scanner = { QrScanner(onCode = viewModel::onScanned, modifier = it) },
        debugTools = { ContactsDebugTools.ScanTools(onCode = viewModel::onScanned) },
    )
    uiState.namesake?.let { name ->
        NamesakeSheet(
            name = name,
            onSamePerson = { viewModel.onNamesakeAnswer(samePerson = true) },
            onSomeoneElse = { viewModel.onNamesakeAnswer(samePerson = false) },
            onDismiss = viewModel::dismissNamesake,
        )
    }
}

/**
 * @param scanner the camera; tests pass a stand-in.
 * @param debugTools under the camera in debug builds only.
 */
@Composable
internal fun AddFriendContent(
    uiState: AddFriendUiState,
    onBack: () -> Unit,
    onSelectTab: (AddFriendTab) -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    scanner: @Composable (Modifier) -> Unit,
    debugTools: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        TflTopBar(
            title = stringResource(R.string.add_friend_title),
            navigationIcon = { TflBackButton(onClick = onBack) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SegmentedTabs(
                tabs = listOf(
                    SegmentedTab(stringResource(R.string.add_friend_tab_my_code), MaterialSymbols.QrCode2),
                    SegmentedTab(stringResource(R.string.add_friend_tab_scan), MaterialSymbols.QrCodeScanner),
                ),
                selectedIndex = uiState.tab.ordinal,
                onSelect = { onSelectTab(AddFriendTab.entries[it]) },
            )
            if (uiState.windowClosed) {
                Callout(stringResource(R.string.add_friend_window_closed), tone = CalloutTone.Warning)
            }
            when (uiState.tab) {
                AddFriendTab.MY_CODE -> MyCodeTab(uiState, onScanTheirs = { onSelectTab(AddFriendTab.SCAN) }, onDone = onDone)
                AddFriendTab.SCAN -> {
                    ScanTab(uiState, scanner)
                    debugTools()
                }
            }
        }
    }
}

@Composable
private fun MyCodeTab(uiState: AddFriendUiState, onScanTheirs: () -> Unit, onDone: () -> Unit) {
    when (val step = uiState.step) {
        PairingStep.Ready -> Callout(stringResource(R.string.add_friend_intro))
        is PairingStep.Answering -> AnsweringBanner(step, onScanTheirs, onDone)
    }
    MyCodeCard(uiState)
    if (uiState.step == PairingStep.Ready) {
        ListRow(
            title = stringResource(R.string.add_friend_scan_theirs_title),
            subtitle = stringResource(R.string.add_friend_scan_theirs_subtitle),
            icon = MaterialSymbols.QrCodeScanner,
            onClick = onScanTheirs,
        )
    }
    Text(
        stringResource(R.string.add_friend_code_footnote),
        style = TflTheme.typography.bodySm,
        color = TflTheme.colors.textMuted,
    )
}

@Composable
private fun AnsweringBanner(step: PairingStep.Answering, onScanTheirs: () -> Unit, onDone: () -> Unit) {
    if (step.verified) {
        Callout(
            text = stringResource(R.string.add_friend_answer_verified_body, step.name),
            title = stringResource(R.string.add_friend_answer_verified_title, step.name),
            tone = CalloutTone.Success,
        )
        PrimaryButton(stringResource(R.string.add_friend_done), onClick = onDone, modifier = Modifier.fillMaxWidth())
    } else {
        Callout(
            text = stringResource(R.string.add_friend_answer_added_body, step.name),
            title = stringResource(R.string.add_friend_answer_added_title, step.name),
            tone = CalloutTone.Info,
        )
        PrimaryButton(
            stringResource(R.string.add_friend_scan_their_answer),
            onClick = onScanTheirs,
            icon = MaterialSymbols.QrCodeScanner,
            modifier = Modifier.fillMaxWidth(),
        )
        GhostButton(stringResource(R.string.add_friend_stop_here), onClick = onDone, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun MyCodeCard(uiState: AddFriendUiState) {
    val colors = TflTheme.colors
    TflCard(verticalSpacing = 16.dp) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(colors.primary, CircleShape))
            Spacer(Modifier.width(10.dp))
            Text(
                uiState.myName,
                modifier = Modifier.weight(1f),
                style = TflTheme.typography.headlineSm,
                color = colors.textPrimary,
                maxLines = 1,
            )
            Tag(stringResource(R.string.add_friend_key_type))
        }
        val code = uiState.code
        Box(
            Modifier
                .fillMaxWidth(0.86f)
                .aspectRatio(1f)
                .align(Alignment.CenterHorizontally)
                .cornerMarks(colors.primary)
                .padding(12.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (code != null) {
                QrCode(code.matrix, contentDescription = stringResource(R.string.add_friend_code_description), modifier = Modifier.fillMaxSize())
            } else {
                Text(stringResource(R.string.add_friend_code_making), style = TflTheme.typography.bodyMd, color = colors.textMuted)
            }
        }
        if (code != null) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TflIcon(MaterialSymbols.Timer, contentDescription = null, size = 16.dp, tint = colors.textMuted)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        pluralStringResource(R.plurals.add_friend_new_code_in, code.secondsLeft, code.secondsLeft),
                        style = TflTheme.typography.labelMd,
                        color = colors.textMuted,
                    )
                }
                TflProgressBar(progress = code.secondsLeft / AddFriendViewModel.REFRESH_SECONDS.toFloat(), height = 4.dp)
            }
        }
        FingerprintBlock(uiState.myFingerprint, label = stringResource(R.string.add_friend_my_fingerprint))
    }
}

@Composable
private fun ScanTab(uiState: AddFriendUiState, scanner: @Composable (Modifier) -> Unit) {
    val colors = TflTheme.colors
    val step = uiState.step
    if (step is PairingStep.Answering && !step.verified) {
        Callout(stringResource(R.string.add_friend_scan_their_answer_hint, step.name))
    }
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .cornerMarks(colors.primary)
            .padding(10.dp)
            .clip(TflTheme.shapes.card),
    ) {
        scanner(Modifier.fillMaxSize())
    }
    uiState.problem?.let { problem ->
        Callout(problemText(problem), title = stringResource(R.string.add_friend_refused_title), tone = CalloutTone.Danger)
    }
    Text(
        stringResource(R.string.add_friend_scan_hint),
        style = TflTheme.typography.bodySm,
        color = colors.textMuted,
    )
}

@Composable
internal fun problemText(problem: InvalidCode): String = when (problem) {
    InvalidCode.NotPairingCode -> stringResource(R.string.pairing_problem_not_tfl)
    InvalidCode.Unreadable -> stringResource(R.string.pairing_problem_unreadable)
    InvalidCode.UnsupportedVersion -> stringResource(R.string.pairing_problem_version)
    InvalidCode.BadSignature -> stringResource(R.string.pairing_problem_signature)
    is InvalidCode.Expired -> pluralStringResource(
        R.plurals.pairing_problem_expired,
        problem.minutesOld.toInt(),
        problem.minutesOld.toInt(),
    )
    is InvalidCode.FromTheFuture -> pluralStringResource(
        R.plurals.pairing_problem_future,
        problem.minutesAhead.toInt(),
        problem.minutesAhead.toInt(),
    )
    InvalidCode.BadName -> stringResource(R.string.pairing_problem_name)
    InvalidCode.OwnCode -> stringResource(R.string.pairing_problem_own_code)
}

@Composable
private fun NamesakeSheet(name: String, onSamePerson: () -> Unit, onSomeoneElse: () -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.add_friend_namesake_title, name), icon = MaterialSymbols.PersonCheck)
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                stringResource(R.string.add_friend_namesake_body, name),
                style = TflTheme.typography.bodyMd,
                color = TflTheme.colors.textMuted,
            )
            PrimaryButton(stringResource(R.string.add_friend_namesake_same), onClick = onSamePerson, modifier = Modifier.fillMaxWidth())
            GhostButton(stringResource(R.string.add_friend_namesake_other), onClick = onSomeoneElse, modifier = Modifier.fillMaxWidth())
            GhostButton(stringResource(R.string.add_friend_namesake_cancel), onClick = onDismiss, modifier = Modifier.fillMaxWidth())
        }
    }
}
