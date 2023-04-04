package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.channels.Channel.Factory.UNLIMITED
import kotlinx.coroutines.coroutineScope
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
                val defaultProject = authService.activeParticipantProject()
                try {
                    records
                        .takeWhile { isActive }
                        .mapIndexed { idx, record ->
                            mapRecord(idx, record, authChannel, keyMapping, valueMapping,
                                defaultProject)
                        }
                } finally {
                    authChannel.close()
                }
            }

            authChannel.consume {
                val entitiesChecked = HashSet<EntityDetails>()

                for (entity in this) {
                    // only check entities once
                    if (!entitiesChecked.add(entity)) continue

                    authService.checkPermission(
                        Permission.MEASUREMENT_CREATE,
                        entity,
                        location = "POST $topic",
                    )
                }
            }

            resultJob.await()
        }
    }

    private fun mapRecord(
        idx: Int,
        record: JsonNode,
        authChannel: SendChannel<EntityDetails>,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        valueMapping: AvroProcessor.JsonToObjectMapping,
        defaultProject: String?,
    ): Pair<GenericRecord, GenericRecord> {
        val context = avroParsingContext(Schema.Type.ARRAY, "records[$idx]")
        val key = record["key"]
            ?: throw context.invalidContent("Missing key field in record")
        val value = record["value"]
            ?: throw context.invalidContent("Missing value field in record")
        return Pair(
            processKey(
                key,
                keyMapping,
                context.child(Schema.Type.MAP, "key"),
                authChannel,
                defaultProject,
            ),
            processValue(
                value,
                valueMapping,
                context.child(Schema.Type.MAP, "value"),
            ),
        )
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    fun processKey(
        key: JsonNode,
        keyMapping: AvroProcessor.JsonToObjectMapping,
        context: AvroParsingContext,
        authChannel: SendChannel<EntityDetails>,
        defaultProject: String?,
    ): GenericRecord {
        if (!key.isObject) {
            throw context.invalidContent("Field key must be a JSON object")
        }

        authChannel.trySend(key.toEntityDetails(context, defaultProject))

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
            throw context.invalidContent("Field value must be a JSON object")
        }

        return valueMapping.jsonToAvro(value, context)
    }

    private fun JsonNode.toEntityDetails(
        context: AvroParsingContext,
        defaultProject: String?,
    ): EntityDetails {
        return entityDetails {
            val jsonProject = get("projectId")
            project = when {
                jsonProject == null -> defaultProject
                jsonProject.isNull -> {
                    // no project ID was provided, fill it in for the sender
                    (this@toEntityDetails as ObjectNode).set<JsonNode>(
                        "projectId",
                        objectMapper.createObjectNode().apply {
                            put("string", defaultProject)
                        },
                    )
                    defaultProject
                }
                else -> jsonProject["string"]?.asText() ?: throw context.invalidContent(
                    "Project ID should be wrapped in string union type",
                )
            } ?: throw context.invalidContent("Missing project ID")
            subject = get("userId")?.asText()
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
