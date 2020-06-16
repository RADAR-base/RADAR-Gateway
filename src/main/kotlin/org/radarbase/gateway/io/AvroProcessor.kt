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
import org.radarbase.gateway.Config
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.gateway.util.CachedValue
import org.radarbase.gateway.util.Json
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpBadGatewayException
import org.radarbase.jersey.exception.HttpInvalidContentException
import org.radarbase.producer.rest.AvroDataMapper
import org.radarbase.producer.rest.AvroDataMapperFactory
import org.radarbase.producer.rest.RestException
import org.radarbase.producer.rest.SchemaRetriever
import org.radarcns.auth.authorization.Permission
import java.io.Closeable
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.text.ParseException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import javax.ws.rs.core.Context

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
class AvroProcessor(
        @Context private val config: Config,
        @Context private val auth: Auth,
        @Context private val schemaRetriever: SchemaRetriever,
        @Context schedulingService: SchedulingService
): Closeable {
    private val idMapping: ConcurrentMap<Pair<String, Int>, CachedValue<JsonToObjectMapping>> = ConcurrentHashMap()
    private val schemaMapping: ConcurrentMap<Pair<String, String>, CachedValue<JsonToObjectMapping>> = ConcurrentHashMap()
    private val cleanReference: SchedulingService.RepeatReference

    init {
        cleanReference = schedulingService.repeat(SCHEMA_CLEAN, SCHEMA_CLEAN) {
            idMapping.values.removeIf { it.isStale }
            schemaMapping.values.removeIf { it.isStale }
        }
    }

    /**
     * Validates given data with given access token and returns a modified output array.
     * The Avro content validation consists of testing whether both keys and values are being sent,
     * both with Avro schema. The authentication validation checks that all records contain a key
     * with a project ID, user ID and source ID that is also listed in the access token. If no
     * project ID is given in the key, it will be set to the first project ID where the user has
     * role {@code ROLE_PARTICIPANT}.
     *
     * @throws ParseException if the data does not contain valid JSON
     * @throws HttpInvalidContentException if the data does not contain semantically correct Kafka Avro data.
     * @throws IOException if the data cannot be read
     */
    fun process(topic: String, root: JsonNode): AvroProcessingResult {
        if (!root.isObject) {
            throw HttpInvalidContentException("Expecting JSON object in payload")
        }
        if (root["key_schema_id"].isMissing && root["key_schema"].isMissing) {
            throw HttpInvalidContentException("Missing key schema")
        }
        if (root["value_schema_id"].isMissing && root["value_schema"].isMissing) {
            throw HttpInvalidContentException("Missing value schema")
        }

        val keyMapping = schemaMapping(topic, false, root["key_schema_id"], root["key_schema"])
        val valueMapping = schemaMapping(topic, true, root["value_schema_id"], root["value_schema"])

        val records = root["records"] ?: throw HttpInvalidContentException("Missing records")
        return AvroProcessingResult(
                keyMapping.targetSchemaId,
                valueMapping.targetSchemaId,
                processRecords(records, keyMapping, valueMapping))
    }


    @Throws(IOException::class)
    private fun processRecords(records: JsonNode, keyMapping: JsonToObjectMapping, valueMapping: JsonToObjectMapping): List<Pair<GenericRecord, GenericRecord>> {
        if (!records.isArray) {
            throw HttpInvalidContentException("Records should be an array")
        }

        return records.map { record ->
            val key = record["key"] ?: throw HttpInvalidContentException("Missing key field in record")
            val value = record["value"] ?: throw HttpInvalidContentException("Missing value field in record")
            Pair(processKey(key, keyMapping), processValue(value, valueMapping))
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    private fun processKey(key: JsonNode, keyMapping: JsonToObjectMapping): GenericRecord {
        if (!key.isObject) {
            throw HttpInvalidContentException("Field key must be a JSON object")
        }

        val projectId = key["projectId"]?.let { project ->
            if (project.isNull) {
                // no project ID was provided, fill it in for the sender
                val newProject = Json.mapper.createObjectNode()
                newProject.put("string", auth.defaultProject)
                (key as ObjectNode).set("projectId", newProject) as JsonNode?
                auth.defaultProject
            } else {
                // project ID was provided, it should match one of the validated project IDs.
                project["string"]?.asText() ?: throw HttpInvalidContentException(
                        "Project ID should be wrapped in string union type")
            }
        } ?: auth.defaultProject

        auth.checkPermissionOnSource(Permission.MEASUREMENT_CREATE,
                projectId, key["userId"]?.asText(), key["sourceId"]?.asText())

        return keyMapping.jsonToAvro(key)
    }


    /** Parse single record key.  */
    @Throws(IOException::class)
    private fun processValue(value: JsonNode, valueMapping: JsonToObjectMapping): GenericRecord {
        if (!value.isObject) {
            throw HttpInvalidContentException("Field value must be a JSON object")
        }

        return valueMapping.jsonToAvro(value)
    }

    private fun createMapping(topic: String, ofValue: Boolean, sourceSchema: Schema): JsonToObjectMapping {
        val latestSchema = schemaRetriever.getSchemaMetadata(topic, ofValue, -1)
        val schemaMapper = AvroDataMapperFactory.get().createMapper(sourceSchema, latestSchema.schema, null)
        return JsonToObjectMapping(sourceSchema, latestSchema.schema, latestSchema.id, schemaMapper)
    }

    private fun schemaMapping(topic: String, ofValue: Boolean, id: JsonNode?, schema: JsonNode?): JsonToObjectMapping {
        val subject = "$topic-${if (ofValue) "value" else "key"}"
        return when {
            id?.isNumber == true -> {
                idMapping.computeIfAbsent(Pair(subject, id.asInt())) {
                    CachedValue(SCHEMA_REFRESH, SCHEMA_RETRY) {
                        val parsedSchema = try {
                            schemaRetriever.getBySubjectAndId(topic, ofValue, id.asInt())
                        } catch (ex: RestException) {
                            if (ex.statusCode == 404) {
                                throw HttpApplicationException(422, "schema_not_found", "Schema ID not found in subject")
                            } else {
                                throw HttpBadGatewayException("cannot get data from schema registry: ${ex.javaClass.simpleName}")
                            }
                        }
                        createMapping(topic, ofValue, parsedSchema.schema)
                    }
                }.retrieve()
            }
            schema?.isTextual == true -> {
                schemaMapping.computeIfAbsent(Pair(subject, schema.asText())) {
                    CachedValue(SCHEMA_REFRESH, SCHEMA_RETRY) {
                        try {
                            val parsedSchema = Schema.Parser().parse(schema.textValue())
                            createMapping(topic, ofValue, parsedSchema)
                        } catch (ex: Exception) {
                            throw throw HttpApplicationException(422, "schema_not_found", "Schema ID not found in subject")
                        }
                    }
                }.retrieve()
            }
            else -> throw HttpInvalidContentException("No schema provided")
        }
    }

    private fun JsonNode.toAvro(to: Schema, defaultVal: JsonNode? = null): Any? {
        val useNode = if (isNull) {
            if (to.type == Schema.Type.NULL) return null
            if (defaultVal.isMissing) throw HttpInvalidContentException("No value given to field without default.")
            return defaultVal!!.toAvro(to)
        } else this

        return when (to.type!!) {
            Schema.Type.RECORD -> useNode.toAvroObject(to)
            Schema.Type.LONG, Schema.Type.FLOAT, Schema.Type.DOUBLE, Schema.Type.INT -> useNode.toAvroNumber(to.type)
            Schema.Type.BOOLEAN -> useNode.toAvroBoolean()
            Schema.Type.ARRAY -> useNode.toAvroArray(to.elementType)
            Schema.Type.NULL -> null
            Schema.Type.BYTES -> useNode.toAvroBytes()
            Schema.Type.FIXED -> useNode.toAvroFixed(to)
            Schema.Type.ENUM -> useNode.toAvroEnum(to, defaultVal)
            Schema.Type.MAP -> useNode.toAvroMap(to.valueType)
            Schema.Type.STRING -> useNode.toAvroString()
            Schema.Type.UNION -> useNode.toAvroUnion(to, defaultVal)
        }
    }

    private fun JsonNode.toAvroString(): String {
        return if (isTextual || isNumber || isBoolean) asText()
        else throw HttpInvalidContentException("Cannot map non-simple types to string: $this")
    }

    private fun JsonNode.toAvroUnion(to: Schema, defaultVal: JsonNode?): Any? {
        return when {
            this is ObjectNode -> {
                val fieldName = fieldNames().asSequence().firstOrNull()
                        ?: throw HttpInvalidContentException("Cannot union without a value")
                val type = to.types.firstOrNull { unionType ->
                    fieldName == unionType.name || unionType.fullName == fieldName
                } ?: throw HttpInvalidContentException("Cannot find any matching union types")

                this[fieldName].toAvro(type)
            }
            isNumber -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.LONG
                            || unionType.type == Schema.Type.INT
                            || unionType.type == Schema.Type.FLOAT
                            || unionType.type == Schema.Type.DOUBLE
                } ?: throw HttpInvalidContentException("Cannot map number to non-number union")
                toAvroNumber(type.type)
            }
            isTextual -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.STRING
                            || unionType.type == Schema.Type.FIXED
                            || unionType.type == Schema.Type.BYTES
                            || unionType.type == Schema.Type.ENUM
                } ?: throw HttpInvalidContentException("Cannot map number to non-number union")
                toAvro(type)
            }
            isBoolean -> {
                if (to.types.none { unionType -> unionType.type == Schema.Type.BOOLEAN }) {
                    throw HttpInvalidContentException("Cannot map boolean to non-boolean union")
                }
                toAvroBoolean()
            }
            isArray -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.ARRAY
                } ?: throw HttpInvalidContentException("Cannot map array to non-array union")
                return toAvroArray(type.elementType)
            }
            isObject -> {
                val type = to.types.firstOrNull { unionType ->
                    unionType.type == Schema.Type.MAP
                            || unionType.type == Schema.Type.RECORD
                } ?: throw HttpInvalidContentException("Cannot map object to non-object union")
                return toAvro(type, defaultVal)
            }
            else -> throw HttpInvalidContentException("Cannot map unknown JSON node type")
        }
    }

    private fun JsonNode.toAvroEnum(schema: Schema, defaultVal: JsonNode?): GenericData.EnumSymbol {
        return if (isTextual) {
            val textValue = asText()!!
            if (schema.hasEnumSymbol(textValue)) {
                GenericData.EnumSymbol(schema, textValue)
            } else if (defaultVal != null && defaultVal.isTextual) {
                val defaultText = defaultVal.asText()
                if (schema.hasEnumSymbol(defaultText)) {
                    GenericData.EnumSymbol(schema, defaultText)
                } else throw HttpInvalidContentException("Enum symbol default cannot be found")
            } else throw HttpInvalidContentException("Enum symbol without default cannot be found")
        } else throw HttpInvalidContentException("Can only convert strings to enum")
    }

    private fun JsonNode.toAvroMap(schema: Schema): Map<String, Any?> {
        return if (this is ObjectNode) {
            fieldNames().asSequence()
                    .associateWithTo(LinkedHashMap()) { key -> get(key).toAvro(schema) }
        } else throw HttpInvalidContentException("Can only convert objects to map")
    }

    private fun JsonNode.toAvroBytes(): ByteBuffer {
        return if (isTextual) {
            val fromArray = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
            ByteBuffer.wrap(fromArray)
        } else throw HttpInvalidContentException("Can only convert strings to byte arrays")
    }

    private fun JsonNode.toAvroFixed(schema: Schema): GenericFixed {
        return if (isTextual) {
            val bytes = textValue()!!.toByteArray(StandardCharsets.ISO_8859_1)
            if (bytes.size != schema.fixedSize) {
                throw HttpInvalidContentException("Cannot use a different Fixed size")
            }
            GenericData.Fixed(schema, bytes)
        } else throw HttpInvalidContentException("Can only convert strings to byte arrays")
    }

    private fun JsonNode.toAvroObject(schema: Schema): GenericRecord {
        this as? ObjectNode ?: throw HttpInvalidContentException("Cannot map non-object to object")
        val builder = GenericRecordBuilder(schema)
        for (field in schema.fields) {
            get(field.name())?.let { node ->
                builder[field] = node.toAvro(field.schema(), field.jsonDefaultValue)
            }
        }
        return builder.build()
    }

    private fun JsonNode.toAvroArray(schema: Schema): Any {
        return when {
            isArray -> GenericData.Array<Any>(schema, (this as ArrayNode).toList())
            else -> throw HttpInvalidContentException("Cannot map non-array to array")
        }
    }

    private fun JsonNode.toAvroNumber(schemaType: Schema.Type): Number {
        return when {
            isNumber -> when (schemaType) {
                Schema.Type.LONG -> asLong()
                Schema.Type.FLOAT -> asDouble().toFloat()
                Schema.Type.DOUBLE -> asDouble()
                Schema.Type.INT -> asInt()
                else -> throw HttpInvalidContentException("Non-number type used for numbers")
            }
            isTextual -> when (schemaType) {
                Schema.Type.LONG -> asText().toLong()
                Schema.Type.FLOAT -> asText().toFloat()
                Schema.Type.DOUBLE -> asText().toDouble()
                Schema.Type.INT -> asText().toInt()
                else -> throw HttpInvalidContentException("Non-number type used for numbers")
            }
            else -> throw HttpInvalidContentException("Cannot map non-number to number")
        }
    }

    private fun JsonNode.toAvroBoolean(): Boolean {
        return when {
            isBoolean -> asBoolean()
            isTextual -> when (asText()!!) {
                "true" -> true
                "false" -> false
                else -> throw HttpInvalidContentException("Cannot map non-boolean string to boolean")
            }
            isNumber -> asDouble() != 0.0
            else -> throw HttpInvalidContentException("Cannot map non-boolean to boolean")
        }
    }

    override fun close() {
        cleanReference.close()
    }

    companion object {
        private val SCHEMA_REFRESH = Duration.ofHours(1)
        private val SCHEMA_RETRY = Duration.ofMinutes(2)
        private val SCHEMA_CLEAN = Duration.ofHours(2)

        val JsonNode?.isMissing: Boolean
            get() = this == null || this.isNull
    }


    data class JsonToObjectMapping(
            val sourceSchema: Schema,
            val targetSchema: Schema,
            val targetSchemaId: Int,
            val mapper: AvroDataMapper)

    private fun JsonToObjectMapping.jsonToAvro(node: JsonNode): GenericRecord {
        val originalRecord = node.toAvroObject(sourceSchema)
        return mapper.convertAvro(originalRecord) as GenericRecord
    }
}
