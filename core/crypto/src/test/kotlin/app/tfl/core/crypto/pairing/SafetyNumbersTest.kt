package app.tfl.core.crypto.pairing

import app.tfl.core.crypto.hexToBytes
import app.tfl.core.testing.crypto.JvmSodium
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyNumbersTest {

    private val sodium = JvmSodium.api
    private val safetyNumbers = SafetyNumbers(sodium)

    /** Public keys from RFC 8032 tests 1 and 2. */
    private val a = "d75a980182b10ab7d54bfed3c964073a0ee172f3daa62325af021a68f707511a".hexToBytes()
    private val b = "3d4017c3e843895a92b70aa74d1b7ebc9c982ccf2ec4968cc0cd55f12af4660c".hexToBytes()

    @Test
    fun `both phones get the same number whichever way round`() {
        assertEquals(safetyNumbers.between(a, b), safetyNumbers.between(b, a))
        assertEquals(safetyNumbers.between(a, b).groups, safetyNumbers.between(b, a).groups)
    }

    /** Computed independently with Python's hashlib.blake2b(digest_size=60) and the same digit rule. */
    @Test
    fun `matches the independently computed value`() {
        assertEquals(
            "01953 97817 65409 49173 11172 35446 44819 72146 06261 49463 63610 08650",
            safetyNumbers.between(a, b).groups.joinToString(" "),
        )
    }

    @Test
    fun `twelve groups of five digits`() {
        val number = safetyNumbers.between(sodium.randomBytes(32), sodium.randomBytes(32))
        assertEquals(12, number.groups.size)
        assertTrue(number.groups.all { group -> group.length == 5 && group.all { it.isDigit() } })
    }

    @Test
    fun `any other key changes the number`() {
        val c = sodium.randomBytes(32)
        assertNotEquals(safetyNumbers.between(a, b), safetyNumbers.between(a, c))
        assertNotEquals(safetyNumbers.between(a, b), safetyNumbers.between(c, b))
    }

    @Test
    fun `the comparison code matches only the same pair of keys`() {
        val number = safetyNumbers.between(a, b)
        val code = safetyNumbers.comparisonCode(safetyNumbers.between(b, a))
        assertTrue(code.startsWith("TFL-SN1:"))
        assertEquals(ComparisonResult.MATCH, safetyNumbers.compare(number, code))
        assertEquals(ComparisonResult.MISMATCH, safetyNumbers.compare(safetyNumbers.between(a, sodium.randomBytes(32)), code))
        assertEquals(ComparisonResult.NOT_A_COMPARISON_CODE, safetyNumbers.compare(number, "TFL-PAIR1:AAAA"))
        assertEquals(ComparisonResult.NOT_A_COMPARISON_CODE, safetyNumbers.compare(number, "TFL-SN1:%%%"))
        assertEquals(ComparisonResult.NOT_A_COMPARISON_CODE, safetyNumbers.compare(number, "TFL-SN1:" + code.takeLast(10)))
    }
}
