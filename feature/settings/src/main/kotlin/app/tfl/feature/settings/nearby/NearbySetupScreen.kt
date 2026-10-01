package app.tfl.feature.settings.nearby

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.GhostButton
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflButtonSize
import app.tfl.core.designsystem.component.TflCard
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.transport.android.NearbyPermissions
import app.tfl.core.transport.android.NearbySetup
import app.tfl.core.transport.android.NearbyStatus
import app.tfl.feature.settings.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class NearbySetupViewModel @Inject constructor(private val setup: NearbySetup) : ViewModel() {
    val status: StateFlow<NearbyStatus> = setup.status

    fun refresh() = setup.refresh()
}

/** How a permission request went. */
enum class PermissionAnswer {
    /** Not asked yet on this screen. */
    NOT_ASKED,

    /** Refused; Android will ask again. */
    REFUSED,

    /** Refused, and Android won't show its dialog again: only system settings can allow it. */
    BLOCKED,
}

/**
 * Sets up what Nearby needs on this phone, one thing at a time, each explained before Android asks:
 * the permissions for this Android version, Bluetooth, and Location where Android needs it on.
 */
@Composable
internal fun NearbySetupScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NearbySetupViewModel = hiltViewModel(),
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val activity = LocalActivity.current
    var answer by rememberSaveable { mutableStateOf(PermissionAnswer.NOT_ASKED) }
    val request = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
        viewModel.refresh()
        val refused = NearbyPermissions.required(Build.VERSION.SDK_INT).filter { results[it] == false }
        answer = when {
            refused.isEmpty() -> PermissionAnswer.NOT_ASKED
            // Checked here, right after the answer: before the first request it reads false too.
            refused.all { activity?.shouldShowRequestPermissionRationale(it) == false } -> PermissionAnswer.BLOCKED
            else -> PermissionAnswer.REFUSED
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refresh() }

    NearbySetupContent(
        status = status,
        answer = answer,
        sdk = Build.VERSION.SDK_INT,
        onBack = onBack,
        onAllow = { request.launch(NearbyPermissions.toRequest(Build.VERSION.SDK_INT).toTypedArray()) },
        onOpenAppSettings = { context.openAppSettings() },
        onOpenBluetooth = { context.startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS)) },
        onOpenLocation = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) },
        modifier = modifier,
    )
}

@Composable
internal fun NearbySetupContent(
    status: NearbyStatus,
    answer: PermissionAnswer,
    sdk: Int,
    onBack: () -> Unit,
    onAllow: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onOpenBluetooth: () -> Unit,
    onOpenLocation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    // What Android will ask for on this version, explained first.
    val (permissionTitle, permissionBody) = when {
        sdk <= Build.VERSION_CODES.R -> R.string.nearby_setup_location_permission_title to R.string.nearby_setup_location_permission_body
        sdk == Build.VERSION_CODES.S -> R.string.nearby_setup_devices_title to R.string.nearby_setup_devices_and_location_body
        else -> R.string.nearby_setup_devices_title to R.string.nearby_setup_devices_body
    }
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(colors.canvas),
    ) {
        TflTopBar(title = stringResource(R.string.nearby_setup_title), navigationIcon = { TflBackButton(onClick = onBack) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.nearby_setup_intro), style = TflTheme.typography.bodyMd, color = colors.textMuted)

            Step(
                icon = MaterialSymbols.Sensors,
                title = stringResource(permissionTitle),
                body = stringResource(permissionBody),
                done = status.permitted,
            ) {
                when (answer) {
                    PermissionAnswer.BLOCKED -> {
                        Callout(stringResource(R.string.nearby_setup_blocked), tone = CalloutTone.Warning)
                        PrimaryButton(stringResource(R.string.nearby_setup_open_settings), onClick = onOpenAppSettings, size = TflButtonSize.Medium, modifier = Modifier.fillMaxWidth())
                    }
                    PermissionAnswer.REFUSED -> {
                        Callout(stringResource(R.string.nearby_setup_refused), tone = CalloutTone.Warning)
                        PrimaryButton(stringResource(R.string.nearby_setup_allow), onClick = onAllow, size = TflButtonSize.Medium, modifier = Modifier.fillMaxWidth())
                    }
                    PermissionAnswer.NOT_ASKED -> PrimaryButton(stringResource(R.string.nearby_setup_allow), onClick = onAllow, size = TflButtonSize.Medium, modifier = Modifier.fillMaxWidth())
                }
            }
            Step(
                icon = MaterialSymbols.Bluetooth,
                title = stringResource(R.string.nearby_setup_bluetooth_title),
                body = stringResource(R.string.nearby_setup_bluetooth_body),
                done = status.bluetoothOn,
            ) {
                GhostButton(stringResource(R.string.nearby_setup_bluetooth_action), onClick = onOpenBluetooth, size = TflButtonSize.Medium, modifier = Modifier.fillMaxWidth())
            }
            if (status.locationNeeded) {
                Step(
                    icon = MaterialSymbols.LocationOn,
                    title = stringResource(R.string.nearby_setup_location_title),
                    body = stringResource(R.string.nearby_setup_location_body),
                    done = status.locationOn,
                ) {
                    GhostButton(stringResource(R.string.nearby_setup_location_action), onClick = onOpenLocation, size = TflButtonSize.Medium, modifier = Modifier.fillMaxWidth())
                }
            }
            if (status.ready) {
                Callout(stringResource(R.string.nearby_setup_ready), title = stringResource(R.string.nearby_setup_ready_title), tone = CalloutTone.Success)
                PrimaryButton(stringResource(R.string.nearby_setup_done), onClick = onBack, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun Step(icon: String, title: String, body: String, done: Boolean, action: @Composable () -> Unit) {
    val colors = TflTheme.colors
    TflCard(borderColor = if (done) colors.success.copy(alpha = 0.4f) else colors.border, verticalSpacing = 12.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconTile(if (done) MaterialSymbols.CheckCircle else icon, tint = if (done) colors.success else colors.primary)
            Column(Modifier.weight(1f)) {
                Text(title, style = TflTheme.typography.headlineSm, color = colors.textPrimary)
                if (done) Text(stringResource(R.string.nearby_setup_step_done), style = TflTheme.typography.labelSm, color = colors.success)
            }
        }
        if (!done) {
            Text(body, style = TflTheme.typography.bodyMd, color = colors.textMuted)
            action()
        }
    }
}

private fun Context.openAppSettings() {
    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
}
