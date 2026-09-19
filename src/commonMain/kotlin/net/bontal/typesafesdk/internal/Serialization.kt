package net.bontal.typesafesdk.internal

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import net.bontal.typesafesdk.Answer
import net.bontal.typesafesdk.ChoiceAnswer
import net.bontal.typesafesdk.ChoiceQuestion
import net.bontal.typesafesdk.Entry
import net.bontal.typesafesdk.ModelCard
import net.bontal.typesafesdk.NoulAnswer
import net.bontal.typesafesdk.NoulQuestion
import net.bontal.typesafesdk.Question
import net.bontal.typesafesdk.RawQuestion
import net.bontal.typesafesdk.ScoreAnswer
import net.bontal.typesafesdk.ScoreQuestion
import net.bontal.typesafesdk.SystemOneRequest
import net.bontal.typesafesdk.SystemOneResult
import net.bontal.typesafesdk.TokenUsage

internal val DefaultJson: Json = Json { ignoreUnknownKeys = true }

internal fun SystemOneRequest.toJson(defaultModel: String): JsonObject = buildJsonObject {
    put("state", state.toJson())
    put("model", model ?: defaultModel)
    put(
        "questions",
        buildJsonObject {
            for ((name, question) in questions) {
                put(name, question.toJson())
            }
        },
    )
    for ((name, value) in additionalBodyProperties) {
        put(name, value)
    }
}

internal fun Question.toJson(): JsonObject = when (this) {
    is NoulQuestion -> buildJsonObject {
        put("type", "noul")
        instructions?.let { put("instructions", it.toJson()) }
        criteria?.let { criteria ->
            put(
                "criteria",
                buildJsonObject {
                    criteria.yes?.let { put("true", it.toJson()) }
                    criteria.no?.let { put("false", it.toJson()) }
                },
            )
        }
    }

    is ChoiceQuestion -> buildJsonObject {
        put("type", "choice")
        instructions?.let { put("instructions", it.toJson()) }
        put(
            "criteria",
            buildJsonObject {
                for ((label, description) in criteria) {
                    put(label, description?.toJson() ?: JsonNull)
                }
            },
        )
    }

    is ScoreQuestion -> buildJsonObject {
        put("type", "score")
        instructions?.let { put("instructions", it.toJson()) }
        put(
            "criteria",
            buildJsonArray {
                for (level in criteria) {
                    add(level.toJson())
                }
            },
        )
    }

    is RawQuestion -> json
}

internal fun Entry.toJson(): JsonElement = when (this) {
    is Entry.Text -> JsonPrimitive(value)
    is Entry.Object -> value
    is Entry.Array -> value
}

internal fun JsonElement.toEntryOrNull(): Entry? = when (this) {
    is JsonPrimitive -> if (isString) Entry.Text(content) else null
    is JsonObject -> Entry.Object(this)
    is JsonArray -> Entry.Array(this)
}

internal class ResponseFieldException(val fieldPath: String, message: String) : RuntimeException(message)

private fun child(path: String, key: String): String = if (path.isEmpty()) key else "$path.$key"

private fun JsonElement.obj(path: String): JsonObject = this as? JsonObject ?: throw ResponseFieldException(path.ifEmpty { "<root>" }, "expected an object")

private fun JsonObject.field(path: String, key: String): JsonElement = this[key]?.takeUnless { it is JsonNull } ?: throw ResponseFieldException(child(path, key), "missing required field")

private fun JsonElement.string(path: String): String = (this as? JsonPrimitive)?.takeIf { it.isString }?.content ?: throw ResponseFieldException(path, "expected a string")

private fun JsonElement.number(path: String): Double = (this as? JsonPrimitive)?.takeUnless { it.isString }?.doubleOrNull ?: throw ResponseFieldException(path, "expected a number")

private fun JsonElement.integer(path: String): Long = (this as? JsonPrimitive)?.takeUnless { it.isString }?.longOrNull ?: throw ResponseFieldException(path, "expected an integer")

private fun JsonElement.entry(path: String): Entry = toEntryOrNull() ?: throw ResponseFieldException(path, "expected a string, object, or array")

private fun JsonElement.numbers(path: String): Map<String, Double> = obj(path).mapValues { (key, value) -> value.number(child(path, key)) }

internal fun JsonElement.toSystemOneResult(
    requestId: String?,
    onUnknownAnswer: (name: String, type: String) -> Unit,
): SystemOneResult {
    val root = obj("")
    val builder = SystemOneResult.builder()
        .model(root.field("", "model").string("model"))
        .usage(root.field("", "usage").toTokenUsage("usage"))
        .requestId(requestId)
    for ((name, element) in root.field("", "answers").obj("answers")) {
        val path = child("answers", name)
        val type = element.obj(path).field(path, "type").string(child(path, "type"))
        val answer = element.toAnswer(path, type)
        if (answer == null) onUnknownAnswer(name, type) else builder.putAnswer(name, answer)
    }
    return builder.build()
}

private fun JsonElement.toTokenUsage(path: String): TokenUsage {
    val usage = obj(path)
    return TokenUsage.builder()
        .inputTokens(usage.field(path, "input_tokens").integer(child(path, "input_tokens")))
        .outputTokens(usage.field(path, "output_tokens").integer(child(path, "output_tokens")))
        .build()
}

private fun JsonElement.toAnswer(path: String, type: String): Answer? {
    val answer = obj(path)
    fun required(key: String): JsonElement = answer.field(path, key)
    return when (type) {
        "noul" -> NoulAnswer.builder()
            .noul(required("noul").number(child(path, "noul")))
            .build()

        "choice" -> ChoiceAnswer.builder()
            .choice(required("choice").string(child(path, "choice")))
            .confidence(required("confidence").number(child(path, "confidence")))
            .probabilities(required("probabilities").numbers(child(path, "probabilities")))
            .build()

        "score" -> ScoreAnswer.builder()
            .score(required("score").number(child(path, "score")))
            .confidence(required("confidence").number(child(path, "confidence")))
            .legend(
                required("legend").obj(child(path, "legend"))
                    .mapValues { (level, value) -> value.entry(child(child(path, "legend"), level)) },
            )
            .probabilities(required("probabilities").numbers(child(path, "probabilities")))
            .build()

        else -> null
    }
}

internal fun JsonElement.toModelCards(): List<ModelCard> {
    val models = obj("").field("", "models") as? JsonArray ?: throw ResponseFieldException("models", "expected an array")
    return models.mapIndexed { index, element ->
        val path = "models.$index"
        val card = element.obj(path)
        ModelCard.builder()
            .name(card.field(path, "name").string(child(path, "name")))
            .description(card.field(path, "description").string(child(path, "description")))
            .releaseDate(card.field(path, "release_date").string(child(path, "release_date")))
            .build()
    }
}

internal fun errorDetail(body: String): String? {
    val json = runCatching { DefaultJson.parseToJsonElement(body) }.getOrNull()
        ?: return body.trim().take(MAX_DETAIL_LENGTH).ifEmpty { null }
    val root = json as? JsonObject ?: return null
    val detail = when (val detail = root["detail"]) {
        is JsonPrimitive -> detail.content
        is JsonArray -> validationDetail(detail)
        is JsonObject -> detail.message()
        else -> root.message() ?: (root["error"] as? JsonObject)?.message()
    }
    return detail?.let(::issuesDetail) ?: detail
}

private fun JsonObject.message(): String? = (this["message"] as? JsonPrimitive)?.content

private fun issuesDetail(message: String): String? {
    val issues = runCatching { DefaultJson.parseToJsonElement(message) }.getOrNull() as? JsonArray ?: return null
    return issues.mapNotNull { issue ->
        val item = issue as? JsonObject ?: return@mapNotNull null
        val text = item.message() ?: return@mapNotNull null
        val path = (item["path"] as? JsonArray)?.joinToString(".") { (it as? JsonPrimitive)?.content.orEmpty() }
        if (path.isNullOrEmpty()) text else "$path: $text"
    }.joinToString("; ").ifEmpty { null }
}

private fun validationDetail(errors: JsonArray): String? = errors.mapNotNull { error ->
    val item = error as? JsonObject ?: return@mapNotNull null
    val message = (item["msg"] as? JsonPrimitive)?.content ?: return@mapNotNull null
    val field = (item["loc"] as? JsonArray)?.drop(1)?.joinToString(".") { (it as? JsonPrimitive)?.content.orEmpty() }
    if (field.isNullOrEmpty()) message else "$field: $message"
}.joinToString("; ").ifEmpty { null }

private const val MAX_DETAIL_LENGTH = 500
