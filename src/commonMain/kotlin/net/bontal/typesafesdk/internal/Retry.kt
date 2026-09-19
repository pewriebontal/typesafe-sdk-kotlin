package net.bontal.typesafesdk.internal

import io.ktor.http.fromHttpToGmtDate
import io.ktor.util.date.getTimeMillis
import net.bontal.typesafesdk.Headers
import net.bontal.typesafesdk.RetryStrategy
import kotlin.random.Random

internal const val RETRY_COUNT_HEADER: String = "X-TypeSafe-Retry-Count"

internal fun parseRetryAfterMillis(headers: Headers, now: Long = getTimeMillis()): Long? {
    headers["retry-after-ms"]?.trim()?.toDoubleOrNull()?.let { millis ->
        if (millis.isFinite() && millis >= 0) return millis.toLong()
    }
    val raw = headers["retry-after"]?.trim() ?: return null
    raw.toDoubleOrNull()?.let { seconds ->
        return if (seconds.isFinite() && seconds >= 0) (seconds * 1000).toLong() else null
    }
    val date = runCatching { raw.fromHttpToGmtDate().timestamp }.getOrNull() ?: return null
    return (date - now).coerceAtLeast(0L)
}

internal fun retryDelayMillis(
    retryCount: Int,
    headers: Headers?,
    retry: RetryStrategy,
    random: Random = Random,
): Long {
    if (retry.respectRetryAfter && headers != null) {
        val retryAfter = parseRetryAfterMillis(headers)
        if (retryAfter != null && retryAfter <= retry.maxRetryAfterMillis) return retryAfter
    }
    val exponent = (retryCount - 1).coerceIn(0, 30)
    val exponential = (retry.baseDelayMillis * (1L shl exponent)).coerceAtMost(retry.maxDelayMillis)
    return (exponential * (1 - random.nextDouble() * retry.jitter)).toLong()
}
