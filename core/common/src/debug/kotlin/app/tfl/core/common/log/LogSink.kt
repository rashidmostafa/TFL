package app.tfl.core.common.log

import android.util.Log

/** Debug builds: writes to logcat. */
internal object LogSink {
    const val ENABLED = true

    fun debug(tag: String, message: () -> String) {
        Log.d(tag, message())
    }

    fun info(tag: String, message: () -> String) {
        Log.i(tag, message())
    }

    fun warn(tag: String, throwable: Throwable?, message: () -> String) {
        Log.w(tag, message(), throwable)
    }

    fun error(tag: String, throwable: Throwable?, message: () -> String) {
        Log.e(tag, message(), throwable)
    }
}
