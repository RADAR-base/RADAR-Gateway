package org.radarcns.gateway.resource

import com.fasterxml.jackson.databind.JsonNode
import org.radarcns.auth.authorization.Permission.Entity.MEASUREMENT
import org.radarcns.auth.authorization.Permission.Operation.CREATE
import org.radarcns.gateway.auth.Authenticated
import org.radarcns.gateway.auth.NeedsPermission
import org.radarcns.gateway.inject.ProcessAvro
import org.radarcns.gateway.io.AvroProcessor
import org.radarcns.gateway.io.BinaryToAvroConverter
import org.radarcns.gateway.io.ProxyClient
import org.radarcns.gateway.io.ProxyClient.Companion.jerseyToOkHttpHeaders
import org.radarcns.gateway.util.Json
import org.radarcns.gateway.util.Json.jsonErrorResponse
import org.slf4j.LoggerFactory
import java.io.IOException
import java.io.InputStream
import javax.inject.Singleton
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.Response

/** Topics submission and listing. Requests need authentication. */
@Path("/topics")
@Singleton
@Authenticated
class KafkaTopics {
    @Context
    private lateinit var proxyClient: ProxyClient

    @GET
    fun topics() = proxyClient.proxyRequest("GET")

    @HEAD
    fun topicsHead() = proxyClient.proxyRequest("HEAD")

    @OPTIONS
    fun topicsOptions(): Response = Response.noContent()
            .header("Allow", "HEAD,GET,OPTIONS")
            .build()

    @Path("/{topic_name}")
    @GET
    fun topic() = proxyClient.proxyRequest("GET")

    @Path("/{topic_name}")
    @HEAD
    fun topicHead() = proxyClient.proxyRequest("HEAD")

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
    @NeedsPermission(MEASUREMENT, CREATE)
    @ProcessAvro
    fun postToTopic(
            tree: JsonNode,
            @Context avroProcessor: AvroProcessor): Response {

        val modifiedTree = avroProcessor.process(tree)
        return proxyClient.proxyRequest("POST") { sink ->
            val generator = Json.factory.createGenerator(sink.outputStream())
            generator.writeTree(modifiedTree)
            generator.flush()
        }
    }

    @Path("/{topic_name}")
    @POST
    @ProcessAvro
    @Consumes(ACCEPT_BINARY_V1)
    @NeedsPermission(MEASUREMENT, CREATE)
    fun postToTopicBinary(
            input: InputStream,
            @Context headers: HttpHeaders,
            @Context binaryToAvroConverter: BinaryToAvroConverter,
            @PathParam("topic_name") topic: String): Response {

        val proxyHeaders = jerseyToOkHttpHeaders(headers)
                .set("Content-Type", "application/vnd.kafka.avro.v2+json")
                .build()

        val dataProcessor = try {
            binaryToAvroConverter.process(topic, input)
        } catch (ex: IOException) {
            logger.error("Invalid recordset content: {}", ex.toString())
            throw BadRequestException(jsonErrorResponse(Response.Status.BAD_REQUEST, "bad_content", "Content is not a valid binary RecordSet"))
        }
        return proxyClient.proxyRequest("POST", proxyHeaders, dataProcessor)
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTopics::class.java)
        const val ACCEPT_AVRO_V1_JSON = "application/vnd.kafka.avro.v1+json"
        const val ACCEPT_AVRO_V2_JSON = "application/vnd.kafka.avro.v2+json"
        const val ACCEPT_BINARY_V1 = "application/vnd.radarbase.avro.v1+binary"
    }
}
