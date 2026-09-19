package net.bontal.typesafesdk.examples

import net.bontal.typesafesdk.TypeSafeClient
import net.bontal.typesafesdk.TypeSafeClientAsync
import net.bontal.typesafesdk.TypeSafeConfig

private const val OPENROUTER_BASE_URL = "https://openrouter.ai/api"
private const val OPENROUTER_MODEL = "typesafe/jev-1.13"

fun exampleConfig(): TypeSafeConfig {
    val openRouterKey = System.getenv("OPENROUTER_API_KEY")?.trim()?.ifEmpty { null }
    return TypeSafeConfig.builder().apply {
        if (openRouterKey != null) {
            apiKey(openRouterKey)
            baseUrl(OPENROUTER_BASE_URL)
            defaultModel(OPENROUTER_MODEL)
        }
    }.build()
}

fun syncClient(): TypeSafeClient = TypeSafeClient(exampleConfig())

fun asyncClient(): TypeSafeClientAsync = TypeSafeClientAsync(exampleConfig())

fun percent(value: Double): String = "${(value * 100).toInt()}%"
