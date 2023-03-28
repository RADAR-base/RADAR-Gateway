package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.radarbase.auth.authorization.EntityDetails
import org.radarbase.auth.authorization.Permission
import org.radarbase.auth.authorization.entityDetails
import org.radarbase.jersey.auth.AuthService
import org.radarbase.jersey.exception.HttpInvalidContentException
import java.io.IOException

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
class AvroRecordProcessor(
    private val checkSourceId: Boolean,
    private val authService: AuthService,
    private val objectMapper: ObjectMapper,
) {
    @Throws(IOException::class)
    suspend fun process(
        topic: String,
        records: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        valueMapping: AvroProcessor.JsonToObjectMapping,
    ): List<Pair<GenericRecord, GenericRecord>> {
        if (!records.isArray) {
            throw HttpInvalidContentException("Records should be an array")
        }

        return coroutineScope {
            val authChannel = Channel<EntityDetails>(UNLIMITED)

            val resultJob = async {
                try {
                    val authIds = mutableSetOf<EntityDetails>()
                    records
                        .takeWhile { isActive }
                        .mapIndexed { idx, record ->
                            mapRecord(idx, record, authChannel, keyMapping, valueMapping, authIds)
                        }
                } finally {
                    authChannel.cancel()
                }
            }

            authChannel.consumeEach { entity ->
                // Only process distinct entities
                authService.checkPermission(
                    Permission.MEASUREMENT_CREATE,
                    entity,
                    location = "POST $topic",
                )
            }

            resultJob.await()
        }
    }

    private suspend fun mapRecord(
        idx: Int,
        record: JsonNode,
        authChannel: SendChannel<EntityDetails>,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        valueMapping: AvroProcessor.JsonToObjectMapping,
        authIds: MutableSet<EntityDetails>,
    ): Pair<GenericRecord, GenericRecord> {
        val context = AvroParsingContext(Schema.Type.ARRAY, "records[$idx]")
        val key = record["key"]
            ?: throw invalidContent("Missing key field in record", context)
        val value = record["value"]
            ?: throw invalidContent("Missing value field in record", context)
        return Pair(
            processKey(
                key,
                keyMapping,
                AvroParsingContext(Schema.Type.MAP, "key", context),
                authChannel,
                authIds,
            ),
            processValue(
                value,
                valueMapping,
                AvroParsingContext(Schema.Type.MAP, "value", context),
            ),
        )
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    suspend fun processKey(
        key: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        context: AvroParsingContext,
        authChannel: SendChannel<EntityDetails>,
        authIds: MutableSet<EntityDetails>,
    ): GenericRecord {
        if (!key.isObject) {
            throw invalidContent("Field key must be a JSON object", context)
        }

        val entity = key.toEntityDetails(context)
        if (authIds.add(entity)) {
            authChannel.trySend(entity)
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

    private suspend fun JsonNode.toEntityDetails(
        context: AvroParsingContext,
    ): EntityDetails {
        val defaultProject = authService.activeParticipantProject()
        return entityDetails {
            project = defaultProject
            val jsonProject = get("projectId")
            if (jsonProject != null) {
                if (jsonProject.isNull) {
                    // no project ID was provided, fill it in for the sender
                    (this@toEntityDetails as ObjectNode).set<JsonNode>(
                        "projectId",
                        objectMapper.createObjectNode().apply {
                            put("string", defaultProject)
                        },
                    )
                } else {
                    // project ID was provided, it should match one of the validated project IDs.
                    project = jsonProject["string"]?.asText() ?: throw invalidContent(
                        "Project ID should be wrapped in string union type",
                        context,
                    )
                }
            }
            user = get("userId")?.asText()
            if (checkSourceId) {
                source = get("sourceId")?.asText()
            }
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
