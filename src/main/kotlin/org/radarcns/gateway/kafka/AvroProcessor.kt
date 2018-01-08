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
import java.util.*
import java.util.stream.Collectors
import javax.servlet.http.HttpServletResponse

class AvroProcessor {
    /**
     * Validates given data with given access token and returns a modified output array.
     * The Avro content validation consists of testing whether both keys and values are being sent,
     * both with Avro schema. The authentication validation checks that all records contain a key
     * with a project ID, user ID and source ID that is also listed in the access token. If no
     * project ID is given in the key, it will be set to the first project ID where the user has
     * role {@code ROLE_PARTICIPANT}.
     *
     * @throws ParseException if the data does not contain valid JSON
     * @throws IllegalArgumentException if the data does not contain semantically correct Kafka Avro data.
     * @throws AuthenticationException if the data does not correspond to the access token.
     * @throws IOException if the data cannot be read
     */
    @Throws(ParseException::class, IOException::class, AuthenticationException::class)
    fun process(data: InputStream, token: DecodedJWT): ByteArray {
        val auth = Auth(token)
        val tree = processTree(data, auth)
        return generateRequest(tree)
    }

    @Throws(ParseException::class, IOException::class)
    private fun processTree(data: InputStream, auth: Auth): JsonNode {
        val tree = factory.createParser(data).use {
            it.readValueAsTree<JsonNode>()
        } ?: throw ParseException("Expecting JSON object in payload", 0)
        if (!tree.isObject) {
            throw ParseException("Expecting JSON object in payload", 0)
        }
        if (isNullField(tree["key_schema_id"]) && isNullField(tree["key_schema"])) {
            throw IllegalArgumentException("Missing key schema")
        }
        if (isNullField(tree["value_schema_id"]) && isNullField(tree["value_schema"])) {
            throw IllegalArgumentException("Missing value schema")
        }

        val records = tree["records"] ?: throw IllegalArgumentException("Missing records")
        processRecords(records, auth)

        return tree
    }

    private fun generateRequest(tree: JsonNode): ByteArray {
        ByteArrayOutputStream().use { stream ->
            factory.createGenerator(stream).use {
                it.writeTree(tree)
            }
            return stream.toByteArray()
        }
    }

    @Throws(IOException::class, AuthenticationException::class)
    private fun processRecords(records: JsonNode, auth: Auth) {
        if (!records.isArray) {
            throw IllegalArgumentException("Records should be an array")
        }

        records.forEach { record ->
            if (isNullField(record["key"])) {
                throw IllegalArgumentException("Missing key field in record")
            }
            if (isNullField(record["value"])) {
                throw IllegalArgumentException("Missing value field in record")
            }
            processKey(record["key"], auth)
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class, AuthenticationException::class)
    private fun processKey(key: JsonNode, auth: Auth) {
        if (!key.isObject) {
            throw IllegalArgumentException("Field key must be a JSON object")
        }

        val project = key["projectId"]
        if (project != null) {
            if (project.isNull) {
                // no project ID was provided, fill it in for the sender
                val newProject = mapper.createObjectNode()
                newProject.put("string", auth.projectIds[0])
                (key as ObjectNode).set("projectId", newProject)
            } else {
                // project ID was provided, it should match one of the validated project IDs.
                val projectId = project["string"]?.asText() ?:
                        throw IllegalArgumentException(
                                "Project ID should be wrapped in string union type")

                if (!auth.projectIds.contains(projectId)) {
                    throw AuthenticationException("record projectId '$projectId' does not match"
                           + " authenticated user ID '${auth.userId}'")
                }
            }
        }

        val user = key["userId"]
        if (user != null && user.asText() != auth.userId) {
            throw AuthenticationException(
                    "record userId '${user.asText()}' does not match authenticated user ID "
                            + "'${auth.userId}'")
        }
        val source = key["sourceId"]
        if (source != null && !auth.sourceIds.contains(source.asText())) {
            throw AuthenticationException(
                    "record sourceId '${source.asText()}' has not been added to JWT allowed "
                            + "IDs ${auth.sourceIds}.")
        }
    }

    private class Auth(jwt: DecodedJWT) {
        val userId: String = jwt.subject
        val projectIds: List<String>
        val sourceIds: Collection<String>
        init {
            // get roles as a list, empty if not set
            val rolesClaim = jwt.getClaim(ROLES_CLAIM)?.asList(String::class.java)
                    ?: Collections.emptyList()

            projectIds = rolesClaim.stream()
                    .filter { it.endsWith(PARTICIPANT_SUFFIX) }
                    .map { it.substring(0, it.length - PARTICIPANT_SUFFIX.length) }
                    .collect(Collectors.toList())

            if (projectIds.isEmpty()) {
                throw AuthenticationException(
                        "User $userId is not a participant in any project")
            }

            val sourcesClaim = jwt.getClaim("sourceIds")?.asList(String::class.java)
                    ?: Collections.emptyList()
            if (sourcesClaim.isEmpty()) {
                throw AuthenticationException(
                        "Request JWT of user $userId did not contain source IDs")
            }
            this.sourceIds = HashSet(sourcesClaim)
        }
    }

    companion object Util {
        private val factory = JsonFactory()
        private val mapper = ObjectMapper(factory)

        private val PARTICIPANT_SUFFIX = ":ROLE_PARTICIPANT"

        fun isNullField(field: JsonNode?): Boolean {
            return field == null || field.isNull
        }

        /** Return a JSON error string.  */
        @Throws(IOException::class)
        fun jsonErrorResponse(response: HttpServletResponse, statusCode: Int, error: String,
                              errorDescription: String?) {
            response.setStatus(statusCode)
            response.setHeader("Content-Type", "application/json; charset=utf-8")
            response.outputStream.use { stream ->
                factory.createGenerator(stream).use {
                    it.writeStartObject()
                    it.writeNumberField("error_code", statusCode)
                    it.writeStringField("error", error)
                    it.writeStringField("message", "$error: $errorDescription")
                    it.writeEndObject()
                }
            }
        }
    }
}