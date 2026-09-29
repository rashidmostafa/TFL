package app.tfl.feature.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.Callout
import app.tfl.core.designsystem.component.FingerprintBlock
import app.tfl.core.designsystem.component.SheetHeader
import app.tfl.core.designsystem.component.TflModalBottomSheet
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.settings.R

/** Your public identity: QR placeholder (real QR codes come with contacts in Phase 2) and fingerprint. */
@Composable
internal fun IdentitySheet(fingerprint: List<String>, onDismiss: () -> Unit) {
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
            QrPlaceholder()
            FingerprintBlock(fingerprint, label = stringResource(R.string.settings_identity_fingerprint))
            Callout(stringResource(R.string.settings_identity_note))
        }
    }
}

@Composable
private fun QrPlaceholder() {
    val colors = TflTheme.colors
    Box(
        modifier = Modifier
            .size(200.dp)
            .background(colors.canvas, TflTheme.shapes.card)
            .drawBehind {
                drawRoundRect(
                    color = colors.outline,
                    cornerRadius = CornerRadius(16.dp.toPx()),
                    style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 10f))),
                )
            }
            .padding(20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            TflIcon(MaterialSymbols.QrCode2, contentDescription = null, size = 64.dp, tint = colors.textDim)
            Text(
                text = stringResource(R.string.settings_identity_qr_placeholder),
                style = TflTheme.typography.bodySm,
                color = colors.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}
