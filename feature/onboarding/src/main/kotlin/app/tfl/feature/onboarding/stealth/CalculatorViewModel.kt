package app.tfl.feature.onboarding.stealth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.tfl.core.session.CalculatorDisguise
import app.tfl.core.session.di.WorkDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class CalculatorUiState(
    /** The expression as typed (display form comes from [CalculatorInput.display]). */
    val expression: String = "",
    /** Live result while typing, or the result after "=". */
    val result: String = "",
    val error: Boolean = false,
    /** After "=", the next digit starts a new calculation. */
    val showingResult: Boolean = false,
)

@HiltViewModel
class CalculatorViewModel @Inject constructor(
    private val disguise: CalculatorDisguise,
    @param:WorkDispatcher private val work: CoroutineDispatcher,
) : ViewModel() {

    private val state = MutableStateFlow(CalculatorUiState())
    val uiState: StateFlow<CalculatorUiState> = state.asStateFlow()

    private val unlockRequests = Channel<Unit>(Channel.CONFLATED)

    /** Emits when the secret code was entered followed by "=". */
    val openTfl = unlockRequests.receiveAsFlow()

    fun press(key: CalculatorKey) = state.update { CalculatorReducer.press(it, key) }

    fun equals() {
        val expression = state.value.expression
        if (expression.length == CODE_LENGTH && expression.all { it.isDigit() }) {
            viewModelScope.launch {
                if (withContext(work) { disguise.matches(expression.encodeToByteArray()) }) {
                    state.value = CalculatorUiState()
                    unlockRequests.send(Unit)
                } else {
                    state.update(CalculatorReducer::equals)
                }
            }
        } else {
            state.update(CalculatorReducer::equals)
        }
    }

    private companion object {
        const val CODE_LENGTH = 6
    }
}

/** The calculator's state changes, shared by the disguise and the onboarding preview. */
internal object CalculatorReducer {

    fun press(current: CalculatorUiState, key: CalculatorKey): CalculatorUiState {
        val base = if (current.showingResult && (key is CalculatorKey.Digit || key == CalculatorKey.Decimal)) "" else current.expression
        val expression = CalculatorInput.press(base, key)
        return CalculatorUiState(expression = expression, result = preview(expression))
    }

    fun equals(current: CalculatorUiState): CalculatorUiState {
        val expression = current.expression
        if (expression.isEmpty()) return current
        val value = CalculatorEngine.evaluate(expression) ?: return CalculatorUiState(expression = expression, error = true)
        val plain = value.stripTrailingZeros().toPlainString().replace('-', CalculatorEngine.MINUS)
        return CalculatorUiState(expression = plain, result = CalculatorEngine.format(value), showingResult = true)
    }

    private fun preview(expression: String): String =
        if (CalculatorEngine.hasOperation(expression)) CalculatorEngine.evaluate(expression)?.let(CalculatorEngine::format).orEmpty() else ""
}
