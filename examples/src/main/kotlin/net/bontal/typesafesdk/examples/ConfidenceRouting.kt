package net.bontal.typesafesdk.examples

import net.bontal.typesafesdk.ChoiceQuestion
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClient

object ConfidenceRouting {

    private const val HUMAN_FLOOR = 0.6

    private val autoExecuteThreshold = mapOf(
        "check_balance" to 0.6,
        "freeze_card" to 0.75,
        "transfer_money" to 0.85,
    )

    private val utterances = listOf(
        "How much money do I have in checking?",
        "Send two hundred dollars to my sister Anna.",
        "I think I lost my card somewhere at the mall.",
        "Uh, the thing with the account, can you do it?",
    )

    private val intentQuestion = ChoiceQuestion {
        instructions("Which banking action is the caller asking for")
        putCriterion("check_balance", "Hear an account balance")
        putCriterion("transfer_money", "Move money to another account or person")
        putCriterion("freeze_card", "Lock or freeze a payment card")
        putCriterion("other", "Anything else, or unclear")
    }

    fun run(client: TypeSafeClient) {
        for (utterance in utterances) {
            val intent = client.systemOne(
                SystemOneRequest {
                    state(utterance)
                    putQuestion("intent", intentQuestion)
                },
            ).choices.getValue("intent")

            val threshold = autoExecuteThreshold[intent.choice]
            val action = when {
                intent.confidence < HUMAN_FLOOR || threshold == null -> "transfer to a human agent"
                intent.confidence >= threshold -> "execute ${intent.choice}"
                else -> "ask the caller to confirm ${intent.choice}"
            }
            println("\"$utterance\"")
            println("  ${intent.choice} at ${percent(intent.confidence)} confidence -> $action")
        }
    }
}
