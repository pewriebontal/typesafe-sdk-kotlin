package net.bontal.typesafesdk.internal

import android.os.Build
import android.os.Looper
import net.bontal.typesafesdk.LogLevel
import net.bontal.typesafesdk.TypeSafeLogger
import android.util.Log as AndroidLog

internal actual fun platformRuntime(): String = "android/${Build.VERSION.SDK_INT}"

internal actual fun getEnvironmentVariable(name: String): String? = System.getenv(name)

internal actual fun getSystemProperty(name: String): String? = System.getProperty(name)

internal actual fun checkNotMainThread() {
    val looper = Looper.myLooper()
    check(looper == null || looper != Looper.getMainLooper()) {
        "TypeSafeClient blocks the calling thread and cannot run on the Android main thread; " +
            "use TypeSafeClientAsync or switch to Dispatchers.IO"
    }
}

internal actual fun defaultLogger(): TypeSafeLogger = TypeSafeLogger { level, message ->
    val priority = when (level) {
        LogLevel.DEBUG -> AndroidLog.DEBUG
        LogLevel.INFO -> AndroidLog.INFO
        LogLevel.WARN -> AndroidLog.WARN
        LogLevel.ERROR, LogLevel.OFF -> AndroidLog.ERROR
    }
    AndroidLog.println(priority, "TypeSafe", message)
}
