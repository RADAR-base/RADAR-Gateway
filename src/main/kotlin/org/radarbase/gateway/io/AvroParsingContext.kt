package org.radarbase.gateway.io

import org.apache.avro.Schema

data class AvroParsingContext(
    val type: Schema.Type,
    val name: String?,
    val parent: AvroParsingContext? = null,
    val authIds: MutableSet<AuthId> = mutableSetOf(),
) {
    private fun toString(child: String?): String {
        val typedName = if (child != null) "$type { $name: $child }" else "$type { $name }"
        return parent?.toString(typedName) ?: typedName
    }
    override fun toString(): String = toString(null)
}
