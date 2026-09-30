package app.tfl.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import app.tfl.core.designsystem.R
import app.tfl.core.designsystem.theme.TflTheme

/**
 * Numbered words in two columns (1–12 left, 13–24 right, the order people write them down).
 * [hidden] masks every word, e.g. while someone might be looking over your shoulder.
 */
@Composable
fun PhraseWordGrid(words: List<String>, modifier: Modifier = Modifier, hidden: Boolean = false) {
    val half = (words.size + 1) / 2
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        listOf(0 until half, half until words.size).forEach { range ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                range.forEach { index -> WordCell(index + 1, words[index], hidden) }
            }
        }
    }
}

@Composable
private fun WordCell(number: Int, word: String, hidden: Boolean) {
    val colors = TflTheme.colors
    val hiddenLabel = stringResource(R.string.phrase_word_hidden, number)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(colors.surfaceContainer, TflTheme.shapes.keyBlock)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .then(if (hidden) Modifier.semantics { contentDescription = hiddenLabel } else Modifier),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("%02d".format(number), style = TflTheme.typography.labelSm, color = colors.textMuted)
        Spacer(Modifier.width(10.dp))
        Text(
            text = if (hidden) "•".repeat(6) else word,
            style = TflTheme.typography.codeMd,
            color = colors.primary,
            maxLines = 1,
        )
    }
}
