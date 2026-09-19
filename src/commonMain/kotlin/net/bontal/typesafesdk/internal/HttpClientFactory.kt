package net.bontal.typesafesdk.internal

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import net.bontal.typesafesdk.TypeSafeConfig

internal const val REQUEST_ID_HEADER: String = "x-typesafe-request-id"

internal fun createHttpClient(config: TypeSafeConfig): HttpClient {
    val configuration: HttpClientConfig<*>.() -> Unit = {
        expectSuccess = false
        install(HttpTimeout)
        config.httpClientConfig?.configure(this)
    }
    return when (val engine = config.engine) {
        null -> HttpClient(configuration)
        else -> HttpClient(engine, configuration)
    }
}
