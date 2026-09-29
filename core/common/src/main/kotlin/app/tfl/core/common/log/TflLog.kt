package app.tfl.core.common.log

/**
 * TFL's only logging entry point.
 *
 * Messages are lambdas, so nothing is formatted unless the build actually logs. Release builds
 * compile a no-op sink (`src/release`), so a message is never even built there, and R8 also strips
 * any `android.util.Log` call left in the release APK.
 *
 * Never log plaintext, keys, passphrases or decrypted content, not even in debug builds.
 */
object TflLog {
    /** False in release builds. */
    val isEnabled: Boolean get() = LogSink.ENABLED

    fun d(tag: String, message: () -> String) = LogSink.debug(tag, message)

    fun i(tag: String, message: () -> String) = LogSink.info(tag, message)

    fun w(tag: String, throwable: Throwable? = null, message: () -> String) = LogSink.warn(tag, throwable, message)

    fun e(tag: String, throwable: Throwable? = null, message: () -> String) = LogSink.error(tag, throwable, message)
}
