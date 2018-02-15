package org.radarcns.gateway.resource

import com.fasterxml.jackson.databind.JsonNode
import org.radarcns.auth.authorization.Permission.Entity.MEASUREMENT
import org.radarcns.auth.authorization.Permission.Operation.CREATE
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.ProxyClient
import org.radarcns.gateway.util.AvroProcessor
import org.radarcns.gateway.auth.NeedsPermission
import org.radarcns.gateway.inject.ProcessAvro
import org.radarcns.gateway.util.Json
import javax.inject.Singleton
import javax.ws.rs.Consumes
import javax.ws.rs.GET
import javax.ws.rs.POST
import javax.ws.rs.Path
import javax.ws.rs.core.Context
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.UriInfo

@Path("/topics")
@Singleton
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

    @Path("/{topic_name}")
    @GET
    fun topic() = proxyClient.proxyRequest("GET", uriInfo, headers, null)

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
}
