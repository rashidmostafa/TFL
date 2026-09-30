package app.tfl.feature.onboarding.stealth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.tfl.core.designsystem.component.TflIconButton
import app.tfl.core.designsystem.icon.MaterialSymbols
import app.tfl.core.designsystem.theme.TflTheme
import app.tfl.feature.onboarding.R
import app.tfl.feature.onboarding.stealth.CalculatorEngine.DIVIDE
import app.tfl.feature.onboarding.stealth.CalculatorEngine.MINUS
import app.tfl.feature.onboarding.stealth.CalculatorEngine.PLUS
import app.tfl.feature.onboarding.stealth.CalculatorEngine.TIMES

/** The disguise: an ordinary calculator. Nothing on it says TFL. */
@Composable
internal fun CalculatorRoute(onOpenTfl: () -> Unit, viewModel: CalculatorViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.openTfl.collect { onOpenTfl() } }
    CalculatorScreen(state, onKey = viewModel::press, onEquals = viewModel::equals)
}

@Composable
internal fun CalculatorScreen(
    state: CalculatorUiState,
    onKey: (CalculatorKey) -> Unit,
    onEquals: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TflTheme.colors.canvas)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.weight(1f))
        CalculatorDisplay(state, onDelete = { onKey(CalculatorKey.Delete) })
        CalculatorKeys(onKey, onEquals)
    }
}

@Composable
internal fun CalculatorDisplay(state: CalculatorUiState, onDelete: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val typing = CalculatorInput.display(state.expression)
    val (small, big) = when {
        state.error -> typing to stringResource(R.string.calculator_error)
        state.showingResult -> "" to state.result
        state.result.isNotEmpty() -> typing to state.result
        else -> "" to typing.ifEmpty { "0" }
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.surface, TflTheme.shapes.card)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .heightIn(min = 150.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.Bottom),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = small,
                modifier = Modifier.weight(1f),
                style = TflTheme.typography.codeMd.copy(fontSize = 20.sp, lineHeight = 26.sp),
                color = colors.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TflIconButton(
                symbol = MaterialSymbols.Backspace,
                contentDescription = stringResource(R.string.calculator_delete),
                onClick = onDelete,
                containerColor = Color.Transparent,
                size = 40.dp,
                iconSize = 22.dp,
                autoMirror = true,
            )
        }
        Text(
            text = big,
            modifier = Modifier.fillMaxWidth(),
            style = TflTheme.typography.headlineLg.copy(fontSize = 44.sp, lineHeight = 52.sp),
            color = if (state.error) colors.danger else colors.textPrimary,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun CalculatorKeys(onKey: (CalculatorKey) -> Unit, onEquals: () -> Unit, modifier: Modifier = Modifier) {
    val colors = TflTheme.colors
    val operatorStyle = KeyStyle(colors.mesh, colors.onMesh)
    val functionStyle = KeyStyle(colors.surface, colors.mesh)
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Key("AC", stringResource(R.string.calculator_clear), KeyStyle(colors.surfaceContainer, colors.danger)) { onKey(CalculatorKey.Clear) }
            Key("( )", stringResource(R.string.calculator_parentheses), functionStyle) { onKey(CalculatorKey.Parentheses) }
            Key("%", stringResource(R.string.calculator_percent), functionStyle) { onKey(CalculatorKey.Percent) }
            Key("$DIVIDE", stringResource(R.string.calculator_divide), operatorStyle) { onKey(CalculatorKey.Operator(DIVIDE)) }
        }
        listOf(listOf(7, 8, 9) to TIMES, listOf(4, 5, 6) to MINUS, listOf(1, 2, 3) to PLUS).forEach { (digits, operator) ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                digits.forEach { digit -> Key(digit.toString(), digit.toString()) { onKey(CalculatorKey.Digit(digit)) } }
                val label = when (operator) {
                    TIMES -> R.string.calculator_multiply
                    MINUS -> R.string.calculator_minus
                    else -> R.string.calculator_plus
                }
                Key(operator.toString(), stringResource(label), operatorStyle) { onKey(CalculatorKey.Operator(operator)) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Key("+/−", stringResource(R.string.calculator_sign)) { onKey(CalculatorKey.Sign) }
            Key("0", "0") { onKey(CalculatorKey.Digit(0)) }
            Key(".", stringResource(R.string.calculator_decimal)) { onKey(CalculatorKey.Decimal) }
            Key("=", stringResource(R.string.calculator_equals), KeyStyle(colors.primary, colors.onPrimary), onClick = onEquals)
        }
    }
}

private class KeyStyle(val container: Color, val content: Color)

@Composable
private fun RowScope.Key(
    label: String,
    description: String,
    style: KeyStyle = KeyStyle(TflTheme.colors.surface, TflTheme.colors.textPrimary),
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier
            .weight(1f)
            .aspectRatio(1.35f)
            .semantics { contentDescription = description },
        shape = TflTheme.shapes.card,
        color = style.container,
        contentColor = style.content,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(label, style = TflTheme.typography.headlineMd.copy(fontSize = 26.sp), color = style.content, maxLines = 1)
        }
    }
}
