package app.tfl.feature.settings.security

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.BarSegment
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.ListRowTone
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.RadioRow
import app.tfl.core.designsystem.component.SectionHeader
import app.tfl.core.designsystem.component.SegmentedBar
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.Tag
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflDivider
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.component.TflSnackbarHost
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.model.security.AutoLockTimeout
import app.tfl.core.model.security.DuressMode
import app.tfl.core.model.security.KeyStorageLevel
import app.tfl.core.model.security.PanicTrigger
import app.tfl.core.session.biometric.BiometricAuth
import app.tfl.core.session.biometric.BiometricOutcome
import app.tfl.feature.settings.PinPurpose
import app.tfl.feature.settings.Protection
import app.tfl.feature.settings.R
import app.tfl.feature.settings.SecurityMessage
import app.tfl.feature.settings.SecuritySettingsUiState
import app.tfl.feature.settings.SecuritySettingsViewModel
import app.tfl.feature.settings.SecuritySheet
import app.tfl.feature.settings.label
import kotlinx.coroutines.launch

/** Everything the Security screen can ask its view model to do. */
internal interface SecurityActions {
    fun openSheet(sheet: SecuritySheet?)
    fun setAutoLock(timeout: AutoLockTimeout)
    fun setWipeAfterFailures(count: Int)
    fun setBiometric(enabled: Boolean)
    fun setFaceDownLock(enabled: Boolean)
    fun setPanicTrigger(trigger: PanicTrigger)
    fun setDisguise(enabled: Boolean)
    fun startPinFlow(purpose: PinPurpose)
    fun notYetAvailable()
}

@Composable
internal fun SecuritySettingsScreen(
    onBack: () -> Unit,
    onNotYetAvailable: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecuritySettingsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val activity = LocalActivity.current as? FragmentActivity
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val promptTitle = stringResource(R.string.security_biometric_prompt_title)
    val promptSubtitle = stringResource(R.string.security_biometric_prompt_subtitle)
    val promptCancel = stringResource(R.string.security_biometric_prompt_cancel)
    val message = uiState.message?.let { stringResource(it.text()) }

    LaunchedEffect(message) {
        if (message != null) {
            viewModel.messageShown()
            snackbar.showSnackbar(message)
        }
    }

    val actions = remember(viewModel, activity) {
        object : SecurityActions {
            override fun openSheet(sheet: SecuritySheet?) = viewModel.showSheet(sheet)
            override fun setAutoLock(timeout: AutoLockTimeout) = viewModel.setAutoLock(timeout)
            override fun setWipeAfterFailures(count: Int) = viewModel.setWipeAfterFailures(count)
            override fun setFaceDownLock(enabled: Boolean) = viewModel.setFaceDownLock(enabled)
            override fun setPanicTrigger(trigger: PanicTrigger) = viewModel.setPanicTrigger(trigger)
            override fun setDisguise(enabled: Boolean) = viewModel.setDisguise(enabled)
            override fun startPinFlow(purpose: PinPurpose) = viewModel.startPinFlow(purpose)
            override fun notYetAvailable() = onNotYetAvailable()

            override fun setBiometric(enabled: Boolean) {
                if (!enabled) {
                    viewModel.disableBiometric()
                    return
                }
                val host = activity ?: return
                scope.launch {
                    val enrolment = viewModel.biometricEnrolment()
                    val outcome = if (enrolment == null) {
                        BiometricOutcome.Failed("")
                    } else {
                        BiometricAuth.authenticate(host, promptTitle, promptSubtitle, promptCancel, enrolment.cipher)
                    }
                    viewModel.onBiometricEnrolled(outcome)
                }
            }
        }
    }

    Box(modifier.fillMaxSize()) {
        val flow = uiState.pinFlow
        if (flow == null) {
            SecuritySettingsContent(uiState = uiState, onBack = onBack, actions = actions)
        } else {
            BackHandler { viewModel.cancelPinFlow() }
            PinFlowScreen(
                flow = flow,
                onDigit = viewModel::pinDigit,
                onDelete = viewModel::pinDelete,
                onChooseMode = viewModel::chooseDuressMode,
                onContinue = viewModel::proceed,
                onCancel = viewModel::cancelPinFlow,
            )
        }
        TflSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
    when (uiState.sheet) {
        SecuritySheet.AUTO_LOCK -> AutoLockSheet(uiState.autoLock, onSelect = actions::setAutoLock, onDismiss = { actions.openSheet(null) })
        SecuritySheet.WIPE_AFTER -> WipeAfterSheet(uiState.wipeAfterFailures, onSelect = actions::setWipeAfterFailures, onDismiss = { actions.openSheet(null) })
        null -> Unit
    }
}

@Composable
internal fun SecuritySettingsContent(
    uiState: SecuritySettingsUiState,
    onBack: () -> Unit,
    actions: SecurityActions,
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
            ProtectionsCard(uiState.activeProtections)

            SectionHeader(
                stringResource(R.string.security_section_access),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Fingerprint,
            )
            GroupCard {
                ListRow(
                    title = stringResource(R.string.security_app_lock_title),
                    subtitle = stringResource(R.string.security_app_lock_subtitle, uiState.kdfMemoryMiB.toInt()),
                    standalone = false,
                    trailing = { AlwaysOn() },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_change_pin_title),
                    subtitle = stringResource(R.string.security_change_pin_subtitle),
                    icon = null,
                    standalone = false,
                    onClick = { actions.startPinFlow(PinPurpose.CHANGE_PIN) },
                )
                TflDivider()
                ToggleRow(
                    title = stringResource(R.string.security_biometric_title),
                    subtitle = stringResource(
                        when {
                            !uiState.biometricAvailable -> R.string.security_biometric_unavailable
                            !uiState.biometricAllowed -> R.string.security_biometric_duress
                            else -> R.string.security_biometric_subtitle
                        },
                    ),
                    checked = uiState.biometricEnabled,
                    onCheckedChange = actions::setBiometric,
                    enabled = uiState.biometricAvailable && uiState.biometricAllowed,
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_auto_lock_title),
                    // With the disguise on, AutoLock locks at once whatever the timeout.
                    subtitle = stringResource(if (uiState.disguiseEnabled) R.string.security_auto_lock_disguised else uiState.autoLock.label()),
                    standalone = false,
                    onClick = { actions.openSheet(SecuritySheet.AUTO_LOCK) },
                )
                TflDivider()
                ToggleRow(
                    title = stringResource(R.string.security_face_down_title),
                    subtitle = stringResource(R.string.security_face_down_subtitle),
                    checked = uiState.faceDownLock,
                    onCheckedChange = actions::setFaceDownLock,
                    titleBadge = { NotActiveYet() },
                )
            }

            SectionHeader(
                stringResource(R.string.security_section_duress),
                modifier = Modifier.padding(top = 12.dp),
                icon = MaterialSymbols.Warning,
                iconTint = colors.warning,
            )
            GroupCard {
                val duressSet = uiState.duressMode != DuressMode.NONE
                ListRow(
                    title = stringResource(R.string.security_duress_title),
                    subtitle = stringResource(
                        when (uiState.duressMode) {
                            DuressMode.NONE -> R.string.security_duress_off
                            DuressMode.DECOY -> R.string.security_duress_decoy
                            DuressMode.WIPE -> R.string.security_duress_wipe
                        },
                    ),
                    standalone = false,
                    trailing = {
                        PillButton(
                            stringResource(if (duressSet) R.string.security_duress_change else R.string.security_duress_set_up),
                            onClick = { actions.startPinFlow(PinPurpose.SET_DURESS) },
                            contentColor = colors.textPrimary,
                        )
                    },
                )
                if (duressSet) {
                    TflDivider()
                    ListRow(
                        title = stringResource(R.string.security_duress_remove),
                        standalone = false,
                        tone = ListRowTone.Danger,
                        onClick = { actions.startPinFlow(PinPurpose.REMOVE_DURESS) },
                    )
                }
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_wipe_after_title),
                    subtitle = if (uiState.wipeAfterFailures > 0) {
                        pluralStringResource(R.plurals.security_wipe_after_on, uiState.wipeAfterFailures, uiState.wipeAfterFailures)
                    } else {
                        stringResource(R.string.security_wipe_after_off)
                    },
                    standalone = false,
                    onClick = { actions.openSheet(SecuritySheet.WIPE_AFTER) },
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
                    onSelect = { actions.setPanicTrigger(PanicTrigger.SHAKE) },
                )
                RadioRow(
                    title = stringResource(R.string.security_trigger_volume),
                    selected = uiState.panicTrigger == PanicTrigger.VOLUME_DOWN,
                    onSelect = { actions.setPanicTrigger(PanicTrigger.VOLUME_DOWN) },
                )
                Text(
                    text = stringResource(R.string.security_trigger_note),
                    modifier = Modifier.padding(start = 12.dp, end = 12.dp, bottom = 12.dp),
                    style = TflTheme.typography.codeSm,
                    color = colors.warning,
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
                    checked = uiState.disguiseEnabled,
                    onCheckedChange = actions::setDisguise,
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_recents_title),
                    subtitle = stringResource(R.string.security_recents_subtitle),
                    standalone = false,
                    trailing = { AlwaysOn() },
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
                    trailing = { AlwaysOn() },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_memory_title),
                    subtitle = stringResource(R.string.security_memory_subtitle),
                    standalone = false,
                    trailing = { AlwaysOn() },
                )
                TflDivider()
                ListRow(
                    title = stringResource(R.string.security_key_storage_title),
                    subtitle = stringResource(
                        when (uiState.keyStorage) {
                            KeyStorageLevel.STRONGBOX -> R.string.security_key_storage_strongbox
                            KeyStorageLevel.TEE -> R.string.security_key_storage_tee
                            KeyStorageLevel.SOFTWARE -> R.string.security_key_storage_software
                        },
                    ),
                    standalone = false,
                    trailing = {
                        Tag(
                            stringResource(uiState.keyStorage.label()),
                            color = if (uiState.keyStorage == KeyStorageLevel.SOFTWARE) colors.warning else colors.success,
                        )
                    },
                )
            }

            DestructiveButton(
                text = stringResource(R.string.security_configure_wipe),
                onClick = actions::notYetAvailable,
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
private fun ProtectionsCard(active: List<Protection>) {
    val colors = TflTheme.colors
    val total = Protection.entries.size
    TflCard(modifier = Modifier.padding(top = 4.dp), verticalSpacing = 8.dp) {
        Text(stringResource(R.string.security_protections_label), style = TflTheme.typography.labelSm, color = colors.primary)
        Text(
            text = stringResource(R.string.security_protections_title, active.size, total),
            style = TflTheme.typography.headlineSm,
            color = colors.textPrimary,
        )
        SegmentedBar(
            segments = List(total) { index -> BarSegment(1f, if (index < active.size) colors.success else colors.outline) },
            modifier = Modifier.padding(vertical = 4.dp),
        )
        Text(
            text = stringResource(R.string.security_protections_active, active.map { stringResource(it.label()) }.joinToString(" · ")),
            style = TflTheme.typography.codeSm,
            color = colors.success,
        )
    }
}

private fun Protection.label(): Int = when (this) {
    Protection.PIN_LOCK -> R.string.protection_pin_lock
    Protection.ENCRYPTED_STORAGE -> R.string.protection_encrypted_storage
    Protection.SCREENSHOT_BLOCKING -> R.string.protection_screenshot_blocking
    Protection.DURESS_PIN -> R.string.protection_duress_pin
    Protection.WIPE_AFTER_FAILURES -> R.string.protection_wipe_after_failures
    Protection.CALCULATOR_DISGUISE -> R.string.protection_calculator_disguise
}

private fun SecurityMessage.text(): Int = when (this) {
    SecurityMessage.PIN_CHANGED -> R.string.security_message_pin_changed
    SecurityMessage.DURESS_SET -> R.string.security_message_duress_set
    SecurityMessage.DURESS_REMOVED -> R.string.security_message_duress_removed
    SecurityMessage.DISGUISE_ON -> R.string.security_message_disguise_on
    SecurityMessage.DISGUISE_OFF -> R.string.security_message_disguise_off
    SecurityMessage.BIOMETRIC_ON -> R.string.security_message_biometric_on
    SecurityMessage.BIOMETRIC_FAILED -> R.string.security_message_biometric_failed
}

@Composable
private fun AlwaysOn() = Tag(stringResource(R.string.settings_always_on), color = TflTheme.colors.success)

@Composable
private fun NotActiveYet() = Tag(stringResource(R.string.settings_not_active_yet), color = TflTheme.colors.warning)

@Composable
private fun AutoLockSheet(selected: AutoLockTimeout, onSelect: (AutoLockTimeout) -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.security_auto_lock_title), icon = MaterialSymbols.LockClock, onClose = onDismiss)
        Column(Modifier.padding(bottom = 16.dp)) {
            AutoLockTimeout.entries.forEach { timeout ->
                RadioRow(stringResource(timeout.label()), selected = timeout == selected, onSelect = { onSelect(timeout) })
            }
        }
    }
}

/** The choices offered for wipe-after-N; the vault accepts 3 to 50. */
internal val WIPE_AFTER_CHOICES = listOf(0, 5, 10, 20)

@Composable
private fun WipeAfterSheet(selected: Int, onSelect: (Int) -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(stringResource(R.string.security_wipe_after_title), icon = MaterialSymbols.DeleteForever, onClose = onDismiss)
        Column(Modifier.padding(bottom = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            WIPE_AFTER_CHOICES.forEach { count ->
                RadioRow(
                    title = if (count == 0) {
                        stringResource(R.string.security_wipe_after_sheet_off)
                    } else {
                        pluralStringResource(R.plurals.security_wipe_after_option, count, count)
                    },
                    selected = count == selected,
                    onSelect = { onSelect(count) },
                )
            }
            Text(
                stringResource(R.string.security_wipe_after_note),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                style = TflTheme.typography.bodySm,
                color = TflTheme.colors.textMuted,
            )
        }
    }
}

@Composable
internal fun GroupCard(content: @Composable () -> Unit) {
    TflCard(contentPadding = PaddingValues(0.dp), verticalSpacing = 0.dp) { content() }
}
