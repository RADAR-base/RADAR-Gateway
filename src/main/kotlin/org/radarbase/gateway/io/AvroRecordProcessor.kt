package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.exception.HttpInvalidContentException
import java.io.IOException

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
        topic: String,
        records: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        valueMapping: AvroProcessor.JsonToObjectMapping,
    ): List<Pair<GenericRecord, GenericRecord>> {
        if (!records.isArray) {
            throw HttpInvalidContentException("Records should be an array")
        }

        return records.mapIndexed { idx, record ->
            val context = AvroParsingContext(Schema.Type.ARRAY, "records[$idx]")
            val key = record["key"]
                ?: throw invalidContent("Missing key field in record", context)
            val value = record["value"]
                ?: throw invalidContent("Missing value field in record", context)
            Pair(
                processKey(topic, key, keyMapping, AvroParsingContext(Schema.Type.MAP, "key", context)),
                processValue(value, valueMapping, AvroParsingContext(Schema.Type.MAP, "value", context)),
            )
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    fun processKey(
        topic: String,
        key: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        context: AvroParsingContext,
    ): GenericRecord {
        if (!key.isObject) {
            throw invalidContent("Field key must be a JSON object", context)
        }

        val authId = key.toAuthId(context)
        if (context.authIds.add(authId)) {
            authId.checkPermission(auth, checkSourceId, topic)
        }

        return keyMapping.jsonToAvro(key, context)
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    fun processValue(
        value: JsonNode,
        valueMapping: AvroProcessor.JsonToObjectMapping,
        context: AvroParsingContext,
    ): GenericRecord {
        if (!value.isObject) {
            throw invalidContent("Field value must be a JSON object", context)
        }

        return valueMapping.jsonToAvro(value, context)
    }

    private fun JsonNode.toAuthId(
        context: AvroParsingContext,
    ): AuthId {
        val projectId = this["projectId"]?.let { project ->
            if (project.isNull) {
                // no project ID was provided, fill it in for the sender
                val newProject = objectMapper.createObjectNode().apply {
                    put("string", auth.defaultProject)
                }
                (this as ObjectNode).set<JsonNode?>("projectId", newProject)
                auth.defaultProject
            } else {
                // project ID was provided, it should match one of the validated project IDs.
                project["string"]?.asText() ?: throw invalidContent(
                    "Project ID should be wrapped in string union type",
                    context,
                )
            }
        } ?: auth.defaultProject

        val userId = this["userId"]?.asText()

        return if (checkSourceId) {
            AuthId(projectId, userId, this["sourceId"]?.asText())
        } else {
            AuthId(projectId, userId, null)
        }
    }

    private fun AvroProcessor.JsonToObjectMapping.jsonToAvro(
        node: JsonNode,
        context: AvroParsingContext,
    ): GenericRecord {
        val originalRecord = node.toAvroObject(sourceSchema, context)
        return mapper.convertAvro(originalRecord) as GenericRecord
    }
}
