package net.bontal.typesafesdk.internal

import net.bontal.typesafesdk.LogLevel
import net.bontal.typesafesdk.TypeSafeConfig

internal class Log(val config: TypeSafeConfig) {

    fun isEnabled(level: LogLevel): Boolean = level != LogLevel.OFF && config.logLevel != LogLevel.OFF && level >= config.logLevel

    inline fun debug(message: () -> String) = log(LogLevel.DEBUG, message)

    inline fun info(message: () -> String) = log(LogLevel.INFO, message)

    inline fun warn(message: () -> String) = log(LogLevel.WARN, message)

    inline fun log(level: LogLevel, message: () -> String) {
        if (isEnabled(level)) config.logger.log(level, message())
    }
}

internal fun redactHeaders(headers: List<Pair<String, String>>): String = headers.joinToString(prefix = "{", postfix = "}") { (name, value) ->
    "$name: ${if (isSecretHeader(name)) "<redacted>" else value}"
}

private fun isSecretHeader(name: String): Boolean {
    val normalized = name.lowercase().replace('_', '-')
    return SECRET_HEADER_MARKERS.any { it in normalized }
}

private val SECRET_HEADER_MARKERS = listOf("auth", "cookie", "api-key", "apikey", "token", "secret", "password", "session")
