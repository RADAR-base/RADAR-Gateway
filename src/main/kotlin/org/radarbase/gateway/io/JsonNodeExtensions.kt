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
import org.radarbase.jersey.exception.HttpInvalidContentException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

fun JsonNode.toAvro(to: Schema, context: AvroParsingContext, defaultVal: JsonNode? = null): Any? {
    return if (isNull) {
        when {
            to.type == Schema.Type.NULL -> null
            to.type == Schema.Type.UNION -> toAvroUnion(to, context, defaultVal)
            defaultVal == null || defaultVal.isNull -> throw invalidContent("No value given to field without default", context)
            else -> defaultVal.toAvro(
                to,
                AvroParsingContext(to.type, "default value", context),
            )
        }
    } else {
        when (to.type!!) {
            Schema.Type.RECORD -> toAvroObject(to, context)
            Schema.Type.LONG, Schema.Type.FLOAT, Schema.Type.DOUBLE, Schema.Type.INT -> toAvroNumber(
                to.type,
                context,
            )
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
}

fun JsonNode.toAvroObject(
    schema: Schema,
    context: AvroParsingContext,
): GenericRecord {
    this as? ObjectNode ?: throw invalidContent("Cannot map non-object to object", context)
    val builder = GenericRecordBuilder(schema)
    for (field in schema.fields) {
        get(field.name())?.let { node ->
            val fieldContext = AvroParsingContext(
                type = Schema.Type.RECORD,
                name = "${schema.name}.${field.name()}",
                parent = context,
            )
            builder[field] = node.toAvro(field.schema(), fieldContext, field.jsonDefaultValue)
        }
    }
    return builder.build()
}

fun JsonNode.toAvroFixed(
    schema: Schema,
    context: AvroParsingContext,
): GenericFixed {
    if (!isTextual) throw invalidContent("Can only convert strings to byte arrays", context)

    val bytes = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
    if (bytes.size != schema.fixedSize) {
        throw invalidContent("Cannot use a different Fixed size", context)
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
            else -> throw invalidContent("Non-number type $schemaType used for numbers", context)
        }
        isTextual -> when (schemaType) {
            Schema.Type.LONG -> asText().toLong()
            Schema.Type.FLOAT -> asText().toFloat()
            Schema.Type.DOUBLE -> asText().toDouble()
            Schema.Type.INT -> asText().toInt()
            else -> throw invalidContent("Non-number type $schemaType used for numbers", context)
        }
        else -> throw invalidContent("Cannot map non-number $nodeType to number", context)
    }
}

fun JsonNode.toAvroBoolean(context: AvroParsingContext): Boolean {
    return when {
        isBoolean -> asBoolean()
        isTextual -> when (val t = asText()!!) {
            "true" -> true
            "false" -> false
            else -> throw invalidContent("Cannot map non-boolean string $t to boolean", context)
        }
        isNumber -> asDouble() != 0.0
        else -> throw invalidContent("Cannot map non-boolean to boolean", context)
    }
}

fun invalidContent(message: String, context: AvroParsingContext): HttpInvalidContentException {
    throw HttpInvalidContentException("$message (context $context)")
}

fun JsonNode.toAvroString(context: AvroParsingContext): String {
    if (isTextual || isNumber || isBoolean) {
        return asText()
    } else {
        throw invalidContent("Cannot map non-simple types to string: $this", context)
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
        else -> throw invalidContent("Cannot map null value to non-null union", context)
    }
    this is ObjectNode -> {
        val fieldName = fieldNames().asSequence().firstOrNull()
            ?: throw invalidContent("Cannot union without a value", context)
        val type = to.types
            .firstOrNull { unionType -> fieldName == unionType.name || fieldName == unionType.fullName }
            ?: throw invalidContent("Cannot find any matching union types", context)

        this[fieldName].toAvro(
            type,
            AvroParsingContext(Schema.Type.UNION, type.name, context),
        )
    }
    isNumber -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.LONG ||
                unionType.type == Schema.Type.INT ||
                unionType.type == Schema.Type.FLOAT ||
                unionType.type == Schema.Type.DOUBLE
        } ?: throw invalidContent("Cannot map number to non-number union", context)
        toAvroNumber(
            type.type,
            AvroParsingContext(Schema.Type.UNION, type.name, context),
        )
    }
    isTextual -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.STRING ||
                unionType.type == Schema.Type.FIXED ||
                unionType.type == Schema.Type.BYTES ||
                unionType.type == Schema.Type.ENUM
        } ?: throw invalidContent("Cannot map text to non-textual union", context)
        toAvro(
            type,
            AvroParsingContext(Schema.Type.UNION, type.name, context),
            defaultVal,
        )
    }
    isBoolean -> {
        if (to.types.none { unionType -> unionType.type == Schema.Type.BOOLEAN }) {
            throw invalidContent("Cannot map boolean to non-boolean union", context)
        }
        toAvroBoolean(
            AvroParsingContext(Schema.Type.UNION, Schema.Type.BOOLEAN.name, context),
        )
    }
    isArray -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.ARRAY
        } ?: throw invalidContent("Cannot map array to non-array union", context)
        toAvroArray(
            type,
            AvroParsingContext(Schema.Type.UNION, type.name, context),
        )
    }
    isObject -> {
        val type = to.types.firstOrNull { unionType ->
            unionType.type == Schema.Type.MAP ||
                unionType.type == Schema.Type.RECORD
        } ?: throw invalidContent("Cannot map object to non-object union", context)
        toAvro(
            type,
            AvroParsingContext(Schema.Type.UNION, type.name, context),
            defaultVal,
        )
    }
    else -> throw invalidContent("Cannot map unknown JSON node type", context)
}

fun JsonNode.toAvroEnum(
    schema: Schema,
    context: AvroParsingContext,
    defaultVal: JsonNode?,
): GenericData.EnumSymbol {
    if (!isTextual) throw invalidContent("Can only convert strings to enum", context)

    val symbol = asText()?.toEnumSymbolOrNull(schema)

    return when {
        symbol != null -> symbol
        defaultVal != null && defaultVal.isTextual -> defaultVal.asText()
            .toEnumSymbolOrNull(schema)
            ?: throw invalidContent("Enum symbol default cannot be found", context)
        else -> throw invalidContent("Enum symbol without default cannot be found", context)
    }
}

fun String.toEnumSymbolOrNull(schema: Schema): GenericData.EnumSymbol? {
    return if (schema.hasEnumSymbol(this)) GenericData.EnumSymbol(schema, this) else null
}

fun JsonNode.toAvroMap(
    schema: Schema,
    context: AvroParsingContext,
): Map<String, Any?> {
    if (this !is ObjectNode) throw invalidContent("Can only convert objects to map", context)

    return buildMap {
        fieldNames().forEach { fieldName ->
            val fieldContext = AvroParsingContext(Schema.Type.MAP, fieldName, context)
            val field = this@toAvroMap.get(fieldName)
            put(fieldName, field.toAvro(schema, fieldContext))
        }
    }
}

fun JsonNode.toAvroBytes(context: AvroParsingContext): ByteBuffer {
    if (!isTextual) throw invalidContent("Can only convert strings to byte arrays", context)

    val fromArray = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
    return ByteBuffer.wrap(fromArray)
}

fun JsonNode.toAvroArray(
    schema: Schema,
    context: AvroParsingContext,
): Any {
    if (!isArray) throw invalidContent("Cannot map non-array to array", context)
    return GenericData.Array<Any>(
        schema,
        (this as ArrayNode).mapIndexed { idx, value ->
            val elementContext = AvroParsingContext(Schema.Type.ARRAY, idx.toString(), context)
            value.toAvro(schema.elementType, elementContext)
        },
    )
}
