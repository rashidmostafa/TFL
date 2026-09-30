package app.tfl.core.model.identity

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DisplayNamesTest {

    @Test
    fun `ordinary names in any script are fine`() {
        listOf("Alice", "Dr. Maya Lin", "Kaelen (Valkyrie)", "রাশিদ", "مهسا‌نور", "🦊 Fox", "👨‍👩‍👧 Family", "A".repeat(32))
            .forEach { assertTrue(it, DisplayNames.isValid(it)) }
    }

    @Test
    fun `empty, padded or too long names are refused`() {
        listOf("", " ", " Alice", "Alice ", "A".repeat(33)).forEach { assertFalse("\"$it\"", DisplayNames.isValid(it)) }
    }

    @Test
    fun `length counts characters, not UTF-16 units`() {
        assertTrue(DisplayNames.isValid("🦊".repeat(32)))
        assertFalse(DisplayNames.isValid("🦊".repeat(33)))
    }

    @Test
    fun `control and invisible direction characters are refused`() {
        listOf("Ele‮na", "Two\nlines", "Tab\there", "Zero​space", "Mark‏ed", "Iso⁧late", "Bom﻿", "Half\uD800")
            .forEach { assertFalse(it, DisplayNames.isValid(it)) }
    }
}
