package app.tfl.core.session

/**
 * Collects a fixed-length numeric PIN from [app.tfl.core.designsystem.component.PinPad] into byte
 * arrays (ASCII digits), never a String. With [confirm], the PIN is typed twice and must match.
 */
class PinEntry(private val length: Int = 6, private val confirm: Boolean = true) {

    enum class Stage { ENTER, CONFIRM, COMPLETE }

    private val first = ByteArray(length)
    private val second = ByteArray(length)

    var stage: Stage = Stage.ENTER
        private set
    var entered: Int = 0
        private set

    /** The confirmation didn't match; cleared by the next digit. */
    var mismatch: Boolean = false
        private set

    fun digit(value: Int) {
        require(value in 0..9)
        if (stage == Stage.COMPLETE) return
        mismatch = false
        val buffer = if (stage == Stage.ENTER) first else second
        buffer[entered++] = ('0'.code + value).toByte()
        if (entered < length) return
        entered = 0
        stage = when {
            stage == Stage.ENTER && confirm -> Stage.CONFIRM
            stage == Stage.ENTER -> Stage.COMPLETE
            first.contentEquals(second) -> Stage.COMPLETE
            else -> {
                mismatch = true
                first.fill(0)
                second.fill(0)
                Stage.ENTER
            }
        }
    }

    fun delete() {
        if (stage == Stage.COMPLETE || entered == 0) return
        entered--
        (if (stage == Stage.ENTER) first else second)[entered] = 0
    }

    /** A copy of the finished PIN. The caller wipes it. */
    fun value(): ByteArray {
        check(stage == Stage.COMPLETE) { "PIN not finished" }
        return first.copyOf()
    }

    /**
     * True once this PIN has been typed in full (even before its confirmation) and equals [other]'s
     * finished PIN. Lets the duress PIN be refused before it's typed a second time.
     */
    fun sameAs(other: PinEntry): Boolean =
        stage != Stage.ENTER && other.stage == Stage.COMPLETE && first.contentEquals(other.first)

    fun reset() {
        first.fill(0)
        second.fill(0)
        entered = 0
        mismatch = false
        stage = Stage.ENTER
    }
}
