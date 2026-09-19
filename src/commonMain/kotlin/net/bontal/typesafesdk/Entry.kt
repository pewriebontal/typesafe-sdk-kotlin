package net.bontal.typesafesdk

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlin.jvm.JvmStatic
import kotlin.jvm.JvmSynthetic

public sealed interface Entry {

    public class Text(public val value: String) : Entry {
        override fun equals(other: Any?): Boolean = this === other || (other is Text && value == other.value)

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Entry.Text(value=$value)"
    }

    public class Object(public val value: JsonObject) : Entry {
        override fun equals(other: Any?): Boolean = this === other || (other is Object && value == other.value)

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Entry.Object(value=$value)"
    }

    public class Array(public val value: JsonArray) : Entry {
        override fun equals(other: Any?): Boolean = this === other || (other is Array && value == other.value)

        override fun hashCode(): Int = value.hashCode()

        override fun toString(): String = "Entry.Array(value=$value)"
    }

    public companion object {
        @JvmStatic
        public fun of(value: String): Entry = Text(value)

        @JvmStatic
        public fun of(value: JsonObject): Entry = Object(value)

        @JvmStatic
        public fun of(value: JsonArray): Entry = Array(value)
    }
}

@JvmSynthetic
public fun Entry(value: String): Entry = Entry.Text(value)

@JvmSynthetic
public fun Entry(value: JsonObject): Entry = Entry.Object(value)

@JvmSynthetic
public fun Entry(value: JsonArray): Entry = Entry.Array(value)
