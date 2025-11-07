package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.apache.avro.Schema
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.resource.KafkaTopics
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.jersey.auth.AuthService
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpBadGatewayException
import org.radarbase.jersey.exception.HttpInvalidContentException
import org.radarbase.kotlin.coroutines.CacheConfig
import org.radarbase.kotlin.coroutines.CachedValue
import org.radarbase.producer.avro.AvroDataMapper
import org.radarbase.producer.avro.AvroDataMapperFactory
import org.radarbase.producer.rest.RestException
import org.radarbase.producer.schema.SchemaRetriever
import org.slf4j.LoggerFactory
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
        val records = root["records"]
        if (records.isMissing) {
            throw HttpInvalidContentException("Missing records")
        }

        return coroutineScope {
            val keyMappingJob = async {
                schemaMapping(topic, false, root["key_schema_id"], root["key_schema"])
            }
            val valueMappingJob = async {
                schemaMapping(topic, true, root["value_schema_id"], root["value_schema"])
            }

            val keyMapping = keyMappingJob.await()
            val valueMapping = valueMappingJob.await()

            AvroProcessingResult(
                keyMapping.targetSchemaId,
                valueMapping.targetSchemaId,
                processor.process(topic, records, keyMapping, valueMapping),
            )
        }
    }

    private suspend fun createMapping(topic: String, ofValue: Boolean, sourceSchema: Schema): JsonToObjectMapping {
        val latestSchema = schemaRetriever.getByVersion(topic, ofValue, -1)
        val schemaMapper = AvroDataMapperFactory.createMapper(sourceSchema, latestSchema.schema, null)
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

                            if ((   subject == "sensorkit_acceleration-key" ||
                                    subject == "sensorkit_rotation_rate-key"||
                                    subject == "sensorkit_visits-key" ||
                                    subject == "sensorkit_phone_usage-key" ||
                                    subject == "sensorkit_ambient_pressure-key" ||
                                    subject == "sensorkit_pedometer-key" ||
                                    subject == "sensorkit_on_wrist-key" ||
                                    subject == "sensorkit_keyboard_metrics-key" ||
                                    subject == "sensorkit_device_usage-key" ||
                                    subject == "sensorkit_telephony_speech_metrics-key" ||
                                    subject == "sensorkit_message_usage-key" ||
                                    subject == "sensorkit_ambient_light-key"
                                )
                                && id.asInt() == 2) {
                                logger.warn("Schema ID 2 not found in subject $subject, ID replaced with 3")
                                schemaRetriever.getById(topic, ofValue, 3)
                            } else if (subject == "sensorkit_acceleration-value" && id.asInt() == 127) {
                                logger.warn("Schema ID 127 not found in subject $subject, ID replaced with 114")
                                schemaRetriever.getById(topic, ofValue, 114)
                            } else if (subject == "sensorkit_rotation_rate-value" && id.asInt() == 138) {
                                logger.warn("Schema ID 138 not found in subject $subject, ID replaced with 133")
                                schemaRetriever.getById(topic, ofValue, 133)
                            } else if (subject == "sensorkit_visits-value" && id.asInt() == 136) {
                                logger.warn("Schema ID 136 not found in subject $subject, ID replaced with 115")
                                schemaRetriever.getById(topic, ofValue, 115)
                            } else if (subject == "sensorkit_phone_usage-value" && id.asInt() == 134) {
                                logger.warn("Schema ID 134 not found in subject $subject, ID replaced with 116")
                                schemaRetriever.getById(topic, ofValue, 116)
                            } else if (subject == "sensorkit_pedometer-value" && id.asInt() == 129) {
                                logger.warn("Schema ID 129 not found in subject $subject, ID replaced with 118")
                                schemaRetriever.getById(topic, ofValue, 118)
                            } else if (subject == "sensorkit_keyboard_metrics-value" && id.asInt() == 131) {
                                logger.warn("Schema ID 131 not found in subject $subject, ID replaced with 132")
                                schemaRetriever.getById(topic, ofValue, 132)
                            } else if (subject == "sensorkit_device_usage-value" && id.asInt() == 126) {
                                logger.warn("Schema ID 126 not found in subject $subject, ID replaced with 146")
                                schemaRetriever.getById(topic, ofValue, 146)
                            } else if (subject == "sensorkit_telephony_speech_metrics-value" && id.asInt() == 132) {
                                logger.warn("Schema ID 132 not found in subject $subject, ID replaced with 123")
                                schemaRetriever.getById(topic, ofValue, 123)
                            } else if (subject == "sensorkit_message_usage-value" && id.asInt() == 130) {
                                logger.warn("Schema ID 130 not found in subject $subject, ID replaced with 147")
                                schemaRetriever.getById(topic, ofValue, 147)
                            } else if (subject == "sensorkit_on_wrist-value" && id.asInt() == 128) {
                                logger.warn("Schema ID 128 not found in subject $subject, ID replaced with 129")
                                schemaRetriever.getById(topic, ofValue, 129)
                            } else if (subject == "sensorkit_ambient_light-value" && id.asInt() == 125) {
                                logger.warn("Schema ID 125 not found in subject $subject, ID replaced with 122")
                                schemaRetriever.getById(topic, ofValue, 122)
                            } else if (subject == "sensorkit_ambient_pressure-value" && id.asInt() == 133) {
                                logger.warn("Schema ID 133 not found in subject $subject, ID replaced with 117")
                                schemaRetriever.getById(topic, ofValue, 117)
                            } else {
                                schemaRetriever.getById(topic, ofValue, id.asInt())
                            }

                        } catch (ex: RestException) {
                            if (ex.status == HttpStatusCode.NotFound) {
                                throw HttpApplicationException(
                                    422,
                                    "schema_not_found",
                                    "Schema ID for $subject not found in subject, ID = ${id.asText()}",
                                )
                            } else {
                                throw HttpBadGatewayException("cannot get data from schema registry: $ex")
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

private val logger = LoggerFactory.getLogger(KafkaTopics::class.java)
