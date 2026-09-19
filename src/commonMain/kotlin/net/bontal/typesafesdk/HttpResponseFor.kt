package net.bontal.typesafesdk

import net.bontal.typesafesdk.internal.REQUEST_ID_HEADER

public class HttpResponseFor<T> internal constructor(
    public val value: T,
    public val statusCode: Int,
    public val headers: Headers,
    public val body: String,
) {
    public val requestId: String? get() = headers[REQUEST_ID_HEADER]

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HttpResponseFor<*>) return false
        return value == other.value && statusCode == other.statusCode && headers == other.headers && body == other.body
    }

    override fun hashCode(): Int {
        var result = value.hashCode()
        result = 31 * result + statusCode
        result = 31 * result + headers.hashCode()
        result = 31 * result + body.hashCode()
        return result
    }

    override fun toString(): String = "HttpResponseFor(value=$value, statusCode=$statusCode, requestId=$requestId)"
}
