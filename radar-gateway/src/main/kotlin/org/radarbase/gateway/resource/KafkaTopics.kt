package org.radarbase.gateway.resource

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import jakarta.inject.Singleton
import jakarta.ws.rs.*
import jakarta.ws.rs.container.AsyncResponse
import jakarta.ws.rs.container.Suspended
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import org.radarbase.auth.authorization.Permission.MEASUREMENT_CREATE
import org.radarbase.gateway.inject.ProcessAvro
import org.radarbase.gateway.io.AvroProcessor
import org.radarbase.gateway.io.BinaryToAvroConverter
import org.radarbase.gateway.kafka.KafkaAdminService
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.jersey.auth.NeedsPermission
import org.radarbase.jersey.exception.HttpBadRequestException
import org.radarbase.jersey.service.AsyncCoroutineService
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream

/** Topics submission and listing. Requests need authentication. */
@Path("/topics")
@Singleton
@Produces(
    KafkaTopics.PRODUCE_AVRO_V1_JSON,
    KafkaTopics.PRODUCE_AVRO_V2_JSON,
    KafkaTopics.PRODUCE_AVRO_V3_JSON,
    KafkaTopics.PRODUCE_AVRO_NON_SPECIFIC,
    KafkaTopics.PRODUCE_JSON,
)
class KafkaTopics(
    @Context private val kafkaAdminService: KafkaAdminService,
    @Context private val producerPool: ProducerPool,
    @Context private val asyncService: AsyncCoroutineService,
) {
    @GET
    fun topics(@Suspended asyncResponse: AsyncResponse) = asyncService.runAsCoroutine(asyncResponse) {
        kafkaAdminService.listTopics()
    }

    @Authenticated
    @Path("/{topic_name}")
    @GET
    fun topic(
        @PathParam("topic_name") topic: String,
        @Suspended asyncResponse: AsyncResponse,
    ) = asyncService.runAsCoroutine(asyncResponse) {
        kafkaAdminService.topicInfo(topic)
    }

    @Authenticated
    @OPTIONS
    @Path("/{topic_name}")
    fun topicOptions(): Response = Response.noContent()
        .header(
            "Accept",
            "$ACCEPT_JSON,$ACCEPT_BINARY_V1,$ACCEPT_AVRO_V2_JSON,$ACCEPT_AVRO_V1_JSON," +
                "$ACCEPT_AVRO_V3_JSON,$ACCEPT_AVRO_NON_SPECIFIC,$ACCEPT_BINARY_NON_SPECIFIC",
        )
        .header("Accept-Encoding", "gzip,lzfse")
        .header("Accept-Charset", "utf-8")
        .header("Allow", "HEAD,GET,POST,OPTIONS")
        .build()

    @Authenticated
    @Path("/{topic_name}")
    @POST
    @Consumes(ACCEPT_JSON, ACCEPT_AVRO_V1_JSON, ACCEPT_AVRO_V2_JSON, ACCEPT_AVRO_V3_JSON, ACCEPT_AVRO_NON_SPECIFIC)
    @NeedsPermission(MEASUREMENT_CREATE)
    @ProcessAvro
    fun postToTopic(
        tree: JsonNode?,
        @PathParam("topic_name") topic: String,
        @Context avroProcessor: AvroProcessor,
        @Suspended asyncResponse: AsyncResponse,
    ) {
        if (tree == null) {
            asyncResponse.resume(HttpBadRequestException("missing_body", "Missing contents in body"))
            return
        }
        asyncService.runAsCoroutine(asyncResponse) {
            val processingResult = avroProcessor.process(topic, tree)
            producerPool.produce(topic, processingResult.records)
            TopicPostResponse(processingResult.keySchemaId, processingResult.valueSchemaId)
        }
    }

    @Authenticated
    @Path("/{topic_name}")
    @POST
    @ProcessAvro
    @Consumes(ACCEPT_BINARY_NON_SPECIFIC, ACCEPT_BINARY_V1)
    @NeedsPermission(MEASUREMENT_CREATE)
    fun postToTopicBinary(
        input: InputStream?,
        @Context binaryToAvroConverter: BinaryToAvroConverter,
        @PathParam("topic_name") topic: String,
        @Suspended asyncResponse: AsyncResponse,
    ) {
        if (input == null) {
            asyncResponse.resume(HttpBadRequestException("missing_body", "Missing contents in body"))
            return
        }
        asyncService.runAsCoroutine(asyncResponse) {
            val processingResult = try {
                binaryToAvroConverter.process(topic, input)
            } catch (ex: IOException) {
                logger.error("Invalid RecordSet content: {}", ex.toString())
                throw HttpBadRequestException("bad_content", "Content is not a valid binary RecordSet")
            }

            producerPool.produce(topic, processingResult.records)

            TopicPostResponse(processingResult.keySchemaId, processingResult.valueSchemaId)
        }
    }

    data class TopicPostResponse(
        @JsonProperty("key_schema_id")
        val keySchemaId: Int,
        @JsonProperty("value_schema_id")
        val valueSchemaId: Int,
    )

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTopics::class.java)
        const val ACCEPT_JSON = "application/json"
        const val ACCEPT_AVRO_V1_JSON = "application/vnd.kafka.avro.v1+json"
        const val ACCEPT_AVRO_V2_JSON = "application/vnd.kafka.avro.v2+json"
        const val ACCEPT_AVRO_V3_JSON = "application/vnd.kafka.avro.v3+json"
        const val ACCEPT_AVRO_NON_SPECIFIC = "application/vnd.kafka.avro+json"
        const val ACCEPT_BINARY_V1 = "application/vnd.radarbase.avro.v1+binary"
        const val ACCEPT_BINARY_NON_SPECIFIC = "application/vnd.radarbase.avro+binary"
        const val PRODUCE_AVRO_V1_JSON = "application/vnd.kafka.v1+json"
        const val PRODUCE_AVRO_V2_JSON = "application/vnd.kafka.v2+json"
        const val PRODUCE_AVRO_V3_JSON = "application/vnd.kafka.v3+json"
        const val PRODUCE_AVRO_NON_SPECIFIC = "application/vnd.kafka+json"
        const val PRODUCE_JSON = "application/json"
    }
}
