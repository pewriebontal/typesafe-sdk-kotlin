package net.bontal.typesafesdk

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import net.bontal.typesafesdk.internal.toEntryOrNull
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public sealed interface Question {
    public val instructions: Entry?
}

public class NoulQuestion private constructor(
    override val instructions: Entry?,
    public val criteria: NoulCriteria?,
) : Question {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoulQuestion) return false
        return instructions == other.instructions && criteria == other.criteria
    }

    override fun hashCode(): Int {
        var result = instructions?.hashCode() ?: 0
        result = 31 * result + (criteria?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "NoulQuestion(instructions=$instructions, criteria=$criteria)"

    public fun toBuilder(): Builder = Builder()
        .instructions(instructions)
        .criteria(criteria)

    public class Builder internal constructor() {
        private var instructions: Entry? = null
        private var criteria: NoulCriteria? = null

        public fun instructions(instructions: Entry?): Builder = apply { this.instructions = instructions }

        public fun instructions(text: String): Builder = instructions(Entry.Text(text))

        public fun criteria(criteria: NoulCriteria?): Builder = apply { this.criteria = criteria }

        public fun build(): NoulQuestion = NoulQuestion(instructions, criteria)
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class NoulCriteria private constructor(
    public val yes: Entry?,
    public val no: Entry?,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is NoulCriteria) return false
        return yes == other.yes && no == other.no
    }

    override fun hashCode(): Int {
        var result = yes?.hashCode() ?: 0
        result = 31 * result + (no?.hashCode() ?: 0)
        return result
    }

    override fun toString(): String = "NoulCriteria(yes=$yes, no=$no)"

    public fun toBuilder(): Builder = Builder()
        .yes(yes)
        .no(no)

    public class Builder internal constructor() {
        private var yes: Entry? = null
        private var no: Entry? = null

        public fun yes(yes: Entry?): Builder = apply { this.yes = yes }

        public fun yes(text: String): Builder = yes(Entry.Text(text))

        public fun no(no: Entry?): Builder = apply { this.no = no }

        public fun no(text: String): Builder = no(Entry.Text(text))

        public fun build(): NoulCriteria = NoulCriteria(yes, no)
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class ChoiceQuestion private constructor(
    override val instructions: Entry?,
    criteria: Map<String, Entry?>,
) : Question {
    init {
        require(criteria.isNotEmpty()) { "criteria must not be empty" }
        require(criteria.size <= MAX_CHOICES) { "criteria must not exceed $MAX_CHOICES choices, was ${criteria.size}" }
    }

    public val criteria: Map<String, Entry?> = criteria.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChoiceQuestion) return false
        return instructions == other.instructions && criteria == other.criteria
    }

    override fun hashCode(): Int {
        var result = instructions?.hashCode() ?: 0
        result = 31 * result + criteria.hashCode()
        return result
    }

    override fun toString(): String = "ChoiceQuestion(instructions=$instructions, criteria=$criteria)"

    public fun toBuilder(): Builder = Builder()
        .instructions(instructions)
        .criteria(criteria)

    public class Builder internal constructor() {
        private var instructions: Entry? = null
        private val criteria = LinkedHashMap<String, Entry?>()

        public fun instructions(instructions: Entry?): Builder = apply { this.instructions = instructions }

        public fun instructions(text: String): Builder = instructions(Entry.Text(text))

        public fun criteria(criteria: Map<String, Entry?>): Builder = apply {
            this.criteria.clear()
            this.criteria.putAll(criteria)
        }

        public fun putCriterion(name: String, description: Entry?): Builder = apply { criteria[name] = description }

        public fun putCriterion(name: String, description: String): Builder = putCriterion(name, Entry.Text(description))

        public fun build(): ChoiceQuestion = ChoiceQuestion(instructions, criteria)
    }

    public companion object {
        public const val MAX_CHOICES: Int = 255

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class ScoreQuestion private constructor(
    override val instructions: Entry?,
    criteria: List<Entry>,
) : Question {
    init {
        require(criteria.size in 1..MAX_LEVELS) { "criteria must contain between 1 and $MAX_LEVELS score levels, was ${criteria.size}" }
    }

    public val criteria: List<Entry> = criteria.toList()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScoreQuestion) return false
        return instructions == other.instructions && criteria == other.criteria
    }

    override fun hashCode(): Int {
        var result = instructions?.hashCode() ?: 0
        result = 31 * result + criteria.hashCode()
        return result
    }

    override fun toString(): String = "ScoreQuestion(instructions=$instructions, criteria=$criteria)"

    public fun toBuilder(): Builder = Builder()
        .instructions(instructions)
        .criteria(criteria)

    public class Builder internal constructor() {
        private var instructions: Entry? = null
        private val criteria = ArrayList<Entry>()

        public fun instructions(instructions: Entry?): Builder = apply { this.instructions = instructions }

        public fun instructions(text: String): Builder = instructions(Entry.Text(text))

        public fun criteria(criteria: List<Entry>): Builder = apply {
            this.criteria.clear()
            this.criteria.addAll(criteria)
        }

        public fun addCriterion(level: Entry): Builder = apply { criteria.add(level) }

        public fun addCriterion(level: String): Builder = addCriterion(Entry.Text(level))

        public fun build(): ScoreQuestion = ScoreQuestion(instructions, criteria)
    }

    public companion object {
        public const val MAX_LEVELS: Int = 10

        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class RawQuestion(public val json: JsonObject) : Question {
    init {
        require((json["type"] as? JsonPrimitive)?.isString == true) { "json must contain a string \"type\" field" }
    }

    public val type: String get() = (json.getValue("type") as JsonPrimitive).content

    override val instructions: Entry? get() = json["instructions"]?.toEntryOrNull()

    override fun equals(other: Any?): Boolean = this === other || (other is RawQuestion && json == other.json)

    override fun hashCode(): Int = json.hashCode()

    override fun toString(): String = "RawQuestion(json=$json)"
}

@JvmSynthetic
public fun NoulQuestion(configure: NoulQuestion.Builder.() -> Unit): NoulQuestion = NoulQuestion.builder().apply(configure).build()

@JvmSynthetic
public fun NoulCriteria(configure: NoulCriteria.Builder.() -> Unit): NoulCriteria = NoulCriteria.builder().apply(configure).build()

@JvmSynthetic
public fun ChoiceQuestion(configure: ChoiceQuestion.Builder.() -> Unit): ChoiceQuestion = ChoiceQuestion.builder().apply(configure).build()

@JvmSynthetic
public fun ScoreQuestion(configure: ScoreQuestion.Builder.() -> Unit): ScoreQuestion = ScoreQuestion.builder().apply(configure).build()
