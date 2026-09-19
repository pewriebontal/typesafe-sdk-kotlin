package net.bontal.typesafesdk

public class Headers private constructor(private val entries: Map<String, List<String>>) {

    public val names: Set<String> get() = entries.keys

    public operator fun get(name: String): String? = entries[name.lowercase()]?.firstOrNull()

    public fun values(name: String): List<String> = entries[name.lowercase()].orEmpty()

    public fun toMap(): Map<String, List<String>> = entries

    override fun equals(other: Any?): Boolean = this === other || (other is Headers && entries == other.entries)

    override fun hashCode(): Int = entries.hashCode()

    override fun toString(): String = "Headers(names=$names)"

    internal companion object {
        fun of(values: Map<String, List<String>>): Headers {
            val merged = LinkedHashMap<String, MutableList<String>>()
            for ((name, list) in values) {
                merged.getOrPut(name.lowercase()) { mutableListOf() }.addAll(list)
            }
            return Headers(merged.mapValues { (_, list) -> list.toList() })
        }
    }
}
