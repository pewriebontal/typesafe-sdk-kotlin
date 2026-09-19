package net.bontal.typesafesdk.internal

import kotlin.time.Duration

internal fun requireValidTimeout(timeout: Duration) {
    require(timeout.inWholeMilliseconds >= 1) { "timeout must be at least 1 millisecond, was $timeout" }
}

internal fun requireValidHeaders(headers: Map<String, String>) {
    for ((name, value) in headers) {
        require(name.isNotEmpty() && name.all { it in '!'..'~' && it != ':' }) { "Invalid header name `$name`" }
        require(value.none { it == '\r' || it == '\n' || it == '\u0000' }) { "Header `$name` contains a line break or NUL character" }
    }
}
