package app.tfl.core.model.identity

/**
 * The rules for a display name, shared by onboarding (your own name) and by pairing-code checks
 * (a friend's name), so no phone creates a name another would refuse.
 */
object DisplayNames {
    const val MAX_LENGTH = 32

    /** 1–32 characters, no surrounding spaces, and none of the characters in [isForbidden]. */
    fun isValid(name: String): Boolean {
        if (name.isEmpty() || name != name.trim()) return false
        if (name.codePointCount(0, name.length) > MAX_LENGTH) return false
        return name.codePoints().noneMatch(::isForbidden)
    }

    /**
     * Control characters, line breaks, unpaired surrogates, and invisible characters that change
     * how text runs or joins, which could make one name look like another ("Elena" written right to
     * left, hidden spaces). The zero-width joiner and non-joiner stay allowed: emoji sequences and
     * several scripts need them.
     */
    fun isForbidden(codePoint: Int): Boolean = when (Character.getType(codePoint).toByte()) {
        Character.CONTROL, Character.LINE_SEPARATOR, Character.PARAGRAPH_SEPARATOR, Character.SURROGATE -> true
        else -> codePoint in INVISIBLE_FORMATTING
    }

    private val INVISIBLE_FORMATTING: Set<Int> = buildSet {
        add(0x061C) // Arabic letter mark
        add(0x180E) // Mongolian vowel separator
        add(0x200B) // zero-width space
        add(0x200E) // left-to-right mark
        add(0x200F) // right-to-left mark
        addAll(0x202A..0x202E) // embeddings and overrides
        add(0x2060) // word joiner
        addAll(0x2066..0x2069) // isolates
        add(0xFEFF) // zero-width no-break space
        addAll(0xFFF9..0xFFFB) // interlinear annotation
    }
}
