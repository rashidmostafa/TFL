package app.tfl.feature.onboarding.stealth

import app.tfl.feature.onboarding.stealth.CalculatorEngine.MINUS
import app.tfl.feature.onboarding.stealth.CalculatorEngine.TIMES

sealed interface CalculatorKey {
    data class Digit(val digit: Int) : CalculatorKey
    data class Operator(val symbol: Char) : CalculatorKey
    data object Decimal : CalculatorKey
    data object Parentheses : CalculatorKey
    data object Percent : CalculatorKey
    data object Sign : CalculatorKey
    data object Delete : CalculatorKey
    data object Clear : CalculatorKey
}

/** What each key does to the expression, like a phone calculator. Pure, so it's easy to test. */
object CalculatorInput {

    fun press(expression: String, key: CalculatorKey): String = when (key) {
        is CalculatorKey.Digit -> if (endsWithCloser(expression)) "$expression$TIMES${key.digit}" else "$expression${key.digit}"
        is CalculatorKey.Operator -> operator(expression, key.symbol)
        CalculatorKey.Decimal -> decimal(expression)
        CalculatorKey.Parentheses -> parentheses(expression)
        CalculatorKey.Percent -> if (expression.lastOrNull()?.let { it.isDigit() || it == ')' } == true) "$expression%" else expression
        CalculatorKey.Sign -> toggleSign(expression)
        CalculatorKey.Delete -> expression.dropLast(1)
        CalculatorKey.Clear -> ""
    }

    private fun endsWithCloser(expression: String) = expression.lastOrNull().let { it == ')' || it == '%' }

    private fun operator(expression: String, symbol: Char): String {
        val last = expression.lastOrNull() ?: return if (symbol == MINUS) MINUS.toString() else ""
        return when {
            // "5×" then "−" starts a negative number; any other operator replaces the previous one.
            CalculatorEngine.isOperator(last) ->
                if (symbol == MINUS && last != MINUS && last != '+') "$expression$MINUS"
                else if (expression.length == 1) expression
                else trimOperators(expression) + symbol
            last == '(' -> if (symbol == MINUS) "$expression$MINUS" else expression
            else -> "$expression$symbol"
        }
    }

    private fun trimOperators(expression: String): String = expression.trimEnd { CalculatorEngine.isOperator(it) }

    private fun decimal(expression: String): String {
        val number = expression.takeLastWhile { it.isDigit() || it == '.' }
        return when {
            number.contains('.') -> expression
            number.isEmpty() -> (if (endsWithCloser(expression)) "$expression$TIMES" else expression) + "0."
            else -> "$expression."
        }
    }

    /** One key for both: closes a group when one is open and a value was just typed, else opens one. */
    private fun parentheses(expression: String): String {
        val open = expression.count { it == '(' } - expression.count { it == ')' }
        val last = expression.lastOrNull()
        val afterValue = last != null && (last.isDigit() || last == ')' || last == '%' || last == '.')
        return when {
            open > 0 && afterValue -> "$expression)"
            afterValue -> "$expression$TIMES("
            else -> "$expression("
        }
    }

    /** Negates the number being typed (or starts a negative one). */
    private fun toggleSign(expression: String): String {
        val numberStart = expression.length - expression.takeLastWhile { it.isDigit() || it == '.' }.length
        if (numberStart == expression.length) {
            return if (expression.lastOrNull() == MINUS && isUnaryMinusAt(expression, expression.length - 1)) {
                expression.dropLast(1)
            } else if (expression.isEmpty() || CalculatorEngine.isOperator(expression.last()) || expression.last() == '(') {
                "$expression$MINUS"
            } else {
                expression
            }
        }
        val minusIndex = numberStart - 1
        return if (minusIndex >= 0 && expression[minusIndex] == MINUS && isUnaryMinusAt(expression, minusIndex)) {
            expression.removeRange(minusIndex, numberStart)
        } else {
            expression.substring(0, numberStart) + MINUS + expression.substring(numberStart)
        }
    }

    private fun isUnaryMinusAt(expression: String, index: Int): Boolean =
        index == 0 || expression[index - 1] == '(' || CalculatorEngine.isOperator(expression[index - 1])

    /** The expression as shown: digit grouping in each number, spaces around binary operators. */
    fun display(expression: String): String {
        val out = StringBuilder()
        var index = 0
        while (index < expression.length) {
            val c = expression[index]
            if (c.isDigit() || c == '.') {
                val number = expression.substring(index).takeWhile { it.isDigit() || it == '.' }
                val integer = number.substringBefore('.')
                out.append(integer.reversed().chunked(3).joinToString(",").reversed())
                if (number.contains('.')) out.append('.').append(number.substringAfter('.'))
                index += number.length
                continue
            }
            if (CalculatorEngine.isOperator(c) && !isUnaryMinusAt(expression, index)) out.append(' ').append(c).append(' ') else out.append(c)
            index++
        }
        return out.toString()
    }
}
