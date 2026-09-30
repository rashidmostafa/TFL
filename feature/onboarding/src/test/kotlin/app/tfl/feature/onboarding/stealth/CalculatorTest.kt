package app.tfl.feature.onboarding.stealth

import app.tfl.feature.onboarding.stealth.CalculatorEngine.DIVIDE
import app.tfl.feature.onboarding.stealth.CalculatorEngine.MINUS
import app.tfl.feature.onboarding.stealth.CalculatorEngine.PLUS
import app.tfl.feature.onboarding.stealth.CalculatorEngine.TIMES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class CalculatorEngineTest {

    private fun value(expression: String): BigDecimal? = CalculatorEngine.evaluate(expression)

    private fun assertValue(expected: String, expression: String) {
        val actual = value(expression)
        assertTrue("$expression = $actual, expected $expected", actual != null && actual.compareTo(BigDecimal(expected)) == 0)
    }

    @Test
    fun `multiplication and division bind tighter than addition and subtraction`() {
        assertValue("14", "2${PLUS}3${TIMES}4")
        assertValue("1", "7${MINUS}3${TIMES}2")
        assertValue("5", "20${DIVIDE}4")
        assertValue("2", "8${DIVIDE}2${DIVIDE}2")
    }

    @Test
    fun `parentheses group and close themselves`() {
        assertValue("20", "(2${PLUS}3)${TIMES}4")
        assertValue("5", "(2${PLUS}3")
        assertValue("21", "3${TIMES}(2${PLUS}(4${PLUS}1")
    }

    @Test
    fun `decimals are exact`() {
        assertValue("0.3", "0.1${PLUS}0.2")
        assertValue("0.3333333333333333", "1${DIVIDE}3")
    }

    @Test
    fun `percent divides by a hundred`() {
        assertValue("0.5", "50%")
        assertValue("20", "200${TIMES}10%")
    }

    @Test
    fun `unary minus`() {
        assertValue("2", "${MINUS}3${PLUS}5")
        assertValue("-6", "2${TIMES}${MINUS}3")
        assertValue("3", "${MINUS}${MINUS}3")
    }

    @Test
    fun `invalid or incomplete expressions have no value`() {
        assertNull(value(""))
        assertNull(value("1${DIVIDE}0"))
        assertNull(value("0${DIVIDE}0"))
        assertNull(value("5${PLUS}"))
        assertNull(value("1..2"))
        assertNull(value("()"))
    }

    @Test
    fun `an operation needs more than a leading minus`() {
        assertFalse(CalculatorEngine.hasOperation("12"))
        assertFalse(CalculatorEngine.hasOperation("${MINUS}12"))
        assertTrue(CalculatorEngine.hasOperation("1${PLUS}2"))
        assertTrue(CalculatorEngine.hasOperation("12%"))
    }

    @Test
    fun `results are grouped, trimmed and rounded to 12 significant digits`() {
        assertEquals("1,234,567", CalculatorEngine.format(BigDecimal("1234567")))
        assertEquals("${MINUS}1,234.5", CalculatorEngine.format(BigDecimal("-1234.50")))
        assertEquals("0.3", CalculatorEngine.format(BigDecimal("0.30")))
        assertEquals("0", CalculatorEngine.format(BigDecimal("0.000")))
        assertEquals("0.333333333333", CalculatorEngine.format(BigDecimal("0.3333333333333333")))
        assertEquals("1e15", CalculatorEngine.format(BigDecimal("1E+15")))
        assertEquals("1e${MINUS}10", CalculatorEngine.format(BigDecimal("1E-10")))
    }
}

class CalculatorInputTest {

    private fun type(vararg keys: CalculatorKey): String = keys.fold("") { expression, key -> CalculatorInput.press(expression, key) }

    private fun digit(value: Int) = CalculatorKey.Digit(value)
    private fun op(symbol: Char) = CalculatorKey.Operator(symbol)

    @Test
    fun `operators replace each other, except minus which starts a negative number`() {
        assertEquals("5$TIMES", type(digit(5), op(PLUS), op(TIMES)))
        assertEquals("5$TIMES$MINUS", type(digit(5), op(TIMES), op(MINUS)))
        assertEquals("5$PLUS", type(digit(5), op(MINUS), op(PLUS)))
        assertEquals("", type(op(TIMES)))
        assertEquals("$MINUS", type(op(MINUS)))
    }

    @Test
    fun `one parentheses key opens and closes groups, multiplying implicitly`() {
        assertEquals("2$TIMES(", type(digit(2), CalculatorKey.Parentheses))
        assertEquals("2$TIMES(3)", type(digit(2), CalculatorKey.Parentheses, digit(3), CalculatorKey.Parentheses))
        assertEquals("(3)${TIMES}4", type(CalculatorKey.Parentheses, digit(3), CalculatorKey.Parentheses, digit(4)))
    }

    @Test
    fun `decimal point`() {
        assertEquals("0.", type(CalculatorKey.Decimal))
        assertEquals("1.5", type(digit(1), CalculatorKey.Decimal, digit(5), CalculatorKey.Decimal))
        assertEquals("50%${TIMES}0.", type(digit(5), digit(0), CalculatorKey.Percent, CalculatorKey.Decimal))
    }

    @Test
    fun `sign toggles the number being typed`() {
        assertEquals("${MINUS}12", type(digit(1), digit(2), CalculatorKey.Sign))
        assertEquals("12", type(digit(1), digit(2), CalculatorKey.Sign, CalculatorKey.Sign))
        assertEquals("3$PLUS${MINUS}12", type(digit(3), op(PLUS), digit(1), digit(2), CalculatorKey.Sign))
        // 3 − 12, then ± gives 3 − (−12).
        assertEquals("3${MINUS}${MINUS}12", type(digit(3), op(MINUS), digit(1), digit(2), CalculatorKey.Sign))
    }

    @Test
    fun `percent needs a value before it`() {
        assertEquals("", type(CalculatorKey.Percent))
        assertEquals("50%", type(digit(5), digit(0), CalculatorKey.Percent))
    }

    @Test
    fun `delete and clear`() {
        assertEquals("12", type(digit(1), digit(2), digit(3), CalculatorKey.Delete))
        assertEquals("", type(digit(1), digit(2), CalculatorKey.Clear))
    }

    @Test
    fun `display groups digits and spaces binary operators`() {
        assertEquals("1,234,567 $PLUS 2", CalculatorInput.display("1234567${PLUS}2"))
        assertEquals("${MINUS}5 $TIMES ${MINUS}3", CalculatorInput.display("${MINUS}5$TIMES${MINUS}3"))
        assertEquals("1,000.25", CalculatorInput.display("1000.25"))
    }
}

class CalculatorReducerTest {

    private fun typed(expression: String) = CalculatorUiState(expression = expression)

    @Test
    fun `typing shows a live result once there's an operation`() {
        val state = CalculatorReducer.press(typed("12${TIMES}"), CalculatorKey.Digit(3))
        assertEquals("12${TIMES}3", state.expression)
        assertEquals("36", state.result)
        assertEquals("", CalculatorReducer.press(CalculatorUiState(), CalculatorKey.Digit(3)).result)
    }

    @Test
    fun `equals shows the result, and the next digit starts a new calculation`() {
        val result = CalculatorReducer.equals(typed("1500${PLUS}500"))
        assertTrue(result.showingResult)
        assertEquals("2,000", result.result)
        assertEquals("2000", result.expression)
        assertEquals("7", CalculatorReducer.press(result, CalculatorKey.Digit(7)).expression)
        assertEquals("2000$PLUS", CalculatorReducer.press(result, CalculatorKey.Operator(PLUS)).expression)
    }

    @Test
    fun `negative results continue with a calculator minus`() {
        val result = CalculatorReducer.equals(typed("2${MINUS}5"))
        assertEquals("${MINUS}3", result.expression)
        assertEquals("${MINUS}1", CalculatorReducer.equals(CalculatorReducer.press(CalculatorReducer.press(result, CalculatorKey.Operator(PLUS)), CalculatorKey.Digit(2))).result)
    }

    @Test
    fun `an impossible calculation shows an error`() {
        val state = CalculatorReducer.equals(typed("1${DIVIDE}0"))
        assertTrue(state.error)
        assertEquals("1${DIVIDE}0", state.expression)
        assertEquals(CalculatorUiState(), CalculatorReducer.equals(CalculatorUiState()))
    }
}
