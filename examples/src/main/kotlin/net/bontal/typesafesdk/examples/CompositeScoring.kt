package net.bontal.typesafesdk.examples

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import net.bontal.typesafesdk.Entry
import net.bontal.typesafesdk.ScoreQuestion
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.TypeSafeClientAsync

object CompositeScoring {

    private val candidates = mapOf(
        "Priya" to
            "8 years backend engineering; led the migration of a payments monolith to services; mentors 3 engineers.",
        "Marco" to "2 years as a frontend developer building React dashboards; hackathon winner.",
        "Lena" to
            "12 years across infrastructure and SRE; managed a team of 9; designed a multi-region failover system.",
    )

    private val dimensions = mapOf(
        "experience" to listOf("Under 3 years", "3 to 7 years", "Over 7 years"),
        "system_design" to listOf("No evidence", "Designed components", "Designed large systems"),
        "leadership" to listOf("None", "Mentoring or tech lead", "Managed a team"),
    )

    private val weightsByRole = mapOf(
        "Senior IC" to mapOf("experience" to 0.3, "system_design" to 0.5, "leadership" to 0.2),
        "Engineering Manager" to mapOf("experience" to 0.2, "system_design" to 0.2, "leadership" to 0.6),
    )

    private suspend fun scoreCandidate(client: TypeSafeClientAsync, resume: String): Map<String, Double> {
        val result = client.systemOne(
            SystemOneRequest {
                state(resume)
                for ((dimension, levels) in dimensions) {
                    putQuestion(
                        dimension,
                        ScoreQuestion {
                            instructions("Rate the candidate's ${dimension.replace('_', ' ')}")
                            criteria(levels.map { Entry(it) })
                        },
                    )
                }
            },
        )
        return dimensions.mapValues { (dimension, levels) ->
            result.scores.getValue(dimension).score / (levels.size - 1)
        }
    }

    suspend fun run(client: TypeSafeClientAsync) = coroutineScope {
        val scores = candidates.map { (name, resume) ->
            async { name to scoreCandidate(client, resume) }
        }.awaitAll().toMap()
        for ((role, weights) in weightsByRole) {
            println(role)
            scores.mapValues { (_, normalized) ->
                weights.entries.sumOf { (dimension, weight) ->
                    weight *
                        normalized.getValue(dimension)
                }
            }
                .entries
                .sortedByDescending { it.value }
                .forEachIndexed { rank, (name, composite) ->
                    println("  ${rank + 1}. $name ${"%.2f".format(composite)}")
                }
        }
    }
}
