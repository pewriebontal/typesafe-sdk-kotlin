package net.bontal.typesafesdk

import io.ktor.client.engine.java.Java
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assume.assumeTrue
import kotlin.math.abs
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

class LiveApiTest {

    private val engine = Java.create()

    @AfterTest
    fun closeEngine() {
        engine.close()
    }

    private val live = !System.getenv("TYPESAFE_LIVE_TESTS").isNullOrBlank()

    private val apiKey: String? = System.getenv("TYPESAFE_API_KEY")?.trim()?.ifEmpty { null }

    private val openRouterKey: String? = System.getenv("OPENROUTER_API_KEY")?.trim()?.ifEmpty { null }

    private fun client(key: String): TypeSafeClient = TypeSafeClient {
        apiKey(key)
        engine(engine)
        timeout(30.seconds)
    }

    private fun openRouterClient(key: String): TypeSafeClient = TypeSafeClient {
        apiKey(key)
        baseUrl(OPENROUTER_BASE_URL)
        defaultModel(OPENROUTER_MODEL)
        engine(engine)
        timeout(30.seconds)
    }

    private fun authenticated(): TypeSafeClient {
        assumeTrue("set TYPESAFE_LIVE_TESTS=1 to run live API tests", live)
        assumeTrue("set TYPESAFE_API_KEY to run authenticated live API tests", apiKey != null)
        return client(apiKey!!)
    }

    private fun ticket() = SystemOneRequest {
        state("I was charged twice for my subscription this month. Please refund one of the charges today.")
        putQuestion(
            "department",
            ChoiceQuestion {
                instructions("Which team should handle this message")
                putCriterion("billing", "Payment, refund, or subscription issues")
                putCriterion("technical", "Bugs or integration problems")
                putCriterion("sales", null)
            },
        )
        putQuestion(
            "urgency",
            ScoreQuestion {
                instructions("How urgent is this message")
                addCriterion("Can wait")
                addCriterion("Needs attention this week")
                addCriterion("Needs attention today")
            },
        )
        putQuestion(
            "refund",
            NoulQuestion {
                instructions("The customer asks for a refund")
                criteria(
                    NoulCriteria {
                        yes("Explicitly requests money back")
                        no("Does not ask for money back")
                    },
                )
            },
        )
    }

    @Test
    fun invalidKeyRaisesAuthenticationExceptionWithServerMetadata() {
        assumeTrue("set TYPESAFE_LIVE_TESTS=1 to run live API tests", live)
        val error = assertFailsWith<AuthenticationException> {
            client("invalid-key-for-sdk-tests").use { it.systemOne(ticket()) }
        }
        assertEquals(401, error.statusCode)
        assertEquals("POST https://api.typesafe.ai/v1/systemone", error.endpoint)
        assertTrue(error.requestId!!.startsWith("req_"), error.requestId)
        assertTrue(error.body!!.contains("authentication_error"), error.body)
        assertTrue(
            error.message!!.startsWith("401 Unauthorized from POST https://api.typesafe.ai/v1/systemone: "),
            error.message,
        )
        assertTrue(!error.message!!.contains("{"), error.message)
    }

    @Test
    fun invalidKeyOnModelsIsATypedApiException() {
        assumeTrue("set TYPESAFE_LIVE_TESTS=1 to run live API tests", live)
        val error = assertFailsWith<ApiException> { client("invalid-key-for-sdk-tests").use { it.models() } }
        assertTrue(error.statusCode == 401 || error.statusCode == 403, "status ${error.statusCode}")
        assertTrue(error.requestId!!.startsWith("req_"), error.requestId)
        assertTrue(!error.message!!.contains("{"), error.message)
    }

    @Test
    fun modelsListsJev() {
        val models = authenticated().use { it.models() }
        assertTrue(models.isNotEmpty())
        assertTrue(models.any { it.name.startsWith("jev") }, models.toString())
        models.forEach { assertTrue(it.releaseDate.isNotBlank()) }
    }

    @Test
    fun systemOneAnswersEveryPrimitive() {
        val raw = authenticated().use { it.withRawResponse().systemOne(ticket()) }
        val result = raw.value
        assertEquals(200, raw.statusCode)
        assertTrue(raw.requestId!!.startsWith("req_"))
        assertEquals(raw.requestId, result.requestId)
        assertTrue(result.model.isNotBlank())
        assertTrue(result.usage.inputTokens > 0)

        val department = result.choices.getValue("department")
        assertTrue(department.choice in setOf("billing", "technical", "sales"), department.choice)
        assertEquals(setOf("billing", "technical", "sales"), department.probabilities.keys)
        assertTrue(abs(department.probabilities.values.sum() - 1.0) < 0.01, department.toString())
        assertTrue(department.confidence in 0.0..1.0)

        val urgency = result.scores.getValue("urgency")
        assertTrue(urgency.score in 0.0..2.0, urgency.toString())
        assertEquals(3, urgency.legend.size)

        val refund = result.nouls.getValue("refund")
        assertTrue(refund.noul in 0.0..1.0)
    }

    @Test
    fun asyncFutureStructuredStateAndRawQuestionWork() {
        val client = authenticated()
        client.use {
            val request = SystemOneRequest {
                state(
                    Entry(
                        buildJsonObject {
                            put("subject", "Duplicate charge")
                            put(
                                "messages",
                                buildJsonArray {
                                    add(kotlinx.serialization.json.JsonPrimitive("Please refund me."))
                                },
                            )
                        },
                    ),
                )
                putQuestion(
                    "billing",
                    RawQuestion(
                        buildJsonObject {
                            put("type", "noul")
                            put("instructions", "Is this about billing?")
                        },
                    ),
                )
            }
            val asyncResult = runBlocking { client.async().systemOne(request, RequestOptions { timeout(30.seconds) }) }
            assertTrue(asyncResult.nouls.getValue("billing").noul in 0.0..1.0)
            TypeSafeFutureClient.of(client).use { futures ->
                assertTrue(futures.systemOne(request).get().nouls.containsKey("billing"))
            }
        }
    }

    @Test
    fun serverValidationErrorsAreUnprocessableEntityExceptions() {
        val client = authenticated()
        val error = assertFailsWith<ApiException> {
            client.use {
                it.systemOne(
                    SystemOneRequest {
                        state("hello")
                        putQuestion("broken", RawQuestion(buildJsonObject { put("type", "score") }))
                    },
                )
            }
        }
        assertIs<UnprocessableEntityException>(error, error.message)
        assertTrue(!error.message!!.contains("{"), error.message)
    }

    @Test
    fun openRouterRejectsInvalidKeyWithTypedException() {
        assumeTrue("set TYPESAFE_LIVE_TESTS=1 to run live API tests", live)
        val error = assertFailsWith<AuthenticationException> {
            openRouterClient("sk-or-invalid-key-for-sdk-tests").use { it.systemOne(ticket()) }
        }
        assertEquals("POST https://openrouter.ai/api/v1/systemone", error.endpoint)
        assertTrue(
            error.message!!.startsWith("401 Unauthorized from POST https://openrouter.ai/api/v1/systemone: "),
            error.message,
        )
        assertTrue(!error.message!!.contains("{"), error.message)
    }

    @Test
    fun openRouterValidationErrorsNameTheField() {
        assumeTrue("set TYPESAFE_LIVE_TESTS=1 to run live API tests", live)
        assumeTrue("set OPENROUTER_API_KEY to run OpenRouter live tests", openRouterKey != null)
        val error = assertFailsWith<BadRequestException> {
            openRouterClient(openRouterKey!!).use {
                it.systemOne(
                    SystemOneRequest {
                        state("hello")
                        putQuestion("q", NoulQuestion {})
                    },
                )
            }
        }
        assertTrue(error.message!!.endsWith(": questions.q.instructions: Invalid input"), error.message)
    }

    @Test
    fun openRouterAnswersEveryPrimitiveWithJev113() {
        assumeTrue("set TYPESAFE_LIVE_TESTS=1 to run live API tests", live)
        assumeTrue("set OPENROUTER_API_KEY to run OpenRouter live tests", openRouterKey != null)
        val result = openRouterClient(openRouterKey!!).use { it.systemOne(ticket()) }
        assertTrue(result.model.contains("jev"), result.model)
        assertTrue(result.choices.getValue("department").choice in setOf("billing", "technical", "sales"))
        assertEquals(3, result.scores.getValue("urgency").legend.size)
        assertTrue(result.nouls.getValue("refund").noul in 0.0..1.0)
        assertTrue(result.usage.inputTokens > 0)
    }

    private companion object {
        const val OPENROUTER_BASE_URL = "https://openrouter.ai/api"
        const val OPENROUTER_MODEL = "typesafe/jev-1.13"
    }
}
