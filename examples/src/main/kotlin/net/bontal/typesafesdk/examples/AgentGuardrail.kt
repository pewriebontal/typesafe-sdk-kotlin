package net.bontal.typesafesdk.examples

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import net.bontal.typesafesdk.Entry
import net.bontal.typesafesdk.NoulCriteria
import net.bontal.typesafesdk.NoulQuestion
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClientAsync

object AgentGuardrail {

    private const val EXECUTE_AT = 0.8
    private const val REFUSE_BELOW = 0.2

    private data class ToolCall(val tool: String, val arguments: String)

    private const val USER_REQUEST = "Clean up my downloads folder by deleting installers older than a month."

    private val proposedCalls = listOf(
        ToolCall("list_files", "{\"path\": \"~/Downloads\"}"),
        ToolCall("delete_files", "{\"glob\": \"~/Downloads/*.dmg\", \"older_than_days\": 30}"),
        ToolCall("delete_files", "{\"glob\": \"~/Documents/**\"}"),
        ToolCall("send_email", "{\"to\": \"it@company.com\", \"body\": \"Downloads cleaned\"}"),
    )

    private val supported = NoulQuestion {
        instructions("The proposed tool call is something the user asked for or clearly implied")
        criteria(
            NoulCriteria {
                yes("The call directly serves the user's request and stays within its scope")
                no("The call goes beyond the request, touches unrelated data, or was never asked for")
            },
        )
    }

    private suspend fun review(client: TypeSafeClientAsync, call: ToolCall): Pair<ToolCall, Double> {
        val result = client.systemOne(
            SystemOneRequest {
                state(
                    Entry(
                        buildJsonObject {
                            put("user_request", USER_REQUEST)
                            put("tool", call.tool)
                            put("arguments", call.arguments)
                        },
                    ),
                )
                putQuestion("supported", supported)
            },
        )
        return call to result.nouls.getValue("supported").noul
    }

    suspend fun run(client: TypeSafeClientAsync) = coroutineScope {
        val reviews = proposedCalls.map { call -> async { review(client, call) } }.awaitAll()
        for ((call, probability) in reviews) {
            val verdict = when {
                probability >= EXECUTE_AT -> "run automatically"
                probability < REFUSE_BELOW -> "refuse"
                else -> "pause for human approval"
            }
            println("${call.tool}(${call.arguments}) -> ${percent(probability)} supported -> $verdict")
        }
    }
}
