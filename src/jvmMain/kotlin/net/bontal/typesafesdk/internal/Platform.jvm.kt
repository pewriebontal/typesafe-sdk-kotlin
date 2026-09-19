package net.bontal.typesafesdk.internal

import net.bontal.typesafesdk.LogLevel
import net.bontal.typesafesdk.TypeSafeLogger

internal actual fun platformRuntime(): String = "jvm/${System.getProperty("java.version") ?: "unknown"}"

internal actual fun getEnvironmentVariable(name: String): String? = System.getenv(name)

internal actual fun getSystemProperty(name: String): String? = System.getProperty(name)

internal actual fun checkNotMainThread() {}

internal actual fun defaultLogger(): TypeSafeLogger {
    val logger = System.getLogger("net.bontal.typesafesdk")
    return TypeSafeLogger { level, message ->
        val systemLevel = when (level) {
            LogLevel.DEBUG -> System.Logger.Level.DEBUG
            LogLevel.INFO -> System.Logger.Level.INFO
            LogLevel.WARN -> System.Logger.Level.WARNING
            LogLevel.ERROR -> System.Logger.Level.ERROR
            LogLevel.OFF -> System.Logger.Level.OFF
        }
        logger.log(systemLevel, message)
    }
}
