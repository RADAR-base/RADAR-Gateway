package org.radarbase.gateway.io

import org.apache.avro.Schema
import org.radarbase.jersey.exception.HttpInvalidContentException

fun avroParsingContext(type: Schema.Type, name: String): AvroParsingContext =
    RootAvroParsingContext(type, name)

sealed class AvroParsingContext(
    val type: Schema.Type,
    val name: String,
) {
    internal open fun toString(child: String?): String =
        if (child != null) "$type { $name: $child }" else "$type { $name }"

    override fun toString(): String = toString(null)

    fun child(type: Schema.Type, name: String): AvroParsingContext =
        ChildAvroParsingContext(type, name, this)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as AvroParsingContext

        return type == other.type &&
            name == other.name
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + name.hashCode()
        return result
    }

    fun invalidContent(message: String): HttpInvalidContentException {
        return HttpInvalidContentException("$message (context $this)")
    }
}

private class RootAvroParsingContext(
    type: Schema.Type,
    name: String,
) : AvroParsingContext(type, name)

private class ChildAvroParsingContext(
    type: Schema.Type,
    name: String,
    val parent: AvroParsingContext,
) : AvroParsingContext(type, name) {
    override fun toString(child: String?): String = parent.toString(super.toString(child))

    override fun equals(other: Any?): Boolean = super.equals(other) &&
        parent == (other as ChildAvroParsingContext).parent

    override fun hashCode(): Int = 31 * super.hashCode() + parent.hashCode()
}
