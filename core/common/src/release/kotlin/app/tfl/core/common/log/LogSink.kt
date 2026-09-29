@file:Suppress("UNUSED_PARAMETER")

package app.tfl.core.common.log

/** Release builds: logging does nothing and message lambdas are never called. */
internal object LogSink {
    const val ENABLED = false

    fun debug(tag: String, message: () -> String) = Unit

    fun info(tag: String, message: () -> String) = Unit

    fun warn(tag: String, throwable: Throwable?, message: () -> String) = Unit

    fun error(tag: String, throwable: Throwable?, message: () -> String) = Unit
}
