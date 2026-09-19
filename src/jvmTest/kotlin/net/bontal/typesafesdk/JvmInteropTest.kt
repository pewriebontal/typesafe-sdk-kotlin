package net.bontal.typesafesdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.util.concurrent.ExecutionException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class JvmInteropTest {

    private val engine = MockEngine { request ->
        val body = if (request.url.encodedPath.endsWith("/models")) MODELS_RESPONSE else SYSTEM_ONE_RESPONSE
        respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))
    }

    @Test
    fun javaReadmeUsageCompilesAndRuns() {
        JavaUsage.buildClient(engine).use { client ->
            assertEquals("technical|jev-1.13.0|200|jev-1.13.0", JavaUsage.run(client))
        }
    }

    @Test
    fun javaTryWithResourcesNeedsNoCheckedException() {
        assertEquals(1, JavaUsage.modelCount(engine))
    }

    @Test
    fun closedFutureClientRejectsNewCalls() {
        val futures = TypeSafeFutureClient.of(JavaUsage.buildClient(engine))
        futures.close()
        assertFailsWith<IllegalStateException> { futures.models() }
    }

    @Test
    fun javaSeesExceptionMetadata() {
        val failing = MockEngine {
            respond("", HttpStatusCode.TooManyRequests, headersOf("retry-after-ms", "40"))
        }
        val error = assertFailsWith<RateLimitException> { JavaUsage.buildClient(failing).use { it.models() } }
        assertEquals("rate-limited:40", JavaUsage.describe(error))
    }

    @Test
    fun futureClientPropagatesFailuresAndCancellation() {
        val failing = MockEngine { respond("", HttpStatusCode.Unauthorized) }
        JavaUsage.buildClient(failing).use { client ->
            TypeSafeFutureClient.of(client).use { futures ->
                val error = assertFailsWith<ExecutionException> { futures.models().get() }
                assertIs<AuthenticationException>(error.cause)
            }
        }
    }

    @Test
    fun systemPropertiesTakePrecedenceOverEnvironmentAndExplicitValuesWin() {
        System.setProperty(TypeSafeConfig.API_KEY_PROPERTY, "  property-key ")
        System.setProperty(TypeSafeConfig.DEFAULT_MODEL_PROPERTY, "jev-from-property")
        System.setProperty(TypeSafeConfig.LOG_LEVEL_PROPERTY, "debug")
        try {
            val fromProperties = TypeSafeConfig.builder().build()
            assertEquals("property-key", fromProperties.apiKey)
            assertEquals("jev-from-property", fromProperties.defaultModel)
            assertEquals(LogLevel.DEBUG, fromProperties.logLevel)
            TypeSafeClient.fromEnv().close()

            val explicit = TypeSafeConfig.builder().apiKey("explicit").defaultModel("explicit-model").build()
            assertEquals("explicit", explicit.apiKey)
            assertEquals("explicit-model", explicit.defaultModel)

            assertFailsWith<IllegalArgumentException> { TypeSafeConfig.builder().apiKey("").build() }
        } finally {
            System.clearProperty(TypeSafeConfig.API_KEY_PROPERTY)
            System.clearProperty(TypeSafeConfig.DEFAULT_MODEL_PROPERTY)
            System.clearProperty(TypeSafeConfig.LOG_LEVEL_PROPERTY)
        }
    }

    private companion object {
        const val SYSTEM_ONE_RESPONSE = """
            {"model":"jev-1.13.0",
             "answers":{"department":{"type":"choice","choice":"technical","confidence":0.78,"probabilities":{"technical":0.85,"billing":0.15}},
                        "spam":{"type":"noul","noul":0.02}},
             "usage":{"input_tokens":392,"output_tokens":65}}
        """

        const val MODELS_RESPONSE = """{"models":[{"name":"jev-latest","description":"General-purpose.","release_date":"2026-09-15"}]}"""
    }
}
