package org.radarcns.gateway.kafka

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import java.io.IOException
import javax.servlet.http.HttpServletResponse

class AvroValidator {
    private val factory = JsonFactory()
    private lateinit var parser: JsonParser
    private lateinit var userId: String
    private lateinit var sources: Set<String>

    @Throws(JsonParseException::class, IOException::class)
    fun validate(data: ByteArray, token: DecodedJWT) {
        this.parser = factory.createParser(data)
        this.userId = token.subject

        val sourceClaim = token.getClaim("sources") ?: throw IllegalArgumentException("Request JWT did not include sources")
        this.sources = HashSet(sourceClaim.asList(String::class.java))
        if (sources.isEmpty()) {
            throw IllegalArgumentException("Request JWT did not contain source IDs")
        }

        var hasKeySchema = false
        var hasValueSchema = false

        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw JsonParseException(parser, "Expecting JSON object in payload")
        }
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            when (parser.currentName) {
                "key_schema_id", "key_schema" -> {
                    hasKeySchema = true
                    parser.nextToken() // value
                }
                "value_schema_id", "value_schema" -> {
                    hasValueSchema = true
                    parser.nextToken() // value
                }
                "records" -> parseRecords()
                else -> skipToEndOfValue()
            }
        }
        if (!hasKeySchema) {
            throw IllegalArgumentException("Missing key schema")
        }
        if (!hasValueSchema) {
            throw IllegalArgumentException("Missing value schema")
        }
    }

    @Throws(IOException::class)
    private fun parseRecords() {
        if (parser.nextToken() != JsonToken.START_ARRAY) {
            throw semanticException("Expecting JSON array for records field")
        }
        while (parser.nextToken() != JsonToken.END_ARRAY) {
            var foundKey = false
            var foundValue = false

            while (parser.nextToken() != JsonToken.END_OBJECT) {
                val fieldName = parser.currentName
                if (fieldName == "key") {
                    foundKey = true
                    parseKey()
                } else {
                    if (fieldName == "value") {
                        foundValue = true
                    }
                    skipToEndOfValue()
                }
            }

            if (!foundKey) {
                throw semanticException("Missing key field in record")
            }
            if (!foundValue) {
                throw semanticException("Missing value field in record")
            }
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    private fun parseKey() {
        if (parser.nextToken() != JsonToken.START_OBJECT) {
            throw semanticException("Field key must be a JSON object")
        }

        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (parser.currentName == "userId") {
                if (parser.nextToken() != JsonToken.VALUE_STRING) {
                    throw semanticException("userId field string")
                }
                val userId = parser.valueAsString
                if (userId != this.userId) {
                    throw semanticException("record userId '" + userId
                            + "' does not match authenticated user ID '" + this.userId
                            + '\'')
                }
            } else if (parser.currentName == "sourceId") {
                if (parser.nextToken() != JsonToken.VALUE_STRING) {
                    throw semanticException("sourceId field string")
                }
                val sourceId = parser.valueAsString
                if (!sources.contains(sourceId)) {
                    throw semanticException("record sourceId '" + sourceId
                            + "' has not been added to JWT allowed IDs " + sources + ".")
                }
            } else {
                skipToEndOfValue()
            }
        }
    }

    private fun semanticException(message: String): IllegalArgumentException {
        val location = parser.currentLocation
        return IllegalArgumentException(message
                + " at [line " + location.lineNr
                + " column " + location.columnNr + ']')
    }

    /**
     * Skip to the last part of a value. This method assumes that the parser is now pointing to a
     * field name, and when it returns, it will point at the value if it is a simple type, or at the
     * end of an array or object if it is an array or object.
     */
    @Throws(IOException::class)
    private fun skipToEndOfValue() {
        val nextToken = parser.nextToken()
        // skip nested contents
        if (nextToken == JsonToken.START_ARRAY || nextToken == JsonToken.START_OBJECT) {
            parser.skipChildren()
        }
    }

    companion object Util {
        /** Return a JSON error string.  */
        @Throws(IOException::class)
        fun jsonErrorResponse(response: HttpServletResponse, statusCode: Int, error: String,
                              errorDescription: String) {
            response.setStatus(statusCode)
            response.setHeader("Content-Type", "application/json; charset=utf-8")
            response.writer.use { writer ->
                writer.write("{\"error_code\":")
                writer.write(Integer.toString(statusCode))
                writer.write(",\"message\":\"")
                writer.write(error)
                writer.write(": ")
                writer.write(errorDescription
                        .replace("\n", "")
                        .replace("\"", "'"))
                writer.write("\"}")
            }
        }
    }
}