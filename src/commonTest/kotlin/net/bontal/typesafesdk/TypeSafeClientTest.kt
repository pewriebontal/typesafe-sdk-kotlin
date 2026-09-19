package net.bontal.typesafesdk

import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.plugins.HttpTimeoutCapability
import io.ktor.client.request.HttpRequestData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.io.IOException
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.bontal.typesafesdk.internal.VERSION
import net.bontal.typesafesdk.internal.parseRetryAfterMillis
import net.bontal.typesafesdk.internal.retryDelayMillis
import net.bontal.typesafesdk.internal.sanitizeUrl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class TypeSafeClientTest {

    private fun client(engine: MockEngine, configure: TypeSafeConfig.Builder.() -> Unit = {}) = TypeSafeClient {
        apiKey("test-key")
        retry(RetryStrategy.NONE)
        engine(engine)
        logLevel(LogLevel.OFF)
        configure()
    }

    private fun retryingClient(engine: MockEngine, maxRetries: Int = 2) = client(engine) {
        retry(
            RetryStrategy {
                maxRetries(maxRetries)
                baseDelay(1.milliseconds)
                maxDelay(1.milliseconds)
            },
        )
    }

    private fun MockRequestHandleScope.ok(body: String) = respond(body, HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json"))

    private fun request() = SystemOneRequest {
        state("I was charged twice. Please help.")
        putQuestion("is_urgent", NoulQuestion { instructions("The message conveys urgency") })
    }

    private fun errorEngine(status: Int, body: String) = MockEngine { respond(body, HttpStatusCode.fromValue(status)) }

    private suspend fun HttpRequestData.bodyText(): String = String(body.toByteArray())

    @Test
    fun systemOneBuildsRequestAndParsesResult() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(SYSTEM_ONE_RESPONSE)
        }
        val result = client(engine).use { client ->
            client.systemOne(
                SystemOneRequest {
                    state("I was charged twice. Please help.")
                    putQuestion(
                        "department",
                        ChoiceQuestion {
                            instructions("Which team should handle this")
                            putCriterion("billing", "Payment issues")
                            putCriterion("technical", null)
                        },
                    )
                    putQuestion(
                        "frustration",
                        ScoreQuestion {
                            instructions("How frustrated the customer appears")
                            addCriterion("Calm")
                            addCriterion("Frustrated")
                            addCriterion("Angry")
                        },
                    )
                    putQuestion(
                        "is_urgent",
                        NoulQuestion {
                            instructions("The message conveys urgency")
                            criteria(NoulCriteria { yes("Time-sensitive") })
                        },
                    )
                },
            )
        }

        assertEquals(HttpMethod.Post, request.method)
        assertEquals("https://api.typesafe.ai/v1/systemone", request.url.toString())
        assertEquals("Bearer test-key", request.headers[HttpHeaders.Authorization])
        assertEquals("application/json", request.body.contentType.toString())
        assertEquals("typesafe-sdk-kotlin/$VERSION", request.headers[HttpHeaders.UserAgent])

        val body = request.bodyText()
        assertTrue(body.contains("\"state\":\"I was charged twice. Please help.\""))
        assertTrue(body.contains("\"model\":\"jev-latest\""))
        assertTrue(body.contains("\"department\":{\"type\":\"choice\""))
        assertTrue(body.contains("\"billing\":\"Payment issues\""))
        assertTrue(body.contains("\"technical\":null"))
        assertTrue(body.contains("\"frustration\":{\"type\":\"score\""))
        assertTrue(body.contains("\"is_urgent\":{\"type\":\"noul\""))
        assertTrue(body.contains("\"true\":\"Time-sensitive\""))

        assertEquals("jev-1.13.0", result.model)
        assertEquals(TokenUsage.builder().inputTokens(392).outputTokens(65).build(), result.usage)

        val department = assertIs<ChoiceAnswer>(result.answers.getValue("department"))
        assertEquals("technical", department.choice)
        assertEquals(0.78, department.confidence)
        assertEquals(mapOf("technical" to 0.85, "sales" to 0.0, "billing" to 0.15), department.probabilities)

        val frustration = assertIs<ScoreAnswer>(result.answers.getValue("frustration"))
        assertEquals(1.0, frustration.score)
        assertEquals(mapOf("0" to 0.0, "1" to 1.0, "2" to 0.0), frustration.probabilities)
        assertEquals(Entry("Calm"), frustration.legend.getValue("0"))

        assertEquals(1.0, assertIs<NoulAnswer>(result.answers.getValue("is_urgent")).noul)
    }

    @Test
    fun systemOneHonorsModelOverrideAndStructuredState() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(SYSTEM_ONE_RESPONSE)
        }
        client(engine).use { client ->
            client.systemOne(
                SystemOneRequest {
                    state(
                        Entry(
                            buildJsonObject {
                                put("subject", "Duplicate charge")
                                put("message", "Please help.")
                            },
                        ),
                    )
                    putQuestion("billing", NoulQuestion { instructions("Is this about billing?") })
                    model("jev-1.12.0")
                },
            )
        }
        val body = request.bodyText()
        assertTrue(body.contains("\"model\":\"jev-1.12.0\""))
        assertTrue(body.contains("\"state\":{\"subject\":\"Duplicate charge\",\"message\":\"Please help.\"}"))
    }

    @Test
    fun typedAnswerViewsPartitionAnswers() = runTest {
        val result = client(MockEngine { ok(SYSTEM_ONE_RESPONSE) }).use { it.systemOne(request()) }
        assertEquals(setOf("department"), result.choices.keys)
        assertEquals(setOf("frustration"), result.scores.keys)
        assertEquals(setOf("is_urgent"), result.nouls.keys)
        assertEquals("technical", result.choices.getValue("department").choice)
        assertNull(result.choices["is_urgent"])
    }

    @Test
    fun modelsParsesModelCards() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(MODELS_RESPONSE)
        }
        val models = client(engine).use { it.models() }
        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://api.typesafe.ai/v1/models", request.url.toString())
        assertEquals(
            listOf(
                ModelCard.builder().name("jev-latest").description("General-purpose system one model.").releaseDate("2026-09-15").build(),
                ModelCard.builder().name("jev-1.13.0").description("Pinned Jev release.").releaseDate("2026-09-15").build(),
            ),
            models,
        )
    }

    @Test
    fun unknownAnswerKindsAreSkippedAndLogged() = runTest {
        val logged = mutableListOf<Pair<LogLevel, String>>()
        val engine = MockEngine {
            ok("""{"model":"m","answers":{"x":{"type":"future","value":1},"y":{"type":"noul","noul":0.5}},"usage":{"input_tokens":1,"output_tokens":1}}""")
        }
        val raw = client(engine) {
            logLevel(LogLevel.WARN)
            logger { level, message -> logged += level to message }
        }.use { it.withRawResponse().systemOne(request()) }
        assertEquals(setOf("y"), raw.value.answers.keys)
        assertTrue(raw.body.contains("\"future\""))
        assertEquals(LogLevel.WARN, logged.single().first)
        assertTrue(logged.single().second.contains("`x`"))
    }

    @Test
    fun mapsErrorStatusesToTypedExceptions() = runTest {
        assertFailsWith<BadRequestException> { client(errorEngine(400, "bad")).use { it.models() } }
        assertFailsWith<AuthenticationException> { client(errorEngine(401, "invalid key")).use { it.models() } }
        assertFailsWith<PermissionDeniedException> { client(errorEngine(403, "denied")).use { it.models() } }
        assertFailsWith<NotFoundException> { client(errorEngine(404, "missing")).use { it.models() } }
        assertFailsWith<RateLimitException> { client(errorEngine(429, "slow down")).use { it.models() } }
        assertFailsWith<InternalServerException> { client(errorEngine(503, "overloaded")).use { it.models() } }
        assertFailsWith<UnexpectedStatusCodeException> { client(errorEngine(418, "teapot")).use { it.models() } }
    }

    @Test
    fun errorMessagesIncludeStatusEndpointDetailAndRequestId() = runTest {
        val engine = MockEngine {
            respond(
                """{"detail":"bad key"}""",
                HttpStatusCode.Unauthorized,
                headersOf("x-typesafe-request-id" to listOf("req_401"), HttpHeaders.ContentType to listOf("application/json")),
            )
        }
        val error = assertFailsWith<AuthenticationException> { client(engine).use { it.models() } }
        assertEquals("401 Unauthorized from GET https://api.typesafe.ai/v1/models: bad key (request id req_401)", error.message)
        assertEquals(401, error.statusCode)
        assertEquals("""{"detail":"bad key"}""", error.body)
        assertEquals("GET https://api.typesafe.ai/v1/models", error.endpoint)
        assertEquals("req_401", error.requestId)
        assertEquals(listOf("req_401"), error.headers.values("X-TypeSafe-Request-Id"))

        val overloaded = assertFailsWith<InternalServerException> { client(errorEngine(529, "")).use { it.models() } }
        assertEquals("529 Overloaded from GET https://api.typesafe.ai/v1/models", overloaded.message)
    }

    @Test
    fun liveErrorBodyShapeProducesReadableMessage() = runTest {
        val body = """{"detail":{"error_type":"authentication_error","message":"Cannot authenticate with the server. Please check your API key and try again."}}"""
        val error = assertFailsWith<AuthenticationException> { client(errorEngine(401, body)).use { it.systemOne(request()) } }
        assertEquals(
            "401 Unauthorized from POST https://api.typesafe.ai/v1/systemone: " +
                "Cannot authenticate with the server. Please check your API key and try again.",
            error.message,
        )
        assertEquals(body, error.body)
    }

    @Test
    fun gatewayValidationIssuesAreFlattened() = runTest {
        val body = """{"error":{"message":"[{\"code\":\"invalid_union\",\"path\":[\"questions\",\"q\",\"instructions\"],\"message\":\"Invalid input\"}]","code":400}}"""
        val error = assertFailsWith<BadRequestException> { client(errorEngine(400, body)).use { it.systemOne(request()) } }
        assertTrue(error.message!!.endsWith(": questions.q.instructions: Invalid input"), error.message)
    }

    @Test
    fun redirectsAreUnexpectedStatusNotParsedAsSuccess() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond("<html>moved</html>", HttpStatusCode.MovedPermanently, headersOf(HttpHeaders.Location, "https://elsewhere"))
        }
        val error = assertFailsWith<UnexpectedStatusCodeException> { retryingClient(engine).use { it.systemOne(request()) } }
        assertEquals(301, error.statusCode)
        assertEquals(1, attempts)
    }

    @Test
    fun timeoutAppliesToConnectSocketAndRequest() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(MODELS_RESPONSE)
        }
        client(engine) { timeout(30.seconds) }.use { it.models(RequestOptions { timeout(45.seconds) }) }
        val timeouts = request.getCapabilityOrNull(HttpTimeoutCapability)!!
        assertEquals(45_000L, timeouts.requestTimeoutMillis)
        assertEquals(45_000L, timeouts.connectTimeoutMillis)
        assertEquals(45_000L, timeouts.socketTimeoutMillis)
    }

    @Test
    fun invalidHeadersAndTimeoutsFailFastWithIllegalArgument() {
        assertFailsWith<IllegalArgumentException> { TypeSafeConfig.builder().apiKey("k").putHeader("X-Trace", "a\nb").build() }
        assertFailsWith<IllegalArgumentException> { TypeSafeConfig.builder().apiKey("k").putHeader("Bad Name", "v").build() }
        assertFailsWith<IllegalArgumentException> { RequestOptions { putHeader("X-Trace", "a\r\nInjected: yes") } }
        assertFailsWith<IllegalArgumentException> { RequestOptions { timeout(500.microseconds) } }
    }

    @Test
    fun retryKnobsCanBeTurnedOff() = runTest {
        var attempts = 0
        val flaky = MockEngine {
            attempts++
            throw IOException("connection reset")
        }
        assertFailsWith<ApiConnectionException> {
            client(flaky) { retry(RetryStrategy { retryConnectionErrors(false) }) }.use { it.models() }
        }
        assertEquals(1, attempts)

        val noJitter = object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        val ignoreRetryAfter = RetryStrategy { respectRetryAfter(false) }
        assertEquals(500L, retryDelayMillis(1, Headers.of(mapOf("retry-after-ms" to listOf("9000"))), ignoreRetryAfter, noJitter))

        val unbounded = RetryStrategy { noTotalTimeout() }
        assertNull(unbounded.totalTimeout)
        assertNull(unbounded.totalTimeoutMillis)
        assertEquals(30_000L, RetryStrategy.DEFAULT.totalTimeoutMillis)
        assertEquals(0.25, RetryStrategy.DEFAULT.jitter)
        assertEquals(60_000L, RetryStrategy.DEFAULT.maxRetryAfterMillis)
    }

    @Test
    fun httpClientConfigHookCustomizesTheKtorClient() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(MODELS_RESPONSE)
        }
        client(engine) {
            httpClientConfig { config ->
                config.install(DefaultRequest) { headers.append("X-From-Hook", "yes") }
            }
        }.use { it.models() }
        assertEquals("yes", request.headers["X-From-Hook"])
    }

    @Test
    fun emptyErrorBodyIsNull() = runTest {
        val error = assertFailsWith<AuthenticationException> { client(errorEngine(401, "")).use { it.models() } }
        assertNull(error.body)
    }

    @Test
    fun formatsValidationErrorDetail() = runTest {
        val detail = """{"detail":[{"loc":["body","questions","urgency","score","criteria"],"msg":"Field required","type":"missing"}]}"""
        val error = assertFailsWith<UnprocessableEntityException> {
            client(errorEngine(422, detail)).use { it.systemOne(request()) }
        }
        assertTrue(error.message!!.endsWith(": questions.urgency.score.criteria: Field required"))
    }

    @Test
    fun endpointOmitsCredentialsQueryAndFragment() = runTest {
        val error = assertFailsWith<NotFoundException> {
            client(errorEngine(404, "")) { baseUrl("https://user:secret@proxy.example.com/typesafe/") }.use { it.models() }
        }
        assertEquals("GET https://proxy.example.com/typesafe/v1/models", error.endpoint)
        assertEquals("https://api.example.com/v1/models", sanitizeUrl("https://api.example.com/v1/models?key=1#frag"))
    }

    @Test
    fun apiKeyIsTrimmedAndValidated() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(MODELS_RESPONSE)
        }
        client(engine) { apiKey("  key-123\n") }.use { it.models() }
        assertEquals("Bearer key-123", request.headers[HttpHeaders.Authorization])

        for (invalid in listOf("", "   ", "key 123", "key\u0001", "kéy")) {
            assertFailsWith<IllegalArgumentException>(invalid) { TypeSafeConfig.builder().apiKey(invalid).build() }
        }
    }

    @Test
    fun configValidatesBaseUrlAndTimeout() {
        assertFailsWith<IllegalArgumentException> { TypeSafeConfig.builder().apiKey("k").baseUrl("api.typesafe.ai").build() }
        assertFailsWith<IllegalArgumentException> { TypeSafeConfig.builder().apiKey("k").timeoutMillis(0).build() }
        assertEquals("https://api.typesafe.ai", TypeSafeConfig.builder().apiKey("k").baseUrl("https://api.typesafe.ai/").build().baseUrl)
    }

    @Test
    fun rejectsInvalidQuestions() {
        assertFailsWith<IllegalArgumentException> { SystemOneRequest { state("state") } }
        assertFailsWith<IllegalStateException> { SystemOneRequest { putQuestion("q", NoulQuestion {}) } }
        assertFailsWith<IllegalArgumentException> { ScoreQuestion {} }
        assertFailsWith<IllegalArgumentException> { ScoreQuestion { criteria((1..11).map { Entry("level-$it") }) } }
        assertFailsWith<IllegalArgumentException> { ChoiceQuestion {} }
        assertFailsWith<IllegalArgumentException> { ChoiceQuestion { criteria((1..256).associate { "choice-$it" to Entry("val") }) } }
        assertFailsWith<IllegalArgumentException> { RawQuestion(buildJsonObject { put("instructions", "no type") }) }
    }

    @Test
    fun scoreAcceptsSpecRange() {
        assertEquals(1, ScoreQuestion { addCriterion("only level") }.criteria.size)
        assertEquals(10, ScoreQuestion { criteria((1..10).map { Entry("level-$it") }) }.criteria.size)
    }

    @Test
    fun rawQuestionsAndAdditionalBodyPropertiesPassThrough() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(SYSTEM_ONE_RESPONSE)
        }
        client(engine).use { client ->
            client.systemOne(
                SystemOneRequest {
                    state("state")
                    putQuestion(
                        "billing",
                        RawQuestion(
                            buildJsonObject {
                                put("type", "noul")
                                put("instructions", "About billing?")
                                put("weight", 2)
                            },
                        ),
                    )
                    putAdditionalBodyProperty("beam_width", JsonPrimitive(4))
                    putAdditionalBodyProperty("model", JsonPrimitive("from-extra-body"))
                },
            )
        }
        val body = request.bodyText()
        assertTrue(body.contains("\"billing\":{\"type\":\"noul\",\"instructions\":\"About billing?\",\"weight\":2}"))
        assertTrue(body.contains("\"beam_width\":4"))
        assertTrue(body.contains("\"model\":\"from-extra-body\""))
        assertTrue(!body.contains("jev-latest"))
    }

    @Test
    fun asyncClientAndSyncPivot() = runTest {
        val asyncClient = TypeSafeClientAsync {
            apiKey("test-key")
            engine(MockEngine { ok(SYSTEM_ONE_RESPONSE) })
            logLevel(LogLevel.OFF)
        }
        assertEquals("jev-1.13.0", asyncClient.systemOne(request()).model)
        val syncClient = asyncClient.sync()
        assertEquals("jev-1.13.0", syncClient.systemOne(request()).model)
        assertEquals(asyncClient, syncClient.async())
        asyncClient.close()
    }

    @Test
    fun rawResponseAccessWithCaseInsensitiveHeaders() = runTest {
        val engine = MockEngine {
            respond(
                SYSTEM_ONE_RESPONSE,
                HttpStatusCode.OK,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), "X-TypeSafe-Request-Id" to listOf("req-xyz-123")),
            )
        }
        val client = TypeSafeClient.builder().apiKey("test-key").engine(engine).logLevel(LogLevel.OFF).build()
        val raw = client.withRawResponse().systemOne(request())
        assertEquals(200, raw.statusCode)
        assertEquals("req-xyz-123", raw.requestId)
        assertEquals("req-xyz-123", raw.headers["x-typesafe-request-id"])
        assertEquals("req-xyz-123", raw.value.requestId)
        assertTrue(raw.body.contains("jev-1.13.0"))

        val asyncRaw = client.async().withRawResponse().systemOne(request())
        assertEquals("req-xyz-123", asyncRaw.requestId)
        client.close()
    }

    @Test
    fun withOptionsModifiesConfigurationAndSharesTransport() = runTest {
        lateinit var requestData: HttpRequestData
        val engine = MockEngine {
            requestData = it
            ok(SYSTEM_ONE_RESPONSE)
        }
        val client = client(engine) { defaultModel("default-model") }
        val modified = client.withOptions {
            it.defaultModel("overridden-model")
            it.putHeader("X-Custom", "CustomValue")
        }
        modified.systemOne(request())
        assertTrue(requestData.bodyText().contains("\"model\":\"overridden-model\""))
        assertEquals("CustomValue", requestData.headers["X-Custom"])

        modified.close()
        client.systemOne(request())
        assertTrue(requestData.bodyText().contains("\"model\":\"default-model\""))

        client.close()
        assertFailsWith<IllegalStateException> { modified.systemOne(request()) }
    }

    @Test
    fun closedClientFailsFastWithoutRetrying() = runTest {
        var attempts = 0
        val client = retryingClient(
            MockEngine {
                attempts++
                ok(MODELS_RESPONSE)
            },
        )
        client.close()
        assertFailsWith<IllegalStateException> { client.models() }
        assertEquals(0, attempts)
    }

    @Test
    fun perCallOptionsOverrideRetryHeadersAndTimeout() = runTest {
        var attempts = 0
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            attempts++
            request = it
            throw HttpRequestTimeoutException("https://api.typesafe.ai/v1/models", 250)
        }
        val options = RequestOptions {
            retry(RetryStrategy.NONE)
            timeout(250.milliseconds)
            putHeader("X-Custom", "per-call")
        }
        val error = assertFailsWith<ApiTimeoutException> {
            retryingClient(engine).withOptions { it.putHeader("X-Custom", "client") }.models(options)
        }
        assertEquals(1, attempts)
        assertEquals(250L, error.timeoutMillis)
        assertEquals(listOf("per-call"), request.headers.getAll("X-Custom"))
    }

    @Test
    fun loggingSummarizesRequestsAndRedactsSecrets() = runTest {
        val logged = mutableListOf<Pair<LogLevel, String>>()
        val engine = MockEngine { ok(MODELS_RESPONSE) }
        client(engine) {
            logLevel(LogLevel.DEBUG)
            logger { level, message -> logged += level to message }
            putHeader("X-Session-Token", "tok_abc")
            putHeader("x_api_key", "underscore_secret")
        }.use { it.models() }
        assertTrue(logged.any { (level, message) -> level == LogLevel.INFO && message.startsWith("GET https://api.typesafe.ai/v1/models -> 200") })
        val all = logged.joinToString("\n") { it.second }
        assertTrue(!all.contains("test-key"))
        assertTrue(!all.contains("tok_abc"))
        assertTrue(!all.contains("underscore_secret"))
        assertTrue(all.contains("<redacted>"))

        logged.clear()
        client(engine) {
            logLevel(LogLevel.WARN)
            logger { level, message -> logged += level to message }
        }.use { it.models() }
        assertTrue(logged.isEmpty())
    }

    @Test
    fun modelBuildersRoundTrip() {
        val choice = ChoiceQuestion {
            instructions("i")
            putCriterion("opt1", "val1")
        }
        assertEquals(choice, choice.toBuilder().build())
        val score = ScoreQuestion {
            addCriterion("low")
            addCriterion("high")
        }
        assertEquals(score, score.toBuilder().build())
        val noul = NoulQuestion {
            criteria(
                NoulCriteria {
                    yes("yes")
                    no("no")
                },
            )
        }
        assertEquals(noul, noul.toBuilder().build())
        val request = SystemOneRequest {
            state("s")
            putQuestion("q", noul)
        }
        assertEquals(request, request.toBuilder().build())
        val options = RequestOptions {
            timeoutMillis(5_000)
            putHeader("a", "b")
        }
        assertEquals(options, options.toBuilder().build())
        val retry = RetryStrategy { maxRetries(4) }
        assertEquals(retry, retry.toBuilder().build())
        assertEquals(4, retry.maxRetries)
        assertEquals(RetryStrategy.DEFAULT, RetryStrategy {})
    }

    @Test
    fun builderCollectionsAreCopied() {
        val builder = ChoiceQuestion.builder().putCriterion("a", "A")
        val first = builder.build()
        builder.putCriterion("b", "B")
        assertEquals(setOf("a"), first.criteria.keys)
        assertEquals(setOf("a", "b"), builder.build().criteria.keys)
    }

    @Test
    fun totalRetryBudgetStopsBeforeOverrunningDelay() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond("slow down", HttpStatusCode.TooManyRequests, headersOf("retry-after-ms", "50"))
        }
        val error = assertFailsWith<RateLimitException> {
            client(engine) {
                retry(
                    RetryStrategy {
                        maxRetries(5)
                        totalTimeout(30.milliseconds)
                    },
                )
            }.use { it.models() }
        }
        assertEquals(1, attempts)
        assertEquals(50L, error.retryAfterMillis)
    }

    @Test
    fun timeoutsAreConnectionErrorsAndRetriedByPolicy() = runTest {
        var attempts = 0
        val flaky = MockEngine {
            attempts++
            if (attempts == 1) throw HttpRequestTimeoutException("https://api.typesafe.ai/v1/models", 10_000)
            ok(MODELS_RESPONSE)
        }
        retryingClient(flaky).use { it.models() }
        assertEquals(2, attempts)

        var strictAttempts = 0
        val alwaysTimesOut = MockEngine {
            strictAttempts++
            throw HttpRequestTimeoutException("https://api.typesafe.ai/v1/models", 10_000)
        }
        val error = assertFailsWith<ApiTimeoutException> {
            client(alwaysTimesOut) { retry(RetryStrategy { retryTimeouts(false) }) }.use { it.models() }
        }
        assertIs<ApiConnectionException>(error)
        assertEquals(1, strictAttempts)
    }

    @Test
    fun responseValidationExceptionReportsFieldPath() = runTest {
        val body = """{"model":"jev-1.13.0","answers":{"tone":{"type":"choice","choice":"calm","probabilities":{"calm":1.0}}},"usage":{"input_tokens":1,"output_tokens":1}}"""
        val engine = MockEngine { respond(body, HttpStatusCode.OK, headersOf("x-typesafe-request-id", "req_9")) }
        val error = assertFailsWith<ApiResponseValidationException> { client(engine).use { it.systemOne(request()) } }
        assertEquals("answers.tone.confidence", error.fieldPath)
        assertEquals(200, error.statusCode)
        assertEquals("req_9", error.requestId)
        assertEquals(body, error.body)
    }

    @Test
    fun malformedSuccessBodyIsResponseValidationException() = runTest {
        assertFailsWith<ApiResponseValidationException> { client(MockEngine { ok("""{"model":"jev-1.13.0"}""") }).use { it.systemOne(request()) } }
        assertFailsWith<ApiResponseValidationException> {
            client(MockEngine { respond("<html>gateway</html>", HttpStatusCode.OK) }).use { it.models() }
        }
    }

    @Test
    fun retriesServerErrorsAndOverloadThenSucceeds() = runTest {
        val statuses = ArrayDeque(listOf(503, 529))
        val retryCounts = mutableListOf<String?>()
        val engine = MockEngine { request ->
            retryCounts += request.headers["X-TypeSafe-Retry-Count"]
            val status = statuses.removeFirstOrNull()
            if (status != null) respond("busy", HttpStatusCode.fromValue(status)) else ok(SYSTEM_ONE_RESPONSE)
        }
        assertEquals("jev-1.13.0", retryingClient(engine).use { it.systemOne(request()) }.model)
        assertEquals(listOf(null, "1", "2"), retryCounts)
    }

    @Test
    fun retriesRequestTimeoutRateLimitAndConnectionErrors() = runTest {
        val statuses = ArrayDeque(listOf(408, 429))
        var attempts = 0
        val engine = MockEngine {
            attempts++
            val status = statuses.removeFirstOrNull()
            if (status != null) respond("retry", HttpStatusCode.fromValue(status)) else ok(MODELS_RESPONSE)
        }
        retryingClient(engine).use { it.models() }
        assertEquals(3, attempts)

        var connectionAttempts = 0
        val flaky = MockEngine {
            connectionAttempts++
            if (connectionAttempts == 1) throw IOException("connection reset")
            ok(MODELS_RESPONSE)
        }
        retryingClient(flaky).use { it.models() }
        assertEquals(2, connectionAttempts)
    }

    @Test
    fun doesNotRetryClientErrors() = runTest {
        for (status in listOf(400, 401, 403, 404, 422)) {
            var attempts = 0
            val engine = MockEngine {
                attempts++
                respond("{}", HttpStatusCode.fromValue(status))
            }
            assertFailsWith<ApiException> { retryingClient(engine).use { it.models() } }
            assertEquals(1, attempts, "status $status must not be retried")
        }
    }

    @Test
    fun exhaustedRetriesSurfaceTypedExceptionWithRetryAfter() = runTest {
        var attempts = 0
        val engine = MockEngine {
            attempts++
            respond("slow down", HttpStatusCode.TooManyRequests, headersOf("retry-after-ms" to listOf("1"), "x-typesafe-request-id" to listOf("req_123")))
        }
        val error = assertFailsWith<RateLimitException> { retryingClient(engine).use { it.models() } }
        assertEquals(3, attempts)
        assertEquals(1L, error.retryAfterMillis)
        assertEquals("req_123", error.requestId)
    }

    @Test
    fun userHeadersOverrideDefaultsWithoutDuplicates() = runTest {
        lateinit var request: HttpRequestData
        val engine = MockEngine {
            request = it
            ok(MODELS_RESPONSE)
        }
        client(engine) { putHeader("user-agent", "gateway/1.0") }.use { it.models() }
        assertEquals(listOf("gateway/1.0"), request.headers.getAll(HttpHeaders.UserAgent))
        assertEquals(listOf("Bearer test-key"), request.headers.getAll(HttpHeaders.Authorization))
    }

    @Test
    fun retryDelayFollowsOfficialPolicy() {
        val policy = RetryStrategy.DEFAULT
        val noJitter = object : kotlin.random.Random() {
            override fun nextBits(bitCount: Int): Int = 0
        }
        fun headers(name: String, value: String) = Headers.of(mapOf(name to listOf(value)))
        assertEquals(500L, retryDelayMillis(1, null, policy, noJitter))
        assertEquals(1_000L, retryDelayMillis(2, null, policy, noJitter))
        assertEquals(5_000L, retryDelayMillis(10, null, policy, noJitter))
        assertEquals(250L, retryDelayMillis(1, headers("retry-after-ms", "250"), policy, noJitter))
        assertEquals(20_000L, retryDelayMillis(1, headers("Retry-After", "20"), policy, noJitter))
        assertEquals(500L, retryDelayMillis(1, headers("Retry-After", "120"), policy, noJitter))
        assertEquals(120_000L, retryDelayMillis(1, headers("Retry-After", "120"), RetryStrategy { maxRetryAfter(180.seconds) }, noJitter))
        assertEquals(500L, retryDelayMillis(1, headers("Retry-After", "garbage"), policy, noJitter))
        assertEquals(0L, parseRetryAfterMillis(headers("Retry-After", "Wed, 21 Oct 2015 07:28:00 GMT")))
        assertTrue(529 in policy.retryableStatuses)
        assertTrue(422 !in policy.retryableStatuses)
        assertFailsWith<IllegalArgumentException> { RetryStrategy { retryableStatuses(setOf(700)) } }
    }

    private companion object {
        val SYSTEM_ONE_RESPONSE = """
            {
              "model": "jev-1.13.0",
              "answers": {
                "department": {"type":"choice","choice":"technical","confidence":0.78,"probabilities":{"technical":0.85,"sales":0.0,"billing":0.15}},
                "frustration": {"type":"score","score":1.0,"confidence":1.0,"legend":{"0":"Calm","1":"Frustrated","2":"Angry"},"probabilities":{"0":0.0,"1":1.0,"2":0.0}},
                "is_urgent": {"type":"noul","noul":1.0}
              },
              "usage": {"input_tokens":392,"output_tokens":65}
            }
        """.trimIndent()

        val MODELS_RESPONSE = """
            {"models":[
              {"name":"jev-latest","description":"General-purpose system one model.","release_date":"2026-09-15"},
              {"name":"jev-1.13.0","description":"Pinned Jev release.","release_date":"2026-09-15"}
            ]}
        """.trimIndent()
    }
}
