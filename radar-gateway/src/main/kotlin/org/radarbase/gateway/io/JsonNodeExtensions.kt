package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericFixed
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.jsonDefaultValue
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

fun JsonNode.toAvro(to: Schema, context: AvroParsingContext, defaultVal: JsonNode? = null): Any? =
    if (isNull) {
        when {
            to.type == Schema.Type.NULL -> null
            to.type == Schema.Type.UNION -> toAvroUnion(to, context, defaultVal)
            defaultVal == null || defaultVal.isNull -> throw context.invalidContent("No value given to field without default")
            else -> defaultVal.toAvro(
                to,
                context.child(to.type, "default value"),
            )
        }
    } else {
        when (to.type!!) {
            Schema.Type.RECORD -> toAvroObject(to, context)
            Schema.Type.LONG, Schema.Type.FLOAT, Schema.Type.DOUBLE, Schema.Type.INT ->
                toAvroNumber(to.type, context)
            Schema.Type.BOOLEAN -> toAvroBoolean(context)
            Schema.Type.ARRAY -> toAvroArray(to, context)
            Schema.Type.NULL -> null
            Schema.Type.BYTES -> toAvroBytes(context)
            Schema.Type.FIXED -> toAvroFixed(to, context)
            Schema.Type.ENUM -> toAvroEnum(to, context, defaultVal)
            Schema.Type.MAP -> toAvroMap(to.valueType, context)
            Schema.Type.STRING -> toAvroString(context)
            Schema.Type.UNION -> toAvroUnion(to, context, defaultVal)
        }
    }

fun JsonNode.toAvroObject(
    schema: Schema,
    context: AvroParsingContext,
): GenericRecord {
    if (this !is ObjectNode) throw context.invalidContent("Cannot map non-object to object")
    val builder = GenericRecordBuilder(schema)
    for (field in schema.fields) {
        get(field.name())?.let { node ->
            val fieldContext = context.child(Schema.Type.RECORD, "${schema.name}.${field.name()}")
            builder[field] = node.toAvro(field.schema(), fieldContext, field.jsonDefaultValue)
        }
    }
    return builder.build()
}

fun JsonNode.toAvroFixed(
    schema: Schema,
    context: AvroParsingContext,
): GenericFixed {
    if (!isTextual) throw context.invalidContent("Can only convert strings to byte arrays")

    val bytes = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
    if (bytes.size != schema.fixedSize) {
        throw context.invalidContent("Cannot use a different Fixed size")
    }
    return GenericData.Fixed(schema, bytes)
}

fun JsonNode.toAvroNumber(
    schemaType: Schema.Type,
    context: AvroParsingContext,
): Number {
    return when {
        isNumber -> when (schemaType) {
            Schema.Type.LONG -> asLong()
            Schema.Type.FLOAT -> asDouble().toFloat()
            Schema.Type.DOUBLE -> asDouble()
            Schema.Type.INT -> asInt()
            else -> throw context.invalidContent("Non-number type $schemaType used for numbers")
        }
        isTextual -> when (schemaType) {
            Schema.Type.LONG -> asText().toLong()
            Schema.Type.FLOAT -> asText().toFloat()
            Schema.Type.DOUBLE -> asText().toDouble()
            Schema.Type.INT -> asText().toInt()
            else -> throw context.invalidContent("Non-number type $schemaType used for numbers")
        }
        else -> throw context.invalidContent("Cannot map non-number $nodeType to number")
    }
}

fun JsonNode.toAvroBoolean(context: AvroParsingContext): Boolean {
    return when {
        isBoolean -> asBoolean()
        isTextual -> when (val t = asText()!!) {
            "true" -> true
            "false" -> false
            else -> throw context.invalidContent("Cannot map non-boolean string $t to boolean")
        }
        isNumber -> asDouble() != 0.0
        else -> throw context.invalidContent("Cannot map non-boolean to boolean")
    }
}

fun JsonNode.toAvroString(context: AvroParsingContext): String {
    if (isTextual || isNumber || isBoolean) {
        return asText()
    } else {
        throw context.invalidContent("Cannot map non-simple types to string: $this")
    }
}

fun JsonNode?.toAvroUnion(
    to: Schema,
    context: AvroParsingContext,
    defaultVal: JsonNode?,
): Any? = when {
    this == null || this.isNull -> when {
        to.types.any { it.type == Schema.Type.NULL } -> null
        defaultVal != null -> defaultVal.toAvro(to.types.first(), context)
        else -> throw context.invalidContent("Cannot map null value to non-null union")
    }
    this is ObjectNode -> {
        val fieldName = fieldNames().asSequence().firstOrNull()
            ?: throw context.invalidContent("Cannot union without a value")
        val type = to.types
            .firstOrNull { unionType -> fieldName == unionType.name || fieldName == unionType.fullName }
            ?: throw context.invalidContent("Cannot find any matching union types")

        this[fieldName].toAvro(
            type,
            context.child(Schema.Type.UNION, type.name),
        )
    }
    isNumber -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.LONG ||
                unionType.type == Schema.Type.INT ||
                unionType.type == Schema.Type.FLOAT ||
                unionType.type == Schema.Type.DOUBLE
        } ?: throw context.invalidContent("Cannot map number to non-number union")
        toAvroNumber(
            type.type,
            context.child(Schema.Type.UNION, type.name),
        )
    }
    isTextual -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.STRING ||
                unionType.type == Schema.Type.FIXED ||
                unionType.type == Schema.Type.BYTES ||
                unionType.type == Schema.Type.ENUM
        } ?: throw context.invalidContent("Cannot map text to non-textual union")
        toAvro(
            type,
            context.child(Schema.Type.UNION, type.name),
            defaultVal,
        )
    }
    isBoolean -> {
        if (to.types.none { unionType -> unionType.type == Schema.Type.BOOLEAN }) {
            throw context.invalidContent("Cannot map boolean to non-boolean union")
        }
        toAvroBoolean(
            context.child(Schema.Type.UNION, Schema.Type.BOOLEAN.name),
        )
    }
    isArray -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.ARRAY
        } ?: throw context.invalidContent("Cannot map array to non-array union")
        toAvroArray(
            type,
            context.child(Schema.Type.UNION, type.name),
        )
    }
    isObject -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.MAP ||
                unionType.type == Schema.Type.RECORD
        } ?: throw context.invalidContent("Cannot map object to non-object union")
        toAvro(
            type,
            context.child(Schema.Type.UNION, type.name),
            defaultVal,
        )
    }
    else -> throw context.invalidContent("Cannot map unknown JSON node type")
}

fun JsonNode.toAvroEnum(
    schema: Schema,
    context: AvroParsingContext,
    defaultVal: JsonNode?,
): GenericData.EnumSymbol {
    if (!isTextual) throw context.invalidContent("Can only convert strings to enum")

    val symbol = asText()?.toEnumSymbolOrNull(schema)

    return when {
        symbol != null -> symbol
        defaultVal != null && defaultVal.isTextual -> defaultVal.asText()
            .toEnumSymbolOrNull(schema)
            ?: throw context.invalidContent("Enum symbol default cannot be found")
        else -> throw context.invalidContent("Enum symbol without default cannot be found")
    }
}

fun String.toEnumSymbolOrNull(schema: Schema): GenericData.EnumSymbol? {
    return if (schema.hasEnumSymbol(this)) GenericData.EnumSymbol(schema, this) else null
}

fun JsonNode.toAvroMap(
    schema: Schema,
    context: AvroParsingContext,
): Map<String, Any?> {
    if (this !is ObjectNode) throw context.invalidContent("Can only convert objects to map")

    return buildMap {
        fieldNames().forEach { fieldName ->
            val fieldContext = context.child(Schema.Type.MAP, fieldName)
            val field = this@toAvroMap.get(fieldName)
            put(fieldName, field.toAvro(schema, fieldContext))
        }
    }
}

fun JsonNode.toAvroBytes(context: AvroParsingContext): ByteBuffer {
    if (!isTextual) throw context.invalidContent("Can only convert strings to byte arrays")

    val fromArray = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
    return ByteBuffer.wrap(fromArray)
}

fun JsonNode.toAvroArray(
    schema: Schema,
    context: AvroParsingContext,
): Any {
    if (!isArray) throw context.invalidContent("Cannot map non-array to array")
    return GenericData.Array<Any>(
        schema,
        (this as ArrayNode).mapIndexed { idx, value ->
            value.toAvro(schema.elementType, context.child(Schema.Type.ARRAY, idx.toString()))
        },
    )
}
