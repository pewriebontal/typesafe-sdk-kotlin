package net.bontal.typesafesdk.examples

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.bontal.typesafesdk.ChoiceQuestion
import net.bontal.typesafesdk.Entry
import net.bontal.typesafesdk.ScoreQuestion
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClient

object IntentRouting {

    private data class Message(val channel: String, val plan: String, val text: String)

    private val inbox = listOf(
        Message("chat", "free", "What are your support hours?"),
        Message(
            "email",
            "enterprise",
            "Our SSO login started failing after we rotated the SAML certificate this morning.",
        ),
        Message(
            "chat",
            "pro",
            "I want to cancel and get a refund for the unused months, and I'm considering legal action.",
        ),
    )

    fun run(client: TypeSafeClient) {
        for (message in inbox) {
            val raw = client.withRawResponse().systemOne(
                SystemOneRequest {
                    state(
                        Entry(
                            buildJsonObject {
                                put("channel", message.channel)
                                put("plan", message.plan)
                                put("message", message.text)
                            },
                        ),
                    )
                    putQuestion(
                        "intent",
                        ChoiceQuestion {
                            instructions("What does the customer want, based on the message field")
                            putCriterion("faq", "A general question answered by public documentation")
                            putCriterion("technical", "A product or integration problem")
                            putCriterion("account", "Cancellation, refunds, or contract changes")
                        },
                    )
                    putQuestion(
                        "complexity",
                        ScoreQuestion {
                            instructions("How much expertise is needed to resolve the message")
                            addCriterion("A canned answer")
                            addCriterion("A knowledgeable agent")
                            addCriterion("A specialist or manager")
                        },
                    )
                },
            )
            val intent = raw.value.choices.getValue("intent")
            val complexity = raw.value.scores.getValue("complexity")

            val handler = when {
                intent.choice == "faq" && complexity.score < 0.5 -> "answer from the FAQ database"
                complexity.confidence < 0.5 -> "human triage (complexity unclear)"
                complexity.score >= 1.5 -> "escalate to a specialist"
                else -> "LLM agent with ${intent.choice} tools"
            }
            println("[${message.channel}/${message.plan}] ${message.text.take(60)}")
            val score = "%.2f".format(complexity.score)
            val requestId = raw.requestId?.let { " (request $it)" }.orEmpty()
            println("  ${intent.choice}, complexity $score -> $handler$requestId")
        }
    }
}
