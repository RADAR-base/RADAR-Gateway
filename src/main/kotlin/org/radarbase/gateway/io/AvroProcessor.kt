package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.apache.avro.Schema
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.jersey.auth.AuthService
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpBadGatewayException
import org.radarbase.jersey.exception.HttpInvalidContentException
import org.radarbase.kotlin.coroutines.CacheConfig
import org.radarbase.kotlin.coroutines.CachedValue
import org.radarbase.producer.rest.AvroDataMapper
import org.radarbase.producer.rest.AvroDataMapperFactory
import org.radarbase.producer.rest.RestException
import org.radarbase.producer.rest.SchemaRetriever
import java.io.Closeable
import java.io.IOException
import java.text.ParseException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
class AvroProcessor(
    config: GatewayConfig,
    authService: AuthService,
    private val schemaRetriever: SchemaRetriever,
    objectMapper: ObjectMapper,
    schedulingService: SchedulingService,
) : Closeable {
    private val idMapping: ConcurrentMap<Pair<String, Int>, CachedValue<JsonToObjectMapping>> = ConcurrentHashMap()
    private val schemaMapping: ConcurrentMap<Pair<String, String>, CachedValue<JsonToObjectMapping>> =
        ConcurrentHashMap()
    private val processor: AvroRecordProcessor = AvroRecordProcessor(
        config.auth.checkSourceId,
        authService,
        objectMapper,
    )

    // Clean stale caches regularly. This reduces memory use for caches that aren't being used.
    private val cleanReference: SchedulingService.RepeatReference =
        schedulingService.repeat(SCHEMA_CLEAN, SCHEMA_CLEAN) {
            runBlocking {
                val idIterator = idMapping.values.iterator()
                while (idIterator.hasNext()) {
                    if (idIterator.next().isStale()) idIterator.remove()
                }
                val schemaIterator = schemaMapping.values.iterator()
                while (schemaIterator.hasNext()) {
                    if (schemaIterator.next().isStale()) schemaIterator.remove()
                }
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
    suspend fun process(topic: String, root: JsonNode): AvroProcessingResult {
        if (!root.isObject) {
            throw HttpInvalidContentException("Expecting JSON object in payload")
        }
        if (root["key_schema_id"].isMissing && root["key_schema"].isMissing) {
            throw HttpInvalidContentException("Missing key schema")
        }
        if (root["value_schema_id"].isMissing && root["value_schema"].isMissing) {
            throw HttpInvalidContentException("Missing value schema")
        }

        return coroutineScope {
            val keyMappingJob = async(Dispatchers.IO) {
                schemaMapping(topic, false, root["key_schema_id"], root["key_schema"])
            }
            val valueMappingJob = async(Dispatchers.IO) {
                schemaMapping(topic, true, root["value_schema_id"], root["value_schema"])
            }

            val keyMapping = keyMappingJob.await()
            val valueMapping = valueMappingJob.await()

            val records = root["records"] ?: throw HttpInvalidContentException("Missing records")
            AvroProcessingResult(
                keyMapping.targetSchemaId,
                valueMapping.targetSchemaId,
                processor.process(topic, records, keyMapping, valueMapping),
            )
        }
    }

    private fun createMapping(topic: String, ofValue: Boolean, sourceSchema: Schema): JsonToObjectMapping {
        val latestSchema = schemaRetriever.getBySubjectAndVersion(topic, ofValue, -1)
        val schemaMapper = AvroDataMapperFactory.get()
            .createMapper(sourceSchema, latestSchema.schema, null)
        return JsonToObjectMapping(sourceSchema, latestSchema.schema, latestSchema.id, schemaMapper)
    }

    private suspend fun schemaMapping(topic: String, ofValue: Boolean, id: JsonNode?, schema: JsonNode?): JsonToObjectMapping {
        val subjectSuffix = if (ofValue) "value" else "key"
        val subject = "$topic-$subjectSuffix"
        return when {
            id?.isNumber == true -> {
                idMapping.computeIfAbsent(Pair(subject, id.asInt())) {
                    CachedValue(cacheConfig) {
                        val parsedSchema = try {
                            schemaRetriever.getBySubjectAndId(topic, ofValue, id.asInt())
                        } catch (ex: RestException) {
                            if (ex.statusCode == 404) {
                                throw HttpApplicationException(
                                    422,
                                    "schema_not_found",
                                    "Schema ID not found in subject",
                                )
                            } else {
                                throw HttpBadGatewayException("cannot get data from schema registry: ${ex.javaClass.simpleName}")
                            }
                        }
                        createMapping(topic, ofValue, parsedSchema.schema)
                    }
                }.get()
            }
            schema?.isTextual == true -> {
                schemaMapping.computeIfAbsent(Pair(subject, schema.asText())) {
                    CachedValue(cacheConfig) {
                        try {
                            val parsedSchema = Schema.Parser().parse(schema.textValue())
                            createMapping(topic, ofValue, parsedSchema)
                        } catch (ex: Exception) {
                            throw throw HttpApplicationException(
                                422,
                                "schema_not_found",
                                "Schema ID not found in subject",
                            )
                        }
                    }
                }.get()
            }
            else -> throw HttpInvalidContentException("No schema provided")
        }
    }

    override fun close() {
        cleanReference.close()
    }

    companion object {
        private val SCHEMA_CLEAN = Duration.ofHours(2)

        private val cacheConfig = CacheConfig(
            refreshDuration = 1.hours,
            retryDuration = 2.minutes,
            maxSimultaneousCompute = 3,
        )

        val JsonNode?.isMissing: Boolean
            get() = this == null || this.isNull
    }

    data class JsonToObjectMapping(
        val sourceSchema: Schema,
        val targetSchema: Schema,
        val targetSchemaId: Int,
        val mapper: AvroDataMapper,
    )
}
