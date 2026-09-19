package net.bontal.typesafesdk.internal

import io.ktor.client.HttpClient
import io.ktor.client.network.sockets.ConnectTimeoutException
import io.ktor.client.network.sockets.SocketTimeoutException
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.timeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.URLBuilder
import io.ktor.http.contentType
import io.ktor.http.takeFrom
import io.ktor.util.toMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.JsonElement
import net.bontal.typesafesdk.ApiConnectionException
import net.bontal.typesafesdk.ApiException
import net.bontal.typesafesdk.ApiResponseValidationException
import net.bontal.typesafesdk.ApiTimeoutException
import net.bontal.typesafesdk.AuthenticationException
import net.bontal.typesafesdk.BadRequestException
import net.bontal.typesafesdk.ConfigModifier
import net.bontal.typesafesdk.Headers
import net.bontal.typesafesdk.HttpResponseFor
import net.bontal.typesafesdk.InternalServerException
import net.bontal.typesafesdk.ModelCard
import net.bontal.typesafesdk.NotFoundException
import net.bontal.typesafesdk.PermissionDeniedException
import net.bontal.typesafesdk.RateLimitException
import net.bontal.typesafesdk.RequestOptions
import net.bontal.typesafesdk.RetryStrategy
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.SystemOneResult
import net.bontal.typesafesdk.TypeSafeClient
import net.bontal.typesafesdk.TypeSafeClientAsync
import net.bontal.typesafesdk.TypeSafeClientRaw
import net.bontal.typesafesdk.TypeSafeClientRawAsync
import net.bontal.typesafesdk.TypeSafeConfig
import net.bontal.typesafesdk.TypeSafeException
import net.bontal.typesafesdk.UnexpectedStatusCodeException
import net.bontal.typesafesdk.UnprocessableEntityException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

internal class TypeSafeApi private constructor(
    internal val config: TypeSafeConfig,
    private val client: HttpClient,
    private val ownsClient: Boolean,
) : TypeSafeClientAsync {

    constructor(config: TypeSafeConfig) : this(config, createHttpClient(config), ownsClient = true)

    private val log = Log(config)

    override suspend fun systemOne(request: SystemOneRequest, options: RequestOptions): SystemOneResult = rawSystemOne(request, options).value

    override suspend fun models(options: RequestOptions): List<ModelCard> = rawModels(options).value

    override fun sync(): TypeSafeClient = TypeSafeSyncApi(this)

    override fun withRawResponse(): TypeSafeClientRawAsync = TypeSafeAsyncRawApi(this)

    override fun withOptions(modifier: ConfigModifier): TypeSafeApi {
        val builder = config.toBuilder()
        modifier.modify(builder)
        val updated = builder.build()
        val sharesTransport = updated.engine === config.engine && updated.httpClientConfig === config.httpClientConfig
        return if (sharesTransport) TypeSafeApi(updated, client, ownsClient = false) else TypeSafeApi(updated)
    }

    override fun close() {
        if (ownsClient) client.close()
    }

    internal suspend fun rawSystemOne(request: SystemOneRequest, options: RequestOptions): HttpResponseFor<SystemOneResult> = send(HttpMethod.Post, "/v1/systemone", request.toJson(config.defaultModel).toString(), options) { json, headers ->
        json.toSystemOneResult(headers[REQUEST_ID_HEADER]) { name, type ->
            log.warn { "Skipped answer `$name` with unknown type `$type`; upgrade typesafe-sdk-kotlin or read the raw response body" }
        }
    }

    internal suspend fun rawModels(options: RequestOptions): HttpResponseFor<List<ModelCard>> = send(HttpMethod.Get, "/v1/models", null, options) { json, _ -> json.toModelCards() }

    private suspend fun <T> send(
        method: HttpMethod,
        path: String,
        payload: String?,
        options: RequestOptions,
        parse: (JsonElement, Headers) -> T,
    ): HttpResponseFor<T> {
        check(client.isActive) { CLOSED_MESSAGE }
        val call = Call(method, config.baseUrl + path, payload, options, options.retry ?: config.retry, options.timeout ?: config.timeout)
        val started = TimeSource.Monotonic.markNow()
        var retryCount = 0
        while (true) {
            val failure = when (val outcome = attempt(call, retryCount, parse)) {
                is Outcome.Success -> return outcome.response
                is Outcome.Failure -> outcome
            }
            val retry = call.retry
            if (!failure.retryable || retryCount >= retry.maxRetries) throw failure.exception
            val delayMillis = retryDelayMillis(retryCount + 1, failure.headers, retry)
            val budget = retry.totalTimeout
            if (budget != null && started.elapsedNow() + delayMillis.milliseconds >= budget) throw failure.exception
            retryCount++
            log.info { "Retrying ${call.endpoint} in $delayMillis ms (retry $retryCount of ${retry.maxRetries}): ${failure.exception.message}" }
            delay(delayMillis)
        }
    }

    private suspend fun <T> attempt(call: Call, retryCount: Int, parse: (JsonElement, Headers) -> T): Outcome<T> {
        val started = TimeSource.Monotonic.markNow()
        val (status, responseHeaders, body) = try {
            val response = client.request(call.url) {
                method = call.method
                timeout {
                    requestTimeoutMillis = call.timeout.inWholeMilliseconds
                    connectTimeoutMillis = call.timeout.inWholeMilliseconds
                    socketTimeoutMillis = call.timeout.inWholeMilliseconds
                }
                applyHeaders(call.options, retryCount)
                if (call.payload != null) {
                    contentType(ContentType.Application.Json)
                    setBody(call.payload)
                }
                log.debug {
                    "Request ${call.endpoint} headers=${redactHeaders(headers.entries().flatMap { (n, v) -> v.map { n to it } })} " +
                        "body=${call.payload.orEmpty()}"
                }
            }
            Triple(response.status.value, Headers.of(response.headers.toMap()), response.bodyAsText())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (!client.isActive) throw IllegalStateException(CLOSED_MESSAGE, e)
            val timedOut = e.isTimeout()
            val failure = if (timedOut) ApiTimeoutException(call.timeout, e) else ApiConnectionException("Connection error: ${e.message}", e)
            log.info { "${call.endpoint} failed after ${started.elapsedNow().inWholeMilliseconds} ms: ${failure.message}" }
            return Outcome.Failure(failure, if (timedOut) call.retry.retryTimeouts else call.retry.retryConnectionErrors, null)
        }
        log.info {
            "${call.endpoint} -> $status in ${started.elapsedNow().inWholeMilliseconds} ms" +
                (responseHeaders[REQUEST_ID_HEADER]?.let { " (request id $it)" } ?: "")
        }
        log.debug { "Response ${call.endpoint} headers=${redactHeaders(responseHeaders.toMap().flatMap { (n, v) -> v.map { n to it } })} body=$body" }
        if (status in 200..299) {
            return Outcome.Success(HttpResponseFor(parseBody(body, status, responseHeaders, call.endpoint, parse), status, responseHeaders, body))
        }
        val failure = apiException(status, body.ifEmpty { null }, responseHeaders, call.endpoint)
        return Outcome.Failure(failure, status in call.retry.retryableStatuses, responseHeaders)
    }

    private fun <T> parseBody(body: String, status: Int, headers: Headers, endpoint: String, parse: (JsonElement, Headers) -> T): T {
        val json = try {
            DefaultJson.parseToJsonElement(body)
        } catch (e: SerializationException) {
            throw ApiResponseValidationException(status, "Response body is not valid JSON", null, body, headers, endpoint)
        }
        return try {
            parse(json, headers)
        } catch (e: ResponseFieldException) {
            throw ApiResponseValidationException(
                status,
                "Invalid response field `${e.fieldPath}`: ${e.message}",
                e.fieldPath,
                body,
                headers,
                endpoint,
            )
        }
    }

    private fun HttpRequestBuilder.applyHeaders(options: RequestOptions, retryCount: Int) {
        for ((name, value) in config.headers) headers[name] = value
        for ((name, value) in options.headers) headers[name] = value
        fun default(name: String, value: String) {
            if (!headers.contains(name)) headers[name] = value
        }
        default(HttpHeaders.Authorization, "Bearer ${config.apiKey}")
        default(HttpHeaders.Accept, ContentType.Application.Json.toString())
        default(HttpHeaders.UserAgent, "typesafe-sdk-kotlin/$VERSION")
        default("X-TypeSafe-SDK", "typesafe-sdk-kotlin/$VERSION")
        default("X-TypeSafe-Runtime", platformRuntime())
        if (retryCount > 0) default(RETRY_COUNT_HEADER, retryCount.toString())
    }

    private class Call(
        val method: HttpMethod,
        val url: String,
        val payload: String?,
        val options: RequestOptions,
        val retry: RetryStrategy,
        val timeout: Duration,
    ) {
        val endpoint: String = "${method.value} ${sanitizeUrl(url)}"
    }

    private sealed interface Outcome<out T> {
        class Success<T>(val response: HttpResponseFor<T>) : Outcome<T>

        class Failure(val exception: TypeSafeException, val retryable: Boolean, val headers: Headers?) : Outcome<Nothing>
    }

    private companion object {
        const val CLOSED_MESSAGE = "The TypeSafe client has been closed"
    }
}

internal class TypeSafeSyncApi(private val asyncClient: TypeSafeApi) : TypeSafeClient {

    override fun systemOne(request: SystemOneRequest, options: RequestOptions): SystemOneResult = blocking { asyncClient.systemOne(request, options) }

    override fun models(options: RequestOptions): List<ModelCard> = blocking { asyncClient.models(options) }

    override fun async(): TypeSafeClientAsync = asyncClient

    override fun withRawResponse(): TypeSafeClientRaw = TypeSafeSyncRawApi(asyncClient)

    override fun withOptions(modifier: ConfigModifier): TypeSafeClient = TypeSafeSyncApi(asyncClient.withOptions(modifier))

    override fun close() = asyncClient.close()
}

internal class TypeSafeSyncRawApi(private val asyncClient: TypeSafeApi) : TypeSafeClientRaw {

    override fun systemOne(request: SystemOneRequest, options: RequestOptions): HttpResponseFor<SystemOneResult> = blocking { asyncClient.rawSystemOne(request, options) }

    override fun models(options: RequestOptions): HttpResponseFor<List<ModelCard>> = blocking { asyncClient.rawModels(options) }
}

internal class TypeSafeAsyncRawApi(private val asyncClient: TypeSafeApi) : TypeSafeClientRawAsync {

    override suspend fun systemOne(request: SystemOneRequest, options: RequestOptions): HttpResponseFor<SystemOneResult> = asyncClient.rawSystemOne(request, options)

    override suspend fun models(options: RequestOptions): HttpResponseFor<List<ModelCard>> = asyncClient.rawModels(options)
}

private fun <T> blocking(block: suspend () -> T): T {
    checkNotMainThread()
    return runBlocking { block() }
}

private fun Throwable.isTimeout(): Boolean = this is HttpRequestTimeoutException || this is ConnectTimeoutException || this is SocketTimeoutException

internal fun sanitizeUrl(url: String): String = URLBuilder().takeFrom(url).apply {
    user = null
    password = null
    parameters.clear()
    fragment = ""
}.buildString()

internal fun apiException(status: Int, body: String?, headers: Headers, endpoint: String): ApiException {
    val message = errorMessage(status, body, endpoint, headers[REQUEST_ID_HEADER])
    return when (status) {
        400 -> BadRequestException(message, body, headers, endpoint)
        401 -> AuthenticationException(message, body, headers, endpoint)
        403 -> PermissionDeniedException(message, body, headers, endpoint)
        404 -> NotFoundException(message, body, headers, endpoint)
        422 -> UnprocessableEntityException(message, body, headers, endpoint)
        429 -> RateLimitException(message, body, headers, endpoint)
        in 500..599 -> InternalServerException(status, message, body, headers, endpoint)
        else -> UnexpectedStatusCodeException(status, message, body, headers, endpoint)
    }
}

private fun errorMessage(status: Int, body: String?, endpoint: String, requestId: String?): String {
    val reason = if (status == 529) "Overloaded" else HttpStatusCode.allStatusCodes.firstOrNull { it.value == status }?.description
    val detail = body?.let(::errorDetail)
    return buildString {
        append(status)
        if (reason != null) append(' ').append(reason)
        append(" from ").append(endpoint)
        if (detail != null) append(": ").append(detail)
        if (requestId != null) append(" (request id ").append(requestId).append(')')
    }
}
