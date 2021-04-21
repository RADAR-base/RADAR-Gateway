package org.radarbase.gateway.io

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.Consumes
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.MediaType
import jakarta.ws.rs.core.MultivaluedMap
import jakarta.ws.rs.ext.MessageBodyReader
import jakarta.ws.rs.ext.Provider
import org.radarbase.jersey.exception.HttpBadRequestException
import java.io.InputStream
import java.lang.reflect.Type

/** Reads in JSON Avro. */
@Provider
@Consumes("application/vnd.kafka.avro.v1+json", "application/vnd.kafka.avro.v2+json")
class AvroJsonReader(
    @Context objectMapper: ObjectMapper,
) : MessageBodyReader<JsonNode> {
    private val objectFactory = objectMapper.factory

    override fun readFrom(
        type: Class<JsonNode>?,
        genericType: Type?,
        annotations: Array<out Annotation>?,
        mediaType: MediaType?,
        httpHeaders: MultivaluedMap<String, String>?,
        entityStream: InputStream?,
    ): JsonNode {
        val parser = objectFactory.createParser(entityStream)
        return try {
            parser.readValueAsTree()
                ?: throw HttpBadRequestException("malformed_json", "No content given")
        } catch (ex: JsonParseException) {
            throw HttpBadRequestException("malformed_json", ex.message ?: ex.toString())
        }
    }

    override fun isReadable(
        type: Class<*>?,
        genericType: Type?,
        annotations: Array<out Annotation>?,
        mediaType: MediaType?,
    ) = true
}
