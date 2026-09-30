package app.tfl.feature.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.KeyboardActionHandler
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.component.PillButton
import app.tfl.core.designsystem.component.TflBackButton
import app.tfl.core.designsystem.theme.TflTheme

/**
 * An onboarding page: optional back button, scrolling content, and actions pinned at the bottom
 * (above the keyboard when it's open).
 */
@Composable
internal fun OnboardingPage(
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    bottom: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        if (onBack != null) {
            Box(Modifier.padding(start = 8.dp, top = 8.dp)) {
                TflBackButton(onClick = onBack)
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            content = content,
        )
        if (bottom != null) {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                content = bottom,
            )
        }
    }
}

/** Page title and explanation. */
@Composable
internal fun PageTitle(title: String, body: String? = null) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = TflTheme.typography.headlineLgMobile, color = TflTheme.colors.textPrimary)
        if (body != null) Text(body, style = TflTheme.typography.bodyLg, color = TflTheme.colors.textMuted)
    }
}

/**
 * One recovery-phrase word. Password keyboard type: standard keyboards neither suggest nor learn
 * what's typed here. The text isn't masked, so people can check each word.
 */
@Composable
internal fun WordField(
    state: TextFieldState,
    number: Int,
    focusRequester: FocusRequester,
    onFocused: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    isError: Boolean = false,
    isLast: Boolean = false,
) {
    val colors = TflTheme.colors
    var focused by remember { mutableStateOf(false) }
    val label = stringResource(R.string.restore_word_label, number)
    BasicTextField(
        state = state,
        modifier = modifier
            .fillMaxWidth()
            .focusRequester(focusRequester)
            .onFocusChanged {
                focused = it.isFocused
                if (it.isFocused) onFocused()
            }
            .semantics { contentDescription = label },
        lineLimits = TextFieldLineLimits.SingleLine,
        textStyle = TflTheme.typography.codeMd.copy(color = colors.primary),
        cursorBrush = SolidColor(colors.primary),
        keyboardOptions = KeyboardOptions(
            capitalization = KeyboardCapitalization.None,
            autoCorrectEnabled = false,
            keyboardType = KeyboardType.Password,
            imeAction = if (isLast) ImeAction.Done else ImeAction.Next,
        ),
        onKeyboardAction = KeyboardActionHandler { onNext() },
        decorator = { innerTextField ->
            Row(
                modifier = Modifier
                    .background(colors.surfaceContainer, TflTheme.shapes.keyBlock)
                    .border(
                        1.dp,
                        when {
                            isError -> colors.danger
                            focused -> colors.primary
                            else -> colors.border
                        },
                        TflTheme.shapes.keyBlock,
                    )
                    .heightIn(min = 44.dp)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("%02d".format(number), style = TflTheme.typography.labelSm, color = colors.textMuted)
                Box(Modifier.width(10.dp))
                Box(Modifier.weight(1f)) { innerTextField() }
            }
        },
    )
}

/** Word-list completions for what's typed in a [WordField]; nothing once it's a complete word. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun WordSuggestions(
    typed: String,
    suggestions: (String) -> List<String>,
    isWord: (String) -> Boolean,
    onPick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val prefix = typed.trim().lowercase()
    val options = if (prefix.isNotEmpty() && !isWord(prefix)) suggestions(prefix) else emptyList()
    if (options.isEmpty()) return
    FlowRow(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { word -> PillButton(word, onClick = { onPick(word) }) }
    }
}
