package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

object TflSheetDefaults {
    val ScrimColor = Color.Black.copy(alpha = 0.6f)

    @Composable
    fun DragHandle(modifier: Modifier = Modifier) {
        Box(
            modifier = modifier
                .padding(top = 12.dp, bottom = 8.dp)
                .size(width = 36.dp, height = 4.dp)
                .background(TflTheme.colors.textDim.copy(alpha = 0.6f), CircleShape),
        )
    }
}

/**
 * Modal bottom sheet: surface colour, 24dp top corners, drag handle, 60% black scrim.
 * Opens fully expanded; remove it from composition (via [onDismissRequest]) to close it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TflModalBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = TflTheme.shapes.sheet
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        modifier = modifier.border(1.dp, TflTheme.colors.border, shape),
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = shape,
        containerColor = TflTheme.colors.surface,
        contentColor = TflTheme.colors.textPrimary,
        tonalElevation = 0.dp,
        scrimColor = TflSheetDefaults.ScrimColor,
        dragHandle = { TflSheetDefaults.DragHandle() },
        content = content,
    )
}

/** Sheet title row: icon tile, title, monospace subtitle and an optional close button. */
@Composable
fun SheetHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: String? = null,
    onClose: (() -> Unit)? = null,
) {
    val colors = TflTheme.colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(colors.primary.copy(alpha = 0.15f), TflTheme.shapes.control),
                contentAlignment = Alignment.Center,
            ) {
                TflIcon(icon, contentDescription = null, size = 24.dp, tint = colors.primary)
            }
        }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                modifier = Modifier.semantics { heading() },
                style = TflTheme.typography.headlineSm,
                color = colors.textPrimary,
            )
            if (subtitle != null) {
                Text(subtitle.uppercase(), style = TflTheme.typography.labelSm, color = colors.primary)
            }
        }
        if (onClose != null) {
            TflIconButton(
                symbol = MaterialSymbols.Close,
                contentDescription = stringResource(R.string.action_close),
                onClick = onClose,
                size = 40.dp,
                iconSize = 20.dp,
            )
        }
    }
}

/**
 * What a sheet shows below its [SheetHeader], padded and spaced. It scrolls when the phone is too
 * short to show it all, so the buttons at its end can always be reached. Call it in the sheet's own
 * column, right after the header, so it gets the height that's left.
 */
@Composable
fun ColumnScope.SheetBody(
    modifier: Modifier = Modifier,
    spacing: Dp = 12.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .weight(1f, fill = false)
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}
