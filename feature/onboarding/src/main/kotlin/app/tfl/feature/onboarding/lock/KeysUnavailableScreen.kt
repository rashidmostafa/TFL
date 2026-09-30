package app.tfl.feature.onboarding.lock

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import app.tfl.core.designsystem.component.DestructiveButton
import app.tfl.core.designsystem.component.IconTile
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.onboarding.OnboardingPage
import app.tfl.feature.onboarding.R

/** The lock state can't be decrypted: its hardware key is gone. Only a wipe and restore helps. */
@Composable
fun KeysUnavailableRoute(viewModel: LockViewModel = hiltViewModel()) {
    KeysUnavailableScreen(onWipe = viewModel::wipe)
}

@Composable
internal fun KeysUnavailableScreen(onWipe: () -> Unit) {
    val colors = TflTheme.colors
    OnboardingPage(
        onBack = null,
        bottom = {
            DestructiveButton(stringResource(R.string.lock_forgot_confirm), onClick = onWipe, icon = MaterialSymbols.DeleteForever, modifier = Modifier.fillMaxWidth())
        },
    ) {
        Spacer(Modifier.height(48.dp))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
            IconTile(MaterialSymbols.KeyOff, tint = colors.danger, size = 72.dp, iconSize = 36.dp)
            Text(stringResource(R.string.keys_unavailable_title), style = TflTheme.typography.headlineMd, color = colors.textPrimary, textAlign = TextAlign.Center)
            Text(stringResource(R.string.keys_unavailable_body), style = TflTheme.typography.bodyMd, color = colors.textMuted, textAlign = TextAlign.Center)
        }
    }
}
