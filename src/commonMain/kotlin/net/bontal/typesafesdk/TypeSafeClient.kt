package net.bontal.typesafesdk

import io.ktor.client.engine.HttpClientEngine
import net.bontal.typesafesdk.internal.TypeSafeApi
import net.bontal.typesafesdk.internal.TypeSafeSyncApi
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic
import kotlin.time.Duration

public interface TypeSafeClient : AutoCloseable {
    public fun systemOne(request: SystemOneRequest): SystemOneResult = systemOne(request, RequestOptions.NONE)

    public fun systemOne(request: SystemOneRequest, options: RequestOptions): SystemOneResult

    public fun models(): List<ModelCard> = models(RequestOptions.NONE)

    public fun models(options: RequestOptions): List<ModelCard>

    public fun async(): TypeSafeClientAsync

    public fun withRawResponse(): TypeSafeClientRaw

    public fun withOptions(modifier: ConfigModifier): TypeSafeClient

    override fun close()

    public class Builder internal constructor() {
        private val config = TypeSafeConfig.builder()

        public fun apiKey(apiKey: String): Builder = apply { config.apiKey(apiKey) }

        public fun baseUrl(baseUrl: String): Builder = apply { config.baseUrl(baseUrl) }

        public fun defaultModel(defaultModel: String): Builder = apply { config.defaultModel(defaultModel) }

        public fun timeout(timeout: Duration): Builder = apply { config.timeout(timeout) }

        public fun timeoutMillis(timeoutMillis: Long): Builder = apply { config.timeoutMillis(timeoutMillis) }

        public fun retry(retry: RetryStrategy): Builder = apply { config.retry(retry) }

        public fun headers(headers: Map<String, String>): Builder = apply { config.headers(headers) }

        public fun putHeader(name: String, value: String): Builder = apply { config.putHeader(name, value) }

        public fun engine(engine: HttpClientEngine?): Builder = apply { config.engine(engine) }

        public fun httpClientConfig(httpClientConfig: HttpClientConfigurer?): Builder = apply {
            config.httpClientConfig(httpClientConfig)
        }

        public fun logLevel(logLevel: LogLevel): Builder = apply { config.logLevel(logLevel) }

        public fun logger(logger: TypeSafeLogger): Builder = apply { config.logger(logger) }

        public fun build(): TypeSafeClient = TypeSafeClient(config.build())
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()

        @JvmStatic
        public fun fromEnv(): TypeSafeClient = builder().build()
    }
}

public interface TypeSafeClientAsync : AutoCloseable {
    public suspend fun systemOne(request: SystemOneRequest): SystemOneResult = systemOne(request, RequestOptions.NONE)

    public suspend fun systemOne(request: SystemOneRequest, options: RequestOptions): SystemOneResult

    public suspend fun models(): List<ModelCard> = models(RequestOptions.NONE)

    public suspend fun models(options: RequestOptions): List<ModelCard>

    public fun sync(): TypeSafeClient

    public fun withRawResponse(): TypeSafeClientRawAsync

    public fun withOptions(modifier: ConfigModifier): TypeSafeClientAsync

    override fun close()
}

public interface TypeSafeClientRaw {
    public fun systemOne(request: SystemOneRequest): HttpResponseFor<SystemOneResult> =
        systemOne(request, RequestOptions.NONE)

    public fun systemOne(request: SystemOneRequest, options: RequestOptions): HttpResponseFor<SystemOneResult>

    public fun models(): HttpResponseFor<List<ModelCard>> = models(RequestOptions.NONE)

    public fun models(options: RequestOptions): HttpResponseFor<List<ModelCard>>
}

public interface TypeSafeClientRawAsync {
    public suspend fun systemOne(request: SystemOneRequest): HttpResponseFor<SystemOneResult> =
        systemOne(request, RequestOptions.NONE)

    public suspend fun systemOne(request: SystemOneRequest, options: RequestOptions): HttpResponseFor<SystemOneResult>

    public suspend fun models(): HttpResponseFor<List<ModelCard>> = models(RequestOptions.NONE)

    public suspend fun models(options: RequestOptions): HttpResponseFor<List<ModelCard>>
}

@JvmSynthetic
public fun TypeSafeClient(config: TypeSafeConfig): TypeSafeClient = TypeSafeSyncApi(TypeSafeApi(config))

@JvmSynthetic
public fun TypeSafeClient(configure: TypeSafeConfig.Builder.() -> Unit): TypeSafeClient =
    TypeSafeClient(TypeSafeConfig.builder().apply(configure).build())

@JvmSynthetic
public fun TypeSafeClientAsync(config: TypeSafeConfig): TypeSafeClientAsync = TypeSafeApi(config)

@JvmSynthetic
public fun TypeSafeClientAsync(configure: TypeSafeConfig.Builder.() -> Unit): TypeSafeClientAsync =
    TypeSafeClientAsync(TypeSafeConfig.builder().apply(configure).build())
