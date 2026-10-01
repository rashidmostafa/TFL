package app.tfl.feature.settings.notifications

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import app.tfl.core.common.log.TflLog
import app.tfl.core.database.repository.SettingKeys
import app.tfl.core.database.repository.SettingsRepository
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.CalloutTone
import app.tfl.core.designsystem.component.ListRow
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.component.TflTopBar
import app.tfl.core.designsystem.component.ToggleRow
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.settings.R
import app.tfl.feature.settings.security.GroupCard
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(private val settings: SettingsRepository) : ViewModel() {

    /** Whether new-message alerts name the friend. */
    val showSender: StateFlow<Boolean> = settings.observe(SettingKeys.NOTIFY_SHOW_SENDER)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingKeys.NOTIFY_SHOW_SENDER.default)

    fun setShowSender(show: Boolean) {
        viewModelScope.launch {
            try {
                settings.set(SettingKeys.NOTIFY_SHOW_SENDER, show)
            } catch (e: Exception) {
                // Locked in the meantime: the screen is going away with the database.
                TflLog.w(TAG, e) { "Notification setting not saved" }
            }
        }
    }

    private companion object {
        const val TAG = "NotificationSettings"
    }
}

@Composable
internal fun NotificationSettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val showSender by viewModel.showSender.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var allowed by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = NotificationManagerCompat.from(context).areNotificationsEnabled() }
    NotificationSettingsContent(
        showSender = showSender,
        allowed = allowed,
        onBack = onBack,
        onShowSender = viewModel::setShowSender,
        onOpenSystemSettings = { context.openNotificationSettings() },
        modifier = modifier,
    )
}

@Composable
internal fun NotificationSettingsContent(
    showSender: Boolean,
    allowed: Boolean,
    onBack: () -> Unit,
    onShowSender: (Boolean) -> Unit,
    onOpenSystemSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas),
    ) {
        TflTopBar(title = stringResource(R.string.notifications_title), navigationIcon = { TflBackButton(onClick = onBack) })
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Callout(stringResource(R.string.notifications_about), title = stringResource(R.string.notifications_about_title))
            GroupCard {
                ToggleRow(
                    title = stringResource(R.string.notifications_show_sender_title),
                    subtitle = stringResource(R.string.notifications_show_sender_subtitle),
                    checked = showSender,
                    onCheckedChange = onShowSender,
                )
            }
            if (!allowed) {
                Callout(stringResource(R.string.notifications_off_body), title = stringResource(R.string.notifications_off_title), tone = CalloutTone.Warning)
                ListRow(
                    title = stringResource(R.string.notifications_open_system),
                    icon = MaterialSymbols.Notifications,
                    onClick = onOpenSystemSettings,
                )
            }
        }
    }
}

private fun Context.openNotificationSettings() {
    startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
}
