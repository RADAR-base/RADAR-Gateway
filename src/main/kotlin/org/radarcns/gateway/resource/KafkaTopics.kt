package org.radarcns.gateway.resource

import com.fasterxml.jackson.databind.JsonNode
import org.radarcns.auth.authorization.Permission.Entity.MEASUREMENT
import org.radarcns.auth.authorization.Permission.Operation.CREATE
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.ProxyClient
import org.radarcns.gateway.auth.Authenticated
import org.radarcns.gateway.auth.NeedsPermission
import org.radarcns.gateway.inject.ProcessAvro
import org.radarcns.gateway.io.AvroGenerator
import org.radarcns.gateway.util.AvroProcessor
import org.radarcns.gateway.util.Json
import java.io.InputStream
import javax.inject.Singleton
import javax.ws.rs.*
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.UriInfo

/** Topics submission and listing. Requests need authentication. */
@Path("/topics")
@Singleton
@Authenticated
class KafkaTopics {
    private val avroProcessor = AvroProcessor()

    @Context
    private lateinit var proxyClient: ProxyClient

    @Context
    private lateinit var uriInfo: UriInfo

    @Context
    private lateinit var headers: HttpHeaders

    @GET
    fun topics() = proxyClient.proxyRequest("GET", uriInfo, headers, null)

    @HEAD
    fun topicsHead() = proxyClient.proxyRequest("HEAD", uriInfo, headers, null)

    @Path("/{topic_name}")
    @GET
    fun topic() = proxyClient.proxyRequest("GET", uriInfo, headers, null)

    @Path("/{topic_name}")
    @HEAD
    fun topicHead() = proxyClient.proxyRequest("HEAD", uriInfo, headers, null)

    @Path("/{topic_name}")
    @POST
    @Consumes("application/vnd.kafka.avro.v1+json", "application/vnd.kafka.avro.v2+json")
    @NeedsPermission(MEASUREMENT, CREATE)
    @ProcessAvro
    fun postToTopic(tree: JsonNode, @Context radarToken: RadarToken) {
        val modifiedTree = avroProcessor.process(tree, radarToken)
        proxyClient.proxyRequest("POST", uriInfo, headers) { sink ->
            val generator = Json.factory.createGenerator(sink.outputStream())
            generator.writeTree(modifiedTree)
            generator.flush()
        }
    }

    @Path("/{topic_name}")
    @POST
    @ProcessAvro
    @Consumes("application/vnd.radarbase.avro.v1+binary")
    @NeedsPermission(MEASUREMENT, CREATE)
    fun postToTopicBinary(input: InputStream, @Context avroGenerator: AvroGenerator,
            @PathParam("topic_name") topic: String) {
        proxyClient.proxyRequest("POST", uriInfo, headers,
                avroGenerator.process(topic, input))
    }
}
