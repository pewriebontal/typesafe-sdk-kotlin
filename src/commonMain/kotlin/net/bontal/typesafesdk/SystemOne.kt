package net.bontal.typesafesdk

import kotlinx.serialization.json.JsonElement
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public class SystemOneRequest private constructor(
    public val state: Entry,
    questions: Map<String, Question>,
    public val model: String?,
    additionalBodyProperties: Map<String, JsonElement>,
) {
    init {
        require(questions.isNotEmpty()) { "questions must not be empty" }
        require(model == null || model.isNotBlank()) { "model must not be blank" }
    }

    public val questions: Map<String, Question> = questions.toMap()

    public val additionalBodyProperties: Map<String, JsonElement> = additionalBodyProperties.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SystemOneRequest) return false
        return state == other.state &&
            questions == other.questions &&
            model == other.model &&
            additionalBodyProperties == other.additionalBodyProperties
    }

    override fun hashCode(): Int {
        var result = state.hashCode()
        result = 31 * result + questions.hashCode()
        result = 31 * result + (model?.hashCode() ?: 0)
        result = 31 * result + additionalBodyProperties.hashCode()
        return result
    }

    override fun toString(): String = "SystemOneRequest(state=$state, questions=$questions, model=$model, additionalBodyProperties=$additionalBodyProperties)"

    public fun toBuilder(): Builder = Builder()
        .state(state)
        .questions(questions)
        .model(model)
        .additionalBodyProperties(additionalBodyProperties)

    public class Builder internal constructor() {
        private var state: Entry? = null
        private val questions = LinkedHashMap<String, Question>()
        private var model: String? = null
        private val additionalBodyProperties = LinkedHashMap<String, JsonElement>()

        public fun state(state: Entry): Builder = apply { this.state = state }

        public fun state(text: String): Builder = state(Entry.Text(text))

        public fun questions(questions: Map<String, Question>): Builder = apply {
            this.questions.clear()
            this.questions.putAll(questions)
        }

        public fun putQuestion(name: String, question: Question): Builder = apply { questions[name] = question }

        public fun model(model: String?): Builder = apply { this.model = model }

        public fun additionalBodyProperties(properties: Map<String, JsonElement>): Builder = apply {
            additionalBodyProperties.clear()
            additionalBodyProperties.putAll(properties)
        }

        public fun putAdditionalBodyProperty(name: String, value: JsonElement): Builder = apply {
            additionalBodyProperties[name] = value
        }

        public fun build(): SystemOneRequest = SystemOneRequest(
            state = checkNotNull(state) { "state is required" },
            questions = questions,
            model = model,
            additionalBodyProperties = additionalBodyProperties,
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class SystemOneResult private constructor(
    public val model: String,
    answers: Map<String, Answer>,
    public val usage: TokenUsage,
    public val requestId: String?,
) {
    public val answers: Map<String, Answer> = answers.toMap()

    public val choices: Map<String, ChoiceAnswer> = answersOf()

    public val scores: Map<String, ScoreAnswer> = answersOf()

    public val nouls: Map<String, NoulAnswer> = answersOf()

    private inline fun <reified T : Answer> answersOf(): Map<String, T> = buildMap {
        for ((name, answer) in answers) {
            if (answer is T) put(name, answer)
        }
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SystemOneResult) return false
        return model == other.model && answers == other.answers && usage == other.usage && requestId == other.requestId
    }

    override fun hashCode(): Int {
        var result = model.hashCode()
        result = 31 * result + answers.hashCode()
        result = 31 * result + usage.hashCode()
        result = 31 * result + (requestId?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "SystemOneResult(model=$model, answers=$answers, usage=$usage, requestId=$requestId)"

    public fun toBuilder(): Builder = Builder()
        .model(model)
        .answers(answers)
        .usage(usage)
        .requestId(requestId)

    public class Builder internal constructor() {
        private var model: String? = null
        private val answers = LinkedHashMap<String, Answer>()
        private var usage: TokenUsage? = null
        private var requestId: String? = null

        public fun model(model: String): Builder = apply { this.model = model }

        public fun answers(answers: Map<String, Answer>): Builder = apply {
            this.answers.clear()
            this.answers.putAll(answers)
        }

        public fun putAnswer(name: String, answer: Answer): Builder = apply { answers[name] = answer }

        public fun usage(usage: TokenUsage): Builder = apply { this.usage = usage }

        public fun requestId(requestId: String?): Builder = apply { this.requestId = requestId }

        public fun build(): SystemOneResult = SystemOneResult(
            model = checkNotNull(model) { "model is required" },
            answers = answers,
            usage = checkNotNull(usage) { "usage is required" },
            requestId = requestId,
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class TokenUsage private constructor(
    public val inputTokens: Long,
    public val outputTokens: Long,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is TokenUsage) return false
        return inputTokens == other.inputTokens && outputTokens == other.outputTokens
    }

    override fun hashCode(): Int = 31 * inputTokens.hashCode() + outputTokens.hashCode()

    override fun toString(): String = "TokenUsage(inputTokens=$inputTokens, outputTokens=$outputTokens)"

    public fun toBuilder(): Builder = Builder()
        .inputTokens(inputTokens)
        .outputTokens(outputTokens)

    public class Builder internal constructor() {
        private var inputTokens: Long? = null
        private var outputTokens: Long? = null

        public fun inputTokens(inputTokens: Long): Builder = apply { this.inputTokens = inputTokens }

        public fun outputTokens(outputTokens: Long): Builder = apply { this.outputTokens = outputTokens }

        public fun build(): TokenUsage = TokenUsage(
            inputTokens = checkNotNull(inputTokens) { "inputTokens is required" },
            outputTokens = checkNotNull(outputTokens) { "outputTokens is required" },
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

@JvmSynthetic
public fun SystemOneRequest(configure: SystemOneRequest.Builder.() -> Unit): SystemOneRequest = SystemOneRequest.builder().apply(configure).build()
