package net.bontal.typesafesdk

public enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
    OFF,
}

public fun interface TypeSafeLogger {
    public fun log(level: LogLevel, message: String)
}
