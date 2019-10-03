package org.radarbase.gateway.io

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import org.radarbase.auth.jersey.exception.HttpApplicationException
import org.radarbase.gateway.util.Json
import java.io.InputStream
import java.lang.reflect.Type
import javax.ws.rs.Consumes
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.core.Response
import javax.ws.rs.ext.MessageBodyReader
import javax.ws.rs.ext.Provider

/** Reads in JSON Avro. */
@Provider
@Consumes("application/vnd.kafka.avro.v1+json", "application/vnd.kafka.avro.v2+json")
class AvroJsonReader : MessageBodyReader<JsonNode> {
    override fun readFrom(type: Class<JsonNode>?, genericType: Type?,
            annotations: Array<out Annotation>?, mediaType: MediaType?,
            httpHeaders: MultivaluedMap<String, String>?, entityStream: InputStream?): JsonNode {

        val parser = Json.factory.createParser(entityStream)
        return try {
            parser.readValueAsTree<JsonNode?>()
        } catch (ex: JsonParseException) {
            throw HttpApplicationException(Response.Status.BAD_REQUEST, "malformed_json", ex.message)
        } ?: throw HttpApplicationException(Response.Status.BAD_REQUEST, "malformed_json", "No content given")
    }

    override fun isReadable(type: Class<*>?, genericType: Type?,
            annotations: Array<out Annotation>?, mediaType: MediaType?) = true
}
