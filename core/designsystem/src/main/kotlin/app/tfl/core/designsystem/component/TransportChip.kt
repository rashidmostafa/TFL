package app.tfl.core.designsystem.component

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.core.designsystem.theme.forTransport
import app.tfl.core.model.Transport

/**
 * How a message or peer is routed: Nearby, Mesh, Tor or Queued, with optional [detail]
 * such as "2 hops". Pill with a 50% border in the transport's colour.
 */
@Composable
fun TransportChip(
    transport: Transport,
    modifier: Modifier = Modifier,
    detail: String? = null,
) {
    val color = TflTheme.colors.forTransport(transport)
    val label = transport.label()
    Pill(
        containerColor = TflTheme.colors.surface,
        borderColor = color.copy(alpha = 0.5f),
        modifier = modifier.clearAndSetSemantics {
            contentDescription = if (detail == null) label else "$label, $detail"
        },
    ) {
        TflIcon(transport.symbol, contentDescription = null, size = 14.dp, tint = color)
        Text(
            text = if (detail == null) label else "$label · $detail",
            style = TflTheme.typography.labelSm,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Display name of a transport. */
@Composable
@ReadOnlyComposable
fun Transport.label(): String = stringResource(
    when (this) {
        Transport.NEARBY -> R.string.transport_nearby
        Transport.MESH -> R.string.transport_mesh
        Transport.TOR -> R.string.transport_tor
        Transport.QUEUED -> R.string.transport_queued
    },
)

/** Icon of a transport, as a [MaterialSymbols] codepoint. */
val Transport.symbol: String
    get() = when (this) {
        Transport.NEARBY -> MaterialSymbols.Sensors
        Transport.MESH -> MaterialSymbols.Hub
        Transport.TOR -> MaterialSymbols.VpnLock
        Transport.QUEUED -> MaterialSymbols.Schedule
    }
