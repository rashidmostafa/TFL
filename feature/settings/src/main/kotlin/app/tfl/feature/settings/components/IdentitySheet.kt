package app.tfl.feature.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.PrimaryButton
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.feature.settings.R

/**
 * Your public identity: fingerprint, and the way to your pairing code. Codes live on the Add friend
 * screen, where they're replaced every minute, so none is ever left on screen here.
 */
@Composable
internal fun IdentitySheet(fingerprint: List<String>, onShowPairingCode: () -> Unit, onDismiss: () -> Unit) {
    TflModalBottomSheet(onDismissRequest = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.settings_identity_title),
            subtitle = stringResource(R.string.settings_identity_subtitle),
            icon = MaterialSymbols.QrCode2,
            onClose = onDismiss,
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FingerprintBlock(fingerprint, label = stringResource(R.string.settings_identity_fingerprint))
            PrimaryButton(
                stringResource(R.string.settings_identity_show_code),
                onClick = onShowPairingCode,
                icon = MaterialSymbols.QrCode2,
                modifier = Modifier.fillMaxWidth(),
            )
            Callout(stringResource(R.string.settings_identity_note))
        }
    }
}
