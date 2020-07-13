package org.radarbase.gateway.resource

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.gateway.inject.ProcessAvro
import org.radarbase.gateway.io.AvroProcessor
import org.radarbase.gateway.io.BinaryToAvroConverter
import org.radarbase.gateway.kafka.KafkaAdminService
import org.radarbase.gateway.kafka.ProducerPool
import org.radarbase.jersey.auth.Authenticated
import org.radarbase.jersey.auth.NeedsPermission
import org.radarbase.jersey.exception.HttpBadRequestException
import org.radarcns.auth.authorization.Permission.Entity.MEASUREMENT
import org.radarcns.auth.authorization.Permission.Operation.CREATE
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import javax.inject.Singleton
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response

/** Topics submission and listing. Requests need authentication. */
@Path("/topics")
@Singleton
@Authenticated
class KafkaTopics(
        @Context private val kafkaAdminService: KafkaAdminService,
        @Context private val producerPool: ProducerPool
) {
    @GET
    @Produces(PRODUCE_AVRO_V1_JSON)
    fun topics() = kafkaAdminService.listTopics()

    @Path("/{topic_name}")
    @GET
    fun topic(
            @PathParam("topic_name") topic: String
    ) = kafkaAdminService.topicInfo(topic)

    @OPTIONS
    @Path("/{topic_name}")
    fun topicOptions(): Response = Response.noContent()
            .header("Accept", "$ACCEPT_BINARY_V1,$ACCEPT_AVRO_V2_JSON,$ACCEPT_AVRO_V1_JSON")
            .header("Accept-Encoding", "gzip,lzfse")
            .header("Accept-Charset", "utf-8")
            .header("Allow", "HEAD,GET,POST,OPTIONS")
            .build()

    @Path("/{topic_name}")
    @POST
    @Consumes(ACCEPT_AVRO_V1_JSON, ACCEPT_AVRO_V2_JSON)
    @Produces(PRODUCE_AVRO_V1_JSON, PRODUCE_JSON)
    @NeedsPermission(MEASUREMENT, CREATE)
    @ProcessAvro
    fun postToTopic(
            tree: JsonNode,
            @PathParam("topic_name") topic: String,
            @Context avroProcessor: AvroProcessor): TopicPostResponse {

        val processingResult = avroProcessor.process(topic, tree)
        producerPool.produce(topic, processingResult.records)
        return TopicPostResponse(processingResult.keySchemaId, processingResult.valueSchemaId)
    }

    @Path("/{topic_name}")
    @POST
    @ProcessAvro
    @Consumes(ACCEPT_BINARY_V1)
    @Produces(PRODUCE_AVRO_V1_JSON, PRODUCE_JSON)
    @NeedsPermission(MEASUREMENT, CREATE)
    fun postToTopicBinary(
            input: InputStream,
            @Context binaryToAvroConverter: BinaryToAvroConverter,
            @PathParam("topic_name") topic: String): TopicPostResponse {

        val processingResult = try {
            binaryToAvroConverter.process(topic, input)
        } catch (ex: IOException) {
            logger.error("Invalid RecordSet content: {}", ex.toString())
            throw HttpBadRequestException("bad_content", "Content is not a valid binary RecordSet")
        }

        producerPool.produce(topic, processingResult.records)

        return TopicPostResponse(processingResult.keySchemaId, processingResult.valueSchemaId)
    }

    data class TopicPostResponse(
            @JsonProperty("key_schema_id")
            val keySchemaId: Int,
            @JsonProperty("value_schema_id")
            val valueSchemaId: Int)

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTopics::class.java)
        const val ACCEPT_AVRO_V1_JSON = "application/vnd.kafka.avro.v1+json"
        const val ACCEPT_AVRO_V2_JSON = "application/vnd.kafka.avro.v2+json"
        const val ACCEPT_BINARY_V1 = "application/vnd.radarbase.avro.v1+binary"
        const val PRODUCE_AVRO_V1_JSON = "application/vnd.kafka.v1+json"
        const val PRODUCE_JSON = "application/json"
    }
}
