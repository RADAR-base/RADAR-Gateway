package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericFixed
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.jsonDefaultValue
import org.radarbase.auth.authorization.Permission
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.exception.HttpInvalidContentException
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
class AvroRecordProcessor(
    private val checkSourceId: Boolean,
    private val auth: Auth,
    private val objectMapper: ObjectMapper,
) {

    @Throws(IOException::class)
    fun process(
        records: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        valueMapping: AvroProcessor.JsonToObjectMapping,
    ): List<Pair<GenericRecord, GenericRecord>> {
        if (!records.isArray) {
            throw HttpInvalidContentException("Records should be an array")
        }

        return records.mapIndexed { idx, record ->
            val context = ParsingContext(Schema.Type.ARRAY, "records[$idx]")
            val key = record["key"]
                ?: throw invalidContent("Missing key field in record", context)
            val value = record["value"]
                ?: throw invalidContent("Missing value field in record", context)
            Pair(
                processKey(key, keyMapping, ParsingContext(Schema.Type.MAP, "key", context)),
                processValue(value, valueMapping, ParsingContext(Schema.Type.MAP, "value", context)),
            )
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    fun processKey(
        key: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        context: ParsingContext,
    ): GenericRecord {
        if (!key.isObject) {
            throw invalidContent("Field key must be a JSON object", context)
        }

        val projectId = key["projectId"]?.let { project ->
            if (project.isNull) {
                // no project ID was provided, fill it in for the sender
                val newProject = objectMapper.createObjectNode()
                newProject.put("string", auth.defaultProject)
                (key as ObjectNode).set("projectId", newProject) as JsonNode?
                auth.defaultProject
            } else {
                // project ID was provided, it should match one of the validated project IDs.
                project["string"]?.asText() ?: throw invalidContent(
                    "Project ID should be wrapped in string union type", context
                )
            }
        } ?: auth.defaultProject

        if (checkSourceId) {
            auth.checkPermissionOnSource(
                Permission.MEASUREMENT_CREATE,
                projectId, key["userId"]?.asText(), key["sourceId"]?.asText()
            )
        } else {
            auth.checkPermissionOnSubject(
                Permission.MEASUREMENT_CREATE,
                projectId, key["userId"]?.asText()
            )
        }

        return keyMapping.jsonToAvro(key, context)
    }


    /** Parse single record key.  */
    @Throws(IOException::class)
    fun processValue(
        value: JsonNode,
        valueMapping: AvroProcessor.JsonToObjectMapping,
        context: ParsingContext,
    ): GenericRecord {
        if (!value.isObject) {
            throw invalidContent("Field value must be a JSON object", context)
        }

        return valueMapping.jsonToAvro(value, context)
    }

    private fun JsonNode.toAvro(to: Schema, context: ParsingContext, defaultVal: JsonNode? = null): Any? {
        return if (isNull) {
            when {
                to.type == Schema.Type.NULL -> null
                to.type == Schema.Type.UNION -> toAvroUnion(to, context, defaultVal)
                defaultVal.isMissing -> throw invalidContent("No value given to field without default", context)
                else -> return defaultVal!!.toAvro(to, ParsingContext(to.type, "default value", context))
            }
        } else {
            when (to.type!!) {
                Schema.Type.RECORD -> toAvroObject(to, context)
                Schema.Type.LONG, Schema.Type.FLOAT, Schema.Type.DOUBLE, Schema.Type.INT -> toAvroNumber(
                    to.type,
                    context)
                Schema.Type.BOOLEAN -> toAvroBoolean(context)
                Schema.Type.ARRAY -> toAvroArray(to.elementType, context)
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

    private fun JsonNode.toAvroString(context: ParsingContext): String {
        return if (isTextual || isNumber || isBoolean) asText()
        else throw invalidContent("Cannot map non-simple types to string: $this", context)
    }

    private fun JsonNode?.toAvroUnion(
        to: Schema,
        context: ParsingContext,
        defaultVal: JsonNode?,
    ): Any? {
        return when {
            this == null || this.isNull -> {
                when {
                    to.types.any { it.type == Schema.Type.NULL} -> null
                    defaultVal != null -> defaultVal.toAvro(to.types.first(), context)
                    else -> throw invalidContent("Cannot map null value to non-null union", context)
                }
            }
            this is ObjectNode -> {
                val fieldName = fieldNames().asSequence().firstOrNull()
                    ?: throw invalidContent("Cannot union without a value", context)
                val type = to.types
                    .firstOrNull { unionType -> fieldName == unionType.name || fieldName == unionType.fullName }
                    ?: throw invalidContent("Cannot find any matching union types", context)

                this[fieldName].toAvro(type, ParsingContext(Schema.Type.UNION, type.name, context))
            }
            isNumber -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.LONG
                        || unionType.type == Schema.Type.INT
                        || unionType.type == Schema.Type.FLOAT
                        || unionType.type == Schema.Type.DOUBLE
                } ?: throw invalidContent("Cannot map number to non-number union", context)
                toAvroNumber(type.type, ParsingContext(Schema.Type.UNION, type.name, context))
            }
            isTextual -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.STRING
                        || unionType.type == Schema.Type.FIXED
                        || unionType.type == Schema.Type.BYTES
                        || unionType.type == Schema.Type.ENUM
                } ?: throw invalidContent("Cannot map text to non-textual union", context)
                toAvro(type, ParsingContext(Schema.Type.UNION, type.name, context), defaultVal)
            }
            isBoolean -> {
                if (to.types.none { unionType -> unionType.type == Schema.Type.BOOLEAN }) {
                    throw invalidContent("Cannot map boolean to non-boolean union", context)
                }
                toAvroBoolean(ParsingContext(Schema.Type.UNION, Schema.Type.BOOLEAN.name, context))
            }
            isArray -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.ARRAY
                } ?: throw invalidContent("Cannot map array to non-array union", context)
                return toAvroArray(type.elementType, ParsingContext(Schema.Type.UNION, type.name, context))
            }
            isObject -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.MAP
                        || unionType.type == Schema.Type.RECORD
                } ?: throw invalidContent("Cannot map object to non-object union", context)
                return toAvro(type, ParsingContext(Schema.Type.UNION, type.name, context), defaultVal)
            }
            else -> throw invalidContent("Cannot map unknown JSON node type", context)
        }
    }

    private fun JsonNode.toAvroEnum(
        schema: Schema,
        context: ParsingContext,
        defaultVal: JsonNode?
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

    private fun String.toEnumSymbolOrNull(schema: Schema): GenericData.EnumSymbol? {
        return if (schema.hasEnumSymbol(this)) GenericData.EnumSymbol(schema, this) else null
    }

    private fun JsonNode.toAvroMap(
        schema: Schema,
        context: ParsingContext,
    ): Map<String, Any?> {
        if (this !is ObjectNode) throw invalidContent("Can only convert objects to map", context)

        return fieldNames().asSequence()
            .associateWith { key -> get(key).toAvro(schema, ParsingContext(Schema.Type.MAP, key, context)) }
    }

    private fun JsonNode.toAvroBytes(context: ParsingContext): ByteBuffer {
        return if (isTextual) {
            val fromArray = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
            ByteBuffer.wrap(fromArray)
        } else throw invalidContent("Can only convert strings to byte arrays", context)
    }

    private fun JsonNode.toAvroFixed(
        schema: Schema,
        context: ParsingContext,
    ): GenericFixed {
        return if (isTextual) {
            val bytes = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
            if (bytes.size != schema.fixedSize) {
                throw invalidContent("Cannot use a different Fixed size", context)
            }
            GenericData.Fixed(schema, bytes)
        } else throw invalidContent("Can only convert strings to byte arrays", context)
    }

    private fun JsonNode.toAvroObject(
        schema: Schema,
        context: ParsingContext,
    ): GenericRecord {
        this as? ObjectNode ?: throw invalidContent("Cannot map non-object to object", context)
        val builder = GenericRecordBuilder(schema)
        for (field in schema.fields) {
            get(field.name())?.let { node ->
                val fieldContext = ParsingContext(Schema.Type.RECORD, "${schema.name}.${field.name()}", context)
                builder[field] = node.toAvro(field.schema(), fieldContext, field.jsonDefaultValue)
            }
        }
        return builder.build()
    }

    private fun JsonNode.toAvroArray(
        schema: Schema,
        context: ParsingContext,
    ): Any {
        if (!isArray) throw invalidContent("Cannot map non-array to array", context)
        return GenericData.Array<Any>(schema, (this as ArrayNode).toList())
    }

    private fun JsonNode.toAvroNumber(
        schemaType: Schema.Type,
        context: ParsingContext,
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

    private fun JsonNode.toAvroBoolean(context: ParsingContext): Boolean {
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

    private fun invalidContent(message: String, context: ParsingContext): HttpInvalidContentException {
        throw HttpInvalidContentException("$message (context $context)")
    }

    companion object {
        val JsonNode?.isMissing: Boolean
            get() = this == null || this.isNull
    }

    data class ParsingContext(
        val type: Schema.Type,
        val name: String?,
        val parent: ParsingContext? = null,
    ) {
        private fun toString(child: String?): String {
            val typedName = if (child != null) "$type { $name: $child }" else "$type { $name }"
            return parent?.toString(typedName) ?: typedName
        }
        override fun toString(): String = toString(null)
    }

    private fun AvroProcessor.JsonToObjectMapping.jsonToAvro(
        node: JsonNode,
        context: ParsingContext,
    ): GenericRecord {
        val originalRecord = node.toAvroObject(sourceSchema, context)
        return mapper.convertAvro(originalRecord) as GenericRecord
    }
}
