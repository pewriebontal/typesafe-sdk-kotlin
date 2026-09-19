package net.bontal.typesafesdk.examples

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.bontal.typesafesdk.ApiConnectionException
import net.bontal.typesafesdk.ApiException
import net.bontal.typesafesdk.ApiTimeoutException
import net.bontal.typesafesdk.AuthenticationException
import net.bontal.typesafesdk.NoulQuestion
import net.bontal.typesafesdk.RateLimitException
import net.bontal.typesafesdk.RawQuestion
import net.bontal.typesafesdk.RequestOptions
import net.bontal.typesafesdk.RetryStrategy
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClient
import net.bontal.typesafesdk.TypeSafeException
import kotlin.time.Duration.Companion.seconds

object ErrorHandling {

    private fun attempt(label: String, block: () -> Unit) {
        try {
            block()
            println("$label: ok")
        } catch (e: RateLimitException) {
            println("$label: rate limited, retry after ${e.retryAfterMillis} ms")
        } catch (e: AuthenticationException) {
            println("$label: check the API key (${e.message})")
        } catch (e: ApiException) {
            println("$label: ${e.message}")
        } catch (e: ApiTimeoutException) {
            println("$label: timed out after ${e.timeout}")
        } catch (e: ApiConnectionException) {
            println("$label: network problem: ${e.message}")
        } catch (e: TypeSafeException) {
            println("$label: ${e.message}")
        }
    }

    fun run(client: TypeSafeClient) {
        val request = SystemOneRequest {
            state("Please cancel my order #4417, it hasn't shipped yet.")
            putQuestion("cancel", NoulQuestion { instructions("The customer wants to cancel an order") })
        }

        attempt("raw response") {
            val raw = client.withRawResponse().systemOne(request)
            println("  status ${raw.statusCode}, request ${raw.requestId ?: "n/a"}, noul ${raw.value.nouls.getValue("cancel").noul}")
        }

        attempt("per-call timeout, no retries") {
            client.systemOne(
                request,
                RequestOptions {
                    timeout(5.seconds)
                    retry(RetryStrategy.NONE)
                },
            )
        }

        attempt("patient copy") {
            val patient = client.withOptions { it.timeout(60.seconds).retry(RetryStrategy { maxRetries(5) }) }
            patient.systemOne(request)
        }

        attempt("invalid question") {
            client.systemOne(
                SystemOneRequest {
                    state("anything")
                    putQuestion(
                        "broken",
                        RawQuestion(
                            buildJsonObject {
                                put("type", "score")
                                put("instructions", "no criteria")
                            },
                        ),
                    )
                },
            )
        }

        attempt("wrong API key") {
            client.withOptions { it.apiKey("not-a-real-key") }.systemOne(request)
        }
    }
}
