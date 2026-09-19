package net.bontal.typesafesdk

import net.bontal.typesafesdk.internal.requireValidHeaders
import net.bontal.typesafesdk.internal.requireValidTimeout
import kotlin.jvm.JvmField
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public class RequestOptions private constructor(
    public val timeout: Duration?,
    public val retry: RetryStrategy?,
    headers: Map<String, String>,
) {
    init {
        timeout?.let(::requireValidTimeout)
        requireValidHeaders(headers)
    }

    public val headers: Map<String, String> = headers.toMap()

    public val timeoutMillis: Long? get() = timeout?.inWholeMilliseconds

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RequestOptions) return false
        return timeout == other.timeout && retry == other.retry && headers == other.headers
    }

    override fun hashCode(): Int {
        var result = timeout?.hashCode() ?: 0
        result = 31 * result + (retry?.hashCode() ?: 0)
        result = 31 * result + headers.hashCode()
        return result
    }

    override fun toString(): String = "RequestOptions(timeout=$timeout, retry=$retry, headers=${headers.keys})"

    public fun toBuilder(): Builder = Builder()
        .timeout(timeout)
        .retry(retry)
        .headers(headers)

    public class Builder internal constructor() {
        private var timeout: Duration? = null
        private var retry: RetryStrategy? = null
        private val headers = LinkedHashMap<String, String>()

        public fun timeout(timeout: Duration?): Builder = apply { this.timeout = timeout }

        public fun timeoutMillis(timeoutMillis: Long): Builder = timeout(timeoutMillis.milliseconds)

        public fun retry(retry: RetryStrategy?): Builder = apply { this.retry = retry }

        public fun headers(headers: Map<String, String>): Builder = apply {
            this.headers.clear()
            this.headers.putAll(headers)
        }

        public fun putHeader(name: String, value: String): Builder = apply { headers[name] = value }

        public fun build(): RequestOptions = RequestOptions(timeout, retry, headers)
    }

    public companion object {
        @JvmField
        public val NONE: RequestOptions = Builder().build()

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

@JvmSynthetic
public fun RequestOptions(configure: RequestOptions.Builder.() -> Unit): RequestOptions = RequestOptions.builder().apply(configure).build()
