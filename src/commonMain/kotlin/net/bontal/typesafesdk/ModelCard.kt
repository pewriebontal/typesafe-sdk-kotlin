package net.bontal.typesafesdk

import kotlin.jvm.JvmStatic

public class ModelCard private constructor(
    public val name: String,
    public val description: String,
    public val releaseDate: String,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ModelCard) return false
        return name == other.name && description == other.description && releaseDate == other.releaseDate
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + description.hashCode()
        result = 31 * result + releaseDate.hashCode()
        return result
    }

    override fun toString(): String = "ModelCard(name=$name, description=$description, releaseDate=$releaseDate)"

    public fun toBuilder(): Builder = Builder()
        .name(name)
        .description(description)
        .releaseDate(releaseDate)

    public class Builder internal constructor() {
        private var name: String? = null
        private var description: String? = null
        private var releaseDate: String? = null

        public fun name(name: String): Builder = apply { this.name = name }

        public fun description(description: String): Builder = apply { this.description = description }

        public fun releaseDate(releaseDate: String): Builder = apply { this.releaseDate = releaseDate }

        public fun build(): ModelCard = ModelCard(
            name = checkNotNull(name) { "name is required" },
            description = checkNotNull(description) { "description is required" },
            releaseDate = checkNotNull(releaseDate) { "releaseDate is required" },
        )
    }

    public companion object {
        @JvmStatic
        public fun builder(): Builder = Builder()
    }
}
