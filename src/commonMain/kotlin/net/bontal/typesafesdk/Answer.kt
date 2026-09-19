package net.bontal.typesafesdk

import kotlin.jvm.JvmStatic

public sealed interface Answer

public class NoulAnswer private constructor(
    public val noul: Double,
) : Answer {
    override fun equals(other: Any?): Boolean = this === other || (other is NoulAnswer && noul == other.noul)

    override fun hashCode(): Int = noul.hashCode()

    override fun toString(): String = "NoulAnswer(noul=$noul)"

    public fun toBuilder(): Builder = Builder().noul(noul)

    public class Builder internal constructor() {
        private var noul: Double? = null

        public fun noul(noul: Double): Builder = apply { this.noul = noul }

        public fun build(): NoulAnswer = NoulAnswer(
            noul = checkNotNull(noul) { "noul is required" },
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class ChoiceAnswer private constructor(
    public val choice: String,
    public val confidence: Double,
    probabilities: Map<String, Double>,
) : Answer {
    public val probabilities: Map<String, Double> = probabilities.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ChoiceAnswer) return false
        return choice == other.choice && confidence == other.confidence && probabilities == other.probabilities
    }

    override fun hashCode(): Int {
        var result = choice.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + probabilities.hashCode()
        return result
    }

    override fun toString(): String = "ChoiceAnswer(choice=$choice, confidence=$confidence, probabilities=$probabilities)"

    public fun toBuilder(): Builder = Builder()
        .choice(choice)
        .confidence(confidence)
        .probabilities(probabilities)

    public class Builder internal constructor() {
        private var choice: String? = null
        private var confidence: Double? = null
        private var probabilities: Map<String, Double>? = null

        public fun choice(choice: String): Builder = apply { this.choice = choice }

        public fun confidence(confidence: Double): Builder = apply { this.confidence = confidence }

        public fun probabilities(probabilities: Map<String, Double>): Builder = apply { this.probabilities = probabilities.toMap() }

        public fun build(): ChoiceAnswer = ChoiceAnswer(
            choice = checkNotNull(choice) { "choice is required" },
            confidence = checkNotNull(confidence) { "confidence is required" },
            probabilities = checkNotNull(probabilities) { "probabilities is required" },
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}

public class ScoreAnswer private constructor(
    public val score: Double,
    public val confidence: Double,
    legend: Map<String, Entry>,
    probabilities: Map<String, Double>,
) : Answer {
    public val legend: Map<String, Entry> = legend.toMap()

    public val probabilities: Map<String, Double> = probabilities.toMap()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ScoreAnswer) return false
        return score == other.score &&
            confidence == other.confidence &&
            legend == other.legend &&
            probabilities == other.probabilities
    }

    override fun hashCode(): Int {
        var result = score.hashCode()
        result = 31 * result + confidence.hashCode()
        result = 31 * result + legend.hashCode()
        result = 31 * result + probabilities.hashCode()
        return result
    }

    override fun toString(): String = "ScoreAnswer(score=$score, confidence=$confidence, legend=$legend, probabilities=$probabilities)"

    public fun toBuilder(): Builder = Builder()
        .score(score)
        .confidence(confidence)
        .legend(legend)
        .probabilities(probabilities)

    public class Builder internal constructor() {
        private var score: Double? = null
        private var confidence: Double? = null
        private var legend: Map<String, Entry>? = null
        private var probabilities: Map<String, Double>? = null

        public fun score(score: Double): Builder = apply { this.score = score }

        public fun confidence(confidence: Double): Builder = apply { this.confidence = confidence }

        public fun legend(legend: Map<String, Entry>): Builder = apply { this.legend = legend.toMap() }

        public fun probabilities(probabilities: Map<String, Double>): Builder = apply { this.probabilities = probabilities.toMap() }

        public fun build(): ScoreAnswer = ScoreAnswer(
            score = checkNotNull(score) { "score is required" },
            confidence = checkNotNull(confidence) { "confidence is required" },
            legend = checkNotNull(legend) { "legend is required" },
            probabilities = checkNotNull(probabilities) { "probabilities is required" },
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
