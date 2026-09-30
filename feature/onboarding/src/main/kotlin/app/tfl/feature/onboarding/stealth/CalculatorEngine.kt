package app.tfl.feature.onboarding.stealth

import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

/**
 * The calculator behind the disguise. It really calculates: BigDecimal arithmetic (so 0.1 + 0.2 is
 * 0.3), the usual precedence, parentheses, postfix percent (x% = x / 100) and unary minus.
 *
 * Expressions use the display symbols: digits, `.`, `+ − × ÷`, `( )` and `%`.
 */
object CalculatorEngine {
    const val PLUS = '+'
    const val MINUS = '−'
    const val TIMES = '×'
    const val DIVIDE = '÷'
    private val OPERATORS = setOf(PLUS, MINUS, TIMES, DIVIDE)
    private val MATH = MathContext(16, RoundingMode.HALF_EVEN)
    private const val MAX_DISPLAY_DIGITS = 12

    /** The value of [expression], or null if it's incomplete or invalid (e.g. division by zero). */
    fun evaluate(expression: String): BigDecimal? {
        if (expression.isBlank()) return null
        val closed = expression + ")".repeat((expression.count { it == '(' } - expression.count { it == ')' }).coerceAtLeast(0))
        return try {
            Parser(closed).parse()
        } catch (e: ArithmeticException) {
            null
        } catch (e: IllegalArgumentException) {
            null
        }
    }

    /** True once there's something to calculate: an operator, percent or parentheses beyond a leading minus. */
    fun hasOperation(expression: String): Boolean =
        expression.removePrefix(MINUS.toString()).any { it in OPERATORS || it == '%' || it == '(' || it == ')' }

    fun isOperator(c: Char) = c in OPERATORS

    /** Up to 12 significant digits, digit grouping, no trailing zeros; scientific notation when huge. */
    fun format(value: BigDecimal): String {
        val rounded = value.round(MathContext(MAX_DISPLAY_DIGITS, RoundingMode.HALF_EVEN)).stripTrailingZeros()
        if (rounded.compareTo(BigDecimal.ZERO) == 0) return "0"
        val integerDigits = rounded.precision() - rounded.scale()
        if (integerDigits > MAX_DISPLAY_DIGITS || integerDigits < -8) {
            return rounded.toString().replace("E+", "e").replace("E", "e").replace('-', MINUS)
        }
        val plain = rounded.toPlainString()
        val negative = plain.startsWith('-')
        val unsigned = plain.removePrefix("-")
        val integer = unsigned.substringBefore('.')
        val fraction = unsigned.substringAfter('.', "")
        val grouped = integer.reversed().chunked(3).joinToString(",").reversed()
        return (if (negative) MINUS.toString() else "") + grouped + if (fraction.isEmpty()) "" else ".$fraction"
    }

    /** Recursive descent: expression := term (± term)*; term := factor (×÷ factor)*; factor := −factor | primary %*. */
    private class Parser(private val input: String) {
        private var position = 0

        fun parse(): BigDecimal {
            val value = expression()
            require(position == input.length) { "Unexpected '${input[position]}'" }
            return value
        }

        private fun expression(): BigDecimal {
            var value = term()
            while (position < input.length && (input[position] == PLUS || input[position] == MINUS)) {
                val operator = input[position++]
                val right = term()
                value = if (operator == PLUS) value.add(right, MATH) else value.subtract(right, MATH)
            }
            return value
        }

        private fun term(): BigDecimal {
            var value = factor()
            while (position < input.length && (input[position] == TIMES || input[position] == DIVIDE)) {
                val operator = input[position++]
                val right = factor()
                value = if (operator == TIMES) value.multiply(right, MATH) else value.divide(right, MATH)
            }
            return value
        }

        private fun factor(): BigDecimal {
            if (position < input.length && input[position] == MINUS) {
                position++
                return factor().negate()
            }
            var value = primary()
            while (position < input.length && input[position] == '%') {
                position++
                value = value.divide(BigDecimal(100), MATH)
            }
            return value
        }

        private fun primary(): BigDecimal {
            require(position < input.length) { "Expression ends early" }
            if (input[position] == '(') {
                position++
                val value = expression()
                require(position < input.length && input[position] == ')') { "Missing )" }
                position++
                return value
            }
            val start = position
            while (position < input.length && (input[position].isDigit() || input[position] == '.')) position++
            val number = input.substring(start, position)
            require(number.isNotEmpty() && number != "." && number.count { it == '.' } <= 1) { "Not a number" }
            return BigDecimal(number)
        }
    }
}
