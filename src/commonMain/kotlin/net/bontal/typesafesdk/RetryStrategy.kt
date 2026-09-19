package net.bontal.typesafesdk

import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

public class RetryStrategy private constructor(
    public val maxRetries: Int,
    public val baseDelay: Duration,
    public val maxDelay: Duration,
    public val jitter: Double,
    retryableStatuses: Set<Int>,
    public val respectRetryAfter: Boolean,
    public val maxRetryAfter: Duration,
    public val retryConnectionErrors: Boolean,
    public val retryTimeouts: Boolean,
    public val totalTimeout: Duration?,
) {
    init {
        require(maxRetries >= 0) { "maxRetries must not be negative, was $maxRetries" }
        require(!baseDelay.isNegative()) { "baseDelay must not be negative, was $baseDelay" }
        require(!maxDelay.isNegative()) { "maxDelay must not be negative, was $maxDelay" }
        require(jitter in 0.0..1.0) { "jitter must be between 0 and 1, was $jitter" }
        require(retryableStatuses.all { it in 100..599 }) { "retryableStatuses must be HTTP status codes, was $retryableStatuses" }
        require(!maxRetryAfter.isNegative()) { "maxRetryAfter must not be negative, was $maxRetryAfter" }
        require(totalTimeout == null || totalTimeout.isPositive()) { "totalTimeout must be positive, was $totalTimeout" }
    }

    public val retryableStatuses: Set<Int> = retryableStatuses.toSet()

    public val baseDelayMillis: Long get() = baseDelay.inWholeMilliseconds

    public val maxDelayMillis: Long get() = maxDelay.inWholeMilliseconds

    public val maxRetryAfterMillis: Long get() = maxRetryAfter.inWholeMilliseconds

    public val totalTimeoutMillis: Long? get() = totalTimeout?.inWholeMilliseconds

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RetryStrategy) return false
        return maxRetries == other.maxRetries &&
            baseDelay == other.baseDelay &&
            maxDelay == other.maxDelay &&
            jitter == other.jitter &&
            retryableStatuses == other.retryableStatuses &&
            respectRetryAfter == other.respectRetryAfter &&
            maxRetryAfter == other.maxRetryAfter &&
            retryConnectionErrors == other.retryConnectionErrors &&
            retryTimeouts == other.retryTimeouts &&
            totalTimeout == other.totalTimeout
    }

    override fun hashCode(): Int {
        var result = maxRetries
        result = 31 * result + baseDelay.hashCode()
        result = 31 * result + maxDelay.hashCode()
        result = 31 * result + jitter.hashCode()
        result = 31 * result + retryableStatuses.hashCode()
        result = 31 * result + respectRetryAfter.hashCode()
        result = 31 * result + maxRetryAfter.hashCode()
        result = 31 * result + retryConnectionErrors.hashCode()
        result = 31 * result + retryTimeouts.hashCode()
        result = 31 * result + (totalTimeout?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "RetryStrategy(maxRetries=$maxRetries, baseDelay=$baseDelay, maxDelay=$maxDelay, jitter=$jitter, " +
        "retryableStatuses=$retryableStatuses, respectRetryAfter=$respectRetryAfter, maxRetryAfter=$maxRetryAfter, " +
        "retryConnectionErrors=$retryConnectionErrors, retryTimeouts=$retryTimeouts, totalTimeout=$totalTimeout)"

    public fun toBuilder(): Builder = Builder()
        .maxRetries(maxRetries)
        .baseDelay(baseDelay)
        .maxDelay(maxDelay)
        .jitter(jitter)
        .retryableStatuses(retryableStatuses)
        .respectRetryAfter(respectRetryAfter)
        .maxRetryAfter(maxRetryAfter)
        .retryConnectionErrors(retryConnectionErrors)
        .retryTimeouts(retryTimeouts)
        .totalTimeout(totalTimeout)

    public class Builder internal constructor() {
        private var maxRetries: Int = 2
        private var baseDelay: Duration = 500.milliseconds
        private var maxDelay: Duration = 5.seconds
        private var jitter: Double = 0.25
        private var retryableStatuses: Set<Int> = DEFAULT_RETRYABLE_STATUSES
        private var respectRetryAfter: Boolean = true
        private var maxRetryAfter: Duration = 60.seconds
        private var retryConnectionErrors: Boolean = true
        private var retryTimeouts: Boolean = true
        private var totalTimeout: Duration? = 30.seconds

        public fun maxRetries(maxRetries: Int): Builder = apply { this.maxRetries = maxRetries }

        public fun baseDelay(baseDelay: Duration): Builder = apply { this.baseDelay = baseDelay }

        public fun baseDelayMillis(baseDelayMillis: Long): Builder = baseDelay(baseDelayMillis.milliseconds)

        public fun maxDelay(maxDelay: Duration): Builder = apply { this.maxDelay = maxDelay }

        public fun maxDelayMillis(maxDelayMillis: Long): Builder = maxDelay(maxDelayMillis.milliseconds)

        public fun jitter(jitter: Double): Builder = apply { this.jitter = jitter }

        public fun retryableStatuses(retryableStatuses: Set<Int>): Builder = apply { this.retryableStatuses = retryableStatuses.toSet() }

        public fun respectRetryAfter(respectRetryAfter: Boolean): Builder = apply { this.respectRetryAfter = respectRetryAfter }

        public fun maxRetryAfter(maxRetryAfter: Duration): Builder = apply { this.maxRetryAfter = maxRetryAfter }

        public fun maxRetryAfterMillis(maxRetryAfterMillis: Long): Builder = maxRetryAfter(maxRetryAfterMillis.milliseconds)

        public fun retryConnectionErrors(retryConnectionErrors: Boolean): Builder = apply { this.retryConnectionErrors = retryConnectionErrors }

        public fun retryTimeouts(retryTimeouts: Boolean): Builder = apply { this.retryTimeouts = retryTimeouts }

        public fun totalTimeout(totalTimeout: Duration?): Builder = apply { this.totalTimeout = totalTimeout }

        public fun totalTimeoutMillis(totalTimeoutMillis: Long): Builder = totalTimeout(totalTimeoutMillis.milliseconds)

        public fun noTotalTimeout(): Builder = totalTimeout(null)

        public fun build(): RetryStrategy = RetryStrategy(
            maxRetries = maxRetries,
            baseDelay = baseDelay,
            maxDelay = maxDelay,
            jitter = jitter,
            retryableStatuses = retryableStatuses,
            respectRetryAfter = respectRetryAfter,
            maxRetryAfter = maxRetryAfter,
            retryConnectionErrors = retryConnectionErrors,
            retryTimeouts = retryTimeouts,
            totalTimeout = totalTimeout,
        )
    }

    public companion object {
        @JvmField
        public val DEFAULT_RETRYABLE_STATUSES: Set<Int> = setOf(408, 429) + (500..599)

        @JvmField
        public val DEFAULT: RetryStrategy = Builder().build()

        @JvmField
        public val NONE: RetryStrategy = Builder().maxRetries(0).build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

@JvmSynthetic
public fun RetryStrategy(configure: RetryStrategy.Builder.() -> Unit): RetryStrategy = RetryStrategy.builder().apply(configure).build()
