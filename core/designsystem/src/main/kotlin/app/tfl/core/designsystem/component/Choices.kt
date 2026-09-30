package app.tfl.core.designsystem.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.icon.TflIcon
import app.tfl.core.designsystem.theme.TflTheme

/**
 * One option in a single-choice group, as a card: icon, title, optional tag, explanation and a radio.
 * [accent] colours the icon, tag and selection border (e.g. danger for "Wipe everything").
 */
@Composable
fun ChoiceCard(
    title: String,
    body: String,
    selected: Boolean,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier,
    icon: String? = null,
    tag: String? = null,
    accent: Color = TflTheme.colors.primary,
    footnote: (@Composable () -> Unit)? = null,
) {
    val colors = TflTheme.colors
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton),
        shape = TflTheme.shapes.card,
        color = if (selected) colors.surfaceContainer else colors.surface,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.6f) else colors.border),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) IconTile(icon, tint = accent, containerColor = accent.copy(alpha = 0.15f))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = TflTheme.typography.headlineSm, color = colors.textPrimary)
                    if (tag != null) Text(tag.uppercase(), style = TflTheme.typography.labelSm, color = accent)
                }
                TflRadioButton(selected = selected, onClick = null)
            }
            Text(body, style = TflTheme.typography.bodyMd, color = colors.textMuted)
            footnote?.invoke()
        }
    }
}

/** Single-line text input on surfaceContainer with an optional leading icon and a line below it. */
@Composable
fun TflTextField(
    state: TextFieldState,
    placeholder: String,
    modifier: Modifier = Modifier,
    leadingIcon: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    keyboardOptions: KeyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
) {
    val colors = TflTheme.colors
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        BasicTextField(
            state = state,
            modifier = Modifier.fillMaxWidth(),
            lineLimits = TextFieldLineLimits.SingleLine,
            textStyle = TflTheme.typography.bodyLg.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.primary),
            keyboardOptions = keyboardOptions,
            decorator = { innerTextField ->
                Row(
                    modifier = Modifier
                        .background(colors.surfaceContainer, TflTheme.shapes.control)
                        .then(
                            if (isError) Modifier.background(colors.danger.copy(alpha = 0.08f), TflTheme.shapes.control) else Modifier,
                        )
                        .heightIn(min = 56.dp)
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (leadingIcon != null) {
                        TflIcon(leadingIcon, contentDescription = null, size = 22.dp, tint = colors.textMuted)
                    }
                    Box(Modifier.weight(1f)) {
                        if (state.text.isEmpty()) {
                            Text(placeholder, style = TflTheme.typography.bodyLg, color = colors.textDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        innerTextField()
                    }
                }
            },
        )
        if (supportingText != null) {
            Text(supportingText, style = TflTheme.typography.bodySm, color = if (isError) colors.danger else colors.textMuted)
        }
    }
}
