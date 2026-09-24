package net.bontal.typesafesdk.examples

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import net.bontal.typesafesdk.ChoiceQuestion
import net.bontal.typesafesdk.NoulCriteria
import net.bontal.typesafesdk.NoulQuestion
import net.bontal.typesafesdk.RequestOptions
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClientAsync
import kotlin.time.Duration.Companion.seconds
import kotlin.time.measureTimedValue

object BatchClassification {

    private const val MAX_IN_FLIGHT = 4

    private val reviews = listOf(
        "Arrived two days late and the box was crushed, but the headphones work fine.",
        "Battery lasts all week. Best purchase I've made this year.",
        "The left earbud stopped charging after a month. Support never replied.",
        "Sound is okay for the price. Nothing special.",
        "Returned it. The noise cancelling hisses constantly.",
        "Comfortable enough to wear on a 10 hour flight.",
        "Charging case lid snapped off on day three.",
        "Pairs instantly with my phone and laptop, love it.",
    )

    private val options = RequestOptions { timeout(15.seconds) }

    private fun reviewRequest(review: String) = SystemOneRequest {
        state(review)
        putQuestion(
            "sentiment",
            ChoiceQuestion {
                instructions("Overall sentiment of the product review")
                putCriterion("positive", "Happy with the product")
                putCriterion("mixed", "Both good and bad points, or neutral")
                putCriterion("negative", "Unhappy with the product")
            },
        )
        putQuestion(
            "defect",
            NoulQuestion {
                instructions("The review reports a hardware defect")
                criteria(
                    NoulCriteria {
                        yes("Something physically broke or stopped working")
                        no("No broken part is mentioned")
                    },
                )
            },
        )
    }

    suspend fun run(client: TypeSafeClientAsync) = coroutineScope {
        val limit = Semaphore(MAX_IN_FLIGHT)
        val (results, elapsed) = measureTimedValue {
            reviews.map { review ->
                async { review to limit.withPermit { client.systemOne(reviewRequest(review), options) } }
            }.awaitAll()
        }
        for ((review, result) in results) {
            val sentiment = result.choices.getValue("sentiment").choice
            val defect = result.nouls.getValue("defect").noul >= 0.5
            println("${sentiment.padEnd(8)} defect=${if (defect) "yes" else "no "}  $review")
        }
        val inputTokens = results.sumOf { (_, result) -> result.usage.inputTokens }
        println(
            "Classified ${results.size} reviews in ${elapsed.inWholeMilliseconds} ms using $inputTokens input tokens",
        )
    }
}
