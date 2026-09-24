package net.bontal.typesafesdk

import net.bontal.typesafesdk.internal.REQUEST_ID_HEADER
import net.bontal.typesafesdk.internal.parseRetryAfterMillis
import kotlin.time.Duration

public sealed class TypeSafeException(message: String, cause: Throwable?) : RuntimeException(message, cause)

public sealed class ApiException(
    public val statusCode: Int,
    message: String,
    public val body: String?,
    public val headers: Headers,
    public val endpoint: String?,
) : TypeSafeException(message, null) {
    public val requestId: String? get() = headers[REQUEST_ID_HEADER]
}

public class BadRequestException internal constructor(
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(400, message, body, headers, endpoint)

public class AuthenticationException internal constructor(
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(401, message, body, headers, endpoint)

public class PermissionDeniedException internal constructor(
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(403, message, body, headers, endpoint)

public class NotFoundException internal constructor(
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(404, message, body, headers, endpoint)

public class UnprocessableEntityException internal constructor(
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(422, message, body, headers, endpoint)

public class RateLimitException internal constructor(
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(429, message, body, headers, endpoint) {
    public val retryAfterMillis: Long? = parseRetryAfterMillis(headers)
}

public class InternalServerException internal constructor(
    statusCode: Int,
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(statusCode, message, body, headers, endpoint)

public class UnexpectedStatusCodeException internal constructor(
    statusCode: Int,
    message: String,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(statusCode, message, body, headers, endpoint)

public class ApiResponseValidationException internal constructor(
    statusCode: Int,
    message: String,
    public val fieldPath: String?,
    body: String?,
    headers: Headers,
    endpoint: String?,
) : ApiException(statusCode, message, body, headers, endpoint)

public open class ApiConnectionException internal constructor(message: String, cause: Throwable?) :
    TypeSafeException(message, cause)

public class ApiTimeoutException internal constructor(public val timeout: Duration, cause: Throwable?) :
    ApiConnectionException("Request timed out after $timeout", cause) {
    public val timeoutMillis: Long get() = timeout.inWholeMilliseconds
}
