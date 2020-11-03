package org.radarbase.gateway.io

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.radarbase.jersey.exception.HttpBadRequestException
import java.io.InputStream
import java.lang.reflect.Type
import javax.ws.rs.Consumes
import javax.ws.rs.core.Context
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.MultivaluedMap
import javax.ws.rs.ext.MessageBodyReader
import javax.ws.rs.ext.Provider

/** Reads in JSON Avro. */
@Provider
@Consumes("application/vnd.kafka.avro.v1+json", "application/vnd.kafka.avro.v2+json")
class AvroJsonReader(
    @Context objectMapper: ObjectMapper,
) : MessageBodyReader<JsonNode> {
    private val objectFactory = objectMapper.factory

    override fun readFrom(
        type: Class<JsonNode>?, genericType: Type?,
        annotations: Array<out Annotation>?, mediaType: MediaType?,
        httpHeaders: MultivaluedMap<String, String>?, entityStream: InputStream?,
    ): JsonNode {

        val parser = objectFactory.createParser(entityStream)
        return try {
            parser.readValueAsTree<JsonNode?>()
        } catch (ex: JsonParseException) {
            throw HttpBadRequestException("malformed_json", ex.message ?: ex.toString())
        } ?: throw HttpBadRequestException("malformed_json", "No content given")
    }

    override fun isReadable(
        type: Class<*>?, genericType: Type?,
        annotations: Array<out Annotation>?, mediaType: MediaType?,
    ) = true
}
