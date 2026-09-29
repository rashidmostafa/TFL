package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/**
 * Pill-shaped search input on surfaceContainer, with a clear button once something is typed.
 *
 * Takes a [TextFieldState] (usually owned by the ViewModel and observed with `snapshotFlow`) so
 * keystrokes are never lost to an asynchronous state round-trip.
 */
@Composable
fun TflSearchField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val colors = TflTheme.colors
    val hasText = state.text.isNotEmpty()
    BasicTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp),
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = TflTheme.typography.bodyMd.copy(color = colors.textPrimary),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorator = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(colors.surfaceContainer, TflTheme.shapes.pill)
                    .padding(start = 14.dp, end = 6.dp)
                    .heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TflIcon(MaterialSymbols.Search, contentDescription = null, size = 20.dp, tint = colors.textMuted)
                Box(Modifier.weight(1f)) {
                    if (!hasText) {
                        Text(
                            text = placeholder,
                            style = TflTheme.typography.bodyMd,
                            color = colors.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
                if (hasText) {
                    TflIconButton(
                        symbol = MaterialSymbols.Close,
                        contentDescription = stringResource(R.string.action_clear),
                        onClick = { state.clearText() },
                        containerColor = Color.Transparent,
                        size = 32.dp,
                        iconSize = 18.dp,
                    )
                }
            }
        },
    )
}

/** Single-select filter tab such as "All 7": teal when selected, with an optional count. */
@Composable
fun TflFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    count: Int? = null,
) {
    val colors = TflTheme.colors
    val content = if (selected) colors.onPrimary else colors.textMuted
    Row(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .height(32.dp)
            .clip(TflTheme.shapes.pill)
            .background(if (selected) colors.primary else colors.surfaceContainer)
            .selectable(selected = selected, onClick = onClick, role = Role.Tab)
            .padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) TflIcon(icon, contentDescription = null, size = 14.dp, tint = content)
        Text(label, style = TflTheme.typography.codeMd, color = content, maxLines = 1)
        if (count != null) {
            Text(
                text = count.toString(),
                modifier = Modifier
                    .background(if (selected) colors.onPrimary.copy(alpha = 0.2f) else colors.canvas, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
                style = TflTheme.typography.labelSm.copy(fontSize = 10.sp),
                color = if (selected) colors.onPrimary else colors.primary,
            )
        }
    }
}

/** Material 3 radio button in TFL colours. Put it in a row made `selectable` with `Role.RadioButton`. */
@Composable
fun TflRadioButton(
    selected: Boolean,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TflTheme.colors
    RadioButton(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = RadioButtonDefaults.colors(
            selectedColor = colors.primary,
            unselectedColor = colors.textDim,
            disabledSelectedColor = colors.primary.copy(alpha = 0.4f),
            disabledUnselectedColor = colors.textDim.copy(alpha = 0.4f),
        ),
    )
}

/** Material 3 switch in TFL colours: teal track with a dark thumb when on. */
@Composable
fun TflSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = TflTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = colors.onPrimary,
            checkedTrackColor = colors.primary,
            checkedBorderColor = colors.primary,
            checkedIconColor = colors.primary,
            uncheckedThumbColor = colors.textMuted,
            uncheckedTrackColor = colors.surfaceContainer,
            uncheckedBorderColor = colors.outline,
            disabledCheckedThumbColor = colors.onPrimary,
            disabledCheckedTrackColor = colors.primary.copy(alpha = 0.5f),
            disabledCheckedBorderColor = Color.Transparent,
            disabledUncheckedThumbColor = colors.textDim,
            disabledUncheckedTrackColor = colors.surfaceContainer,
            disabledUncheckedBorderColor = colors.outline,
        ),
    )
}
