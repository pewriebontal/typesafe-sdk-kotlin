package net.bontal.typesafesdk

import io.ktor.client.HttpClientConfig
import io.ktor.client.engine.HttpClientEngine
import io.ktor.http.Url
import net.bontal.typesafesdk.internal.defaultLogger
import net.bontal.typesafesdk.internal.readSetting
import net.bontal.typesafesdk.internal.requireValidHeaders
import net.bontal.typesafesdk.internal.requireValidTimeout
import kotlin.jvm.JvmStatic
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

public fun interface ConfigModifier {
    public fun modify(builder: TypeSafeConfig.Builder)
}

public fun interface HttpClientConfigurer {
    public fun configure(config: HttpClientConfig<*>)
}

public class TypeSafeConfig private constructor(
    public val apiKey: String,
    public val baseUrl: String,
    public val defaultModel: String,
    public val timeout: Duration,
    public val retry: RetryStrategy,
    headers: Map<String, String>,
    public val engine: HttpClientEngine?,
    public val httpClientConfig: HttpClientConfigurer?,
    public val logLevel: LogLevel,
    public val logger: TypeSafeLogger,
) {
    init {
        require(defaultModel.isNotBlank()) { "defaultModel must not be blank" }
        requireValidTimeout(timeout)
        requireValidHeaders(headers)
    }

    public val headers: Map<String, String> = headers.toMap()

    public val timeoutMillis: Long get() = timeout.inWholeMilliseconds

    override fun toString(): String = "TypeSafeConfig(apiKey=***, baseUrl=$baseUrl, defaultModel=$defaultModel, timeout=$timeout, retry=$retry, " +
        "headers=${headers.keys}, logLevel=$logLevel)"

    public fun toBuilder(): Builder = Builder()
        .apiKey(apiKey)
        .baseUrl(baseUrl)
        .defaultModel(defaultModel)
        .timeout(timeout)
        .retry(retry)
        .headers(headers)
        .engine(engine)
        .httpClientConfig(httpClientConfig)
        .logLevel(logLevel)
        .logger(logger)

    public class Builder internal constructor() {
        private var apiKey: String? = null
        private var baseUrl: String? = null
        private var defaultModel: String? = null
        private var timeout: Duration = DEFAULT_TIMEOUT_MILLIS.milliseconds
        private var retry: RetryStrategy = RetryStrategy.DEFAULT
        private val headers = LinkedHashMap<String, String>()
        private var engine: HttpClientEngine? = null
        private var httpClientConfig: HttpClientConfigurer? = null
        private var logLevel: LogLevel? = null
        private var logger: TypeSafeLogger? = null

        public fun apiKey(apiKey: String): Builder = apply { this.apiKey = apiKey }

        public fun baseUrl(baseUrl: String): Builder = apply { this.baseUrl = baseUrl }

        public fun defaultModel(defaultModel: String): Builder = apply { this.defaultModel = defaultModel }

        public fun timeout(timeout: Duration): Builder = apply { this.timeout = timeout }

        public fun timeoutMillis(timeoutMillis: Long): Builder = timeout(timeoutMillis.milliseconds)

        public fun retry(retry: RetryStrategy): Builder = apply { this.retry = retry }

        public fun headers(headers: Map<String, String>): Builder = apply {
            this.headers.clear()
            this.headers.putAll(headers)
        }

        public fun putHeader(name: String, value: String): Builder = apply { headers[name] = value }

        public fun engine(engine: HttpClientEngine?): Builder = apply { this.engine = engine }

        public fun httpClientConfig(httpClientConfig: HttpClientConfigurer?): Builder = apply { this.httpClientConfig = httpClientConfig }

        public fun logLevel(logLevel: LogLevel): Builder = apply { this.logLevel = logLevel }

        public fun logger(logger: TypeSafeLogger): Builder = apply { this.logger = logger }

        public fun build(): TypeSafeConfig = TypeSafeConfig(
            apiKey = normalizeApiKey(
                apiKey ?: readSetting(API_KEY_PROPERTY, API_KEY_ENV) ?: throw IllegalStateException(
                    "apiKey is required: set it on the builder, the $API_KEY_PROPERTY system property, or the " +
                        "$API_KEY_ENV environment variable. Android apps must set it on the builder.",
                ),
            ),
            baseUrl = normalizeBaseUrl(baseUrl ?: readSetting(BASE_URL_PROPERTY, BASE_URL_ENV) ?: DEFAULT_BASE_URL),
            defaultModel = defaultModel ?: readSetting(DEFAULT_MODEL_PROPERTY, DEFAULT_MODEL_ENV) ?: DEFAULT_MODEL,
            timeout = timeout,
            retry = retry,
            headers = headers,
            engine = engine,
            httpClientConfig = httpClientConfig,
            logLevel = logLevel ?: readSetting(LOG_LEVEL_PROPERTY, LOG_LEVEL_ENV)?.let(::parseLogLevel) ?: LogLevel.WARN,
            logger = logger ?: defaultLogger(),
        )
    }

    public companion object {
        public const val DEFAULT_BASE_URL: String = "https://api.typesafe.ai"
        public const val DEFAULT_MODEL: String = "jev-latest"
        public const val DEFAULT_TIMEOUT_MILLIS: Long = 10_000L
        public const val API_KEY_ENV: String = "TYPESAFE_API_KEY"
        public const val BASE_URL_ENV: String = "TYPESAFE_BASE_URL"
        public const val DEFAULT_MODEL_ENV: String = "TYPESAFE_DEFAULT_MODEL"
        public const val LOG_LEVEL_ENV: String = "TYPESAFE_LOG_LEVEL"
        public const val API_KEY_PROPERTY: String = "typesafe.apiKey"
        public const val BASE_URL_PROPERTY: String = "typesafe.baseUrl"
        public const val DEFAULT_MODEL_PROPERTY: String = "typesafe.defaultModel"
        public const val LOG_LEVEL_PROPERTY: String = "typesafe.logLevel"

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

private fun normalizeApiKey(apiKey: String): String {
    val key = apiKey.trim()
    require(key.isNotEmpty()) { "apiKey must not be empty" }
    require(key.all { it in '!'..'~' }) { "apiKey must not contain whitespace, control, or non-ASCII characters" }
    return key
}

private fun normalizeBaseUrl(baseUrl: String): String {
    val url = baseUrl.trim().trimEnd('/')
    val valid = (url.startsWith("https://") || url.startsWith("http://")) &&
        runCatching { Url(url).host.isNotBlank() }.getOrDefault(false)
    require(valid) { "baseUrl must be an absolute http or https URL" }
    return url
}

private fun parseLogLevel(value: String): LogLevel? = when (value.lowercase()) {
    "debug" -> LogLevel.DEBUG
    "info" -> LogLevel.INFO
    "warn", "warning" -> LogLevel.WARN
    "error" -> LogLevel.ERROR
    "off" -> LogLevel.OFF
    else -> null
}
