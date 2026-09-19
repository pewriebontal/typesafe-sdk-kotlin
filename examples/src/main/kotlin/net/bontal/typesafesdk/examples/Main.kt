package net.bontal.typesafesdk.examples

import kotlinx.coroutines.runBlocking

private val syncExamples = mapOf(
    "support-triage" to SupportTriage::run,
    "confidence-routing" to ConfidenceRouting::run,
    "intent-routing" to IntentRouting::run,
    "error-handling" to ErrorHandling::run,
    "java" to JavaQuickstart::run,
)

private val asyncExamples = mapOf(
    "agent-guardrail" to AgentGuardrail::run,
    "batch-classification" to BatchClassification::run,
    "composite-scoring" to CompositeScoring::run,
)

fun main(args: Array<String>) {
    val names = args.toList().ifEmpty { syncExamples.keys + asyncExamples.keys }
    for (name in names) {
        println("=== $name")
        when (name) {
            in syncExamples -> syncClient().use { syncExamples.getValue(name)(it) }
            in asyncExamples -> asyncClient().use { client -> runBlocking { asyncExamples.getValue(name)(client) } }
            else -> println("Unknown example `$name`; choose from ${syncExamples.keys + asyncExamples.keys}")
        }
        println()
    }
}
