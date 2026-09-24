package net.bontal.typesafesdk.examples

import net.bontal.typesafesdk.ChoiceQuestion
import net.bontal.typesafesdk.ScoreQuestion
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClient

object SupportTriage {

    private val tickets = listOf(
        "The export button crashes the app every time I click it and I've lost two hours of work.",
        "Can you send last month's invoice again with our VAT number on it?",
        "Is there a way to share a dashboard with someone outside my company?",
        "Just wanted to say the new timeline view is fantastic, thank you!",
    )

    private fun triageRequest(ticket: String) = SystemOneRequest {
        state(ticket)
        putQuestion(
            "category",
            ChoiceQuestion {
                instructions("What kind of support ticket is this")
                putCriterion("bug", "Something is broken or behaves incorrectly")
                putCriterion("billing", "Invoices, payments, refunds, or plans")
                putCriterion("how_to", "A question about how to use the product")
                putCriterion("feedback", "Praise or suggestions with no action needed")
            },
        )
        putQuestion(
            "severity",
            ScoreQuestion {
                instructions("If this is a bug report, how severe is the bug")
                addCriterion("Cosmetic or minor inconvenience")
                addCriterion("Degrades a workflow but has a workaround")
                addCriterion("Blocks work or loses data")
            },
        )
        putQuestion(
            "frustration",
            ScoreQuestion {
                instructions("How frustrated does the customer sound")
                addCriterion("Calm")
                addCriterion("Annoyed")
                addCriterion("Angry")
            },
        )
    }

    fun run(client: TypeSafeClient) {
        for (ticket in tickets) {
            val result = client.systemOne(triageRequest(ticket))
            val category = result.choices.getValue("category")
            val severity = result.scores.getValue("severity").score
            val frustration = result.scores.getValue("frustration").score

            val queue = when (category.choice) {
                "bug" -> if (severity >= 1.5) "page the on-call engineer" else "engineering backlog"
                "billing" -> "billing team"
                "how_to" -> "help-center bot"
                else -> "product feedback board"
            }
            val priority = if (frustration >= 1.5) "high" else "normal"

            println("\"${ticket.take(60)}...\"")
            val confidence = percent(category.confidence)
            println("  category=${category.choice} ($confidence confident) -> $queue, priority $priority")
        }
    }
}
