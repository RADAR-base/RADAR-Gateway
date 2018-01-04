package org.radarcns.gateway.kafka

import com.auth0.jwt.interfaces.DecodedJWT
import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.apache.http.auth.AuthenticationException
import org.radarcns.auth.authorization.RadarAuthorization.ROLES_CLAIM
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.ParseException
import javax.servlet.http.HttpServletResponse

class AvroValidator {
    private val factory = JsonFactory()
    private lateinit var mapper: ObjectMapper
    private lateinit var stream: ByteArrayOutputStream
    private lateinit var userId: String
    private lateinit var sourceIds: Set<String>
    private lateinit var projectId: String

    @Throws(ParseException::class, IOException::class, AuthenticationException::class)
    fun validate(data: InputStream, token: DecodedJWT): ByteArray {
        this.userId = token.subject

        val sourceClaim = token.getClaim("sourceIds")
        if (sourceClaim == null || sourceClaim.isNull) {
            throw AuthenticationException("Request JWT did not include sourceIds")
        }
        this.sourceIds = HashSet(sourceClaim.asList(String::class.java))
        if (sourceIds.isEmpty()) {
            throw AuthenticationException("Request JWT did not contain source IDs")
        }

        val rolesClaim = token.getClaim(ROLES_CLAIM)
        if (rolesClaim == null || rolesClaim.isNull) {
            throw AuthenticationException("User ${token.subject} is not a participant in any projectId")
        }
        val optProject = token.getClaim(ROLES_CLAIM).asList(String::class.java).stream()
                .filter { it.endsWith(":ROLE_PARTICIPANT") }
                .map { it.substring(0, it.lastIndexOf(':')) }
                .findAny()

        if (!optProject.isPresent) {
            throw AuthenticationException("User ${token.subject} is not a participant in any projectId")
        }
        this.projectId = optProject.get()

        stream = ByteArrayOutputStream()

        mapper = ObjectMapper(factory)
        val tree = mapper.readTree(data) ?: throw ParseException("Expecting JSON object in payload", 0)
        if (!tree.isObject) {
            throw ParseException("Expecting JSON object in payload, not an array", 0)
        }
        if (isNullField(tree["key_schema_id"]) && isNullField(tree["key_schema"])) {
            throw IllegalArgumentException("Missing key schema")
        }
        if (isNullField(tree["value_schema_id"]) && isNullField(tree["value_schema"])) {
            throw IllegalArgumentException("Missing value schema")
        }

        parseRecords(tree["records"])

        factory.createGenerator(stream).use {
            it.writeTree(tree)
        }

        return stream.toByteArray()
    }

    @Throws(IOException::class, AuthenticationException::class)
    private fun parseRecords(records: JsonNode) {
        if (!records.isArray) {
            throw IllegalArgumentException("Records should be an array")
        }

        (0..records.size()).forEach { i ->
            val record = records[i]
            if (isNullField(record["key"])) {
                throw IllegalArgumentException("Missing key field in record")
            }
            if (isNullField(record["value"])) {
                throw IllegalArgumentException("Missing value field in record")
            }
            parseKey(record["key"])
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class, AuthenticationException::class)
    private fun parseKey(key: JsonNode) {
        if (!key.isObject) {
            throw IllegalArgumentException("Field key must be a JSON object")
        }

        val project = key["projectId"]
        if (project != null && project.isNull) {
            val newProject = mapper.createObjectNode()
            newProject.put("string", this.projectId)
            (key as ObjectNode).set("projectId", newProject)
        }
        val user = key["userId"]
        if (user != null) {
            if (!user.isTextual) {
                throw IllegalArgumentException("userId field string")
            }
            val userId = user.asText()
            if (userId != this.userId) {
                throw AuthenticationException("record userId '" + userId
                        + "' does not match authenticated user ID '" + this.userId
                        + '\'')
            }
        }
        val source = key["sourceId"]
        if (source != null) {
            if (!source.isTextual) {
                throw IllegalArgumentException("sourceId field string")
            }
            val sourceId = source.asText()
            if (!sourceIds.contains(sourceId)) {
                throw AuthenticationException("record sourceId '" + sourceId
                        + "' has not been added to JWT allowed IDs " + sourceIds + ".")
            }
        }
    }

    companion object Util {
        fun isNullField(field: JsonNode?): Boolean {
            return field == null || field.isNull
        }

        /** Return a JSON error string.  */
        @Throws(IOException::class)
        fun jsonErrorResponse(response: HttpServletResponse, statusCode: Int, error: String,
                              errorDescription: String?) {
            response.setStatus(statusCode)
            response.setHeader("Content-Type", "application/json; charset=utf-8")
            response.writer.use { writer ->
                writer.write("{\"error_code\":")
                writer.write(Integer.toString(statusCode))
                writer.write(",\"message\":\"")
                writer.write(error)
                writer.write(": ")
                writer.write(errorDescription
                        ?.replace("\n", "")
                        ?.replace("\"", "'") ?: "null")
                writer.write("\"}")
            }
        }
    }
}