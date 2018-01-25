package org.radarcns.gateway.kafka

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.radarcns.auth.exception.NotAuthorizedException
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.util.Json
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.text.ParseException

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
     * @throws NotAuthorizedException if the data does not correspond to the access token.
     * @throws IOException if the data cannot be read
     */
    @Throws(ParseException::class, IOException::class, NotAuthorizedException::class)
    fun process(data: InputStream, token: RadarToken): ByteArray {
        val auth = AvroAuth(token)
        val tree = processTree(data, auth)
        return generateRequest(tree)
    }

    @Throws(ParseException::class, IOException::class)
    private fun processTree(data: InputStream, auth: AvroAuth): JsonNode {
        val tree = Json.factory.createParser(data).use {
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
            Json.factory.createGenerator(stream).use {
                it.writeTree(tree)
            }
            return stream.toByteArray()
        }
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    private fun processRecords(records: JsonNode, auth: AvroAuth) {
        if (!records.isArray) {
            throw IllegalArgumentException("Records should be an array")
        }

        records.forEach { record ->
            if (isNullField(record["value"])) {
                throw IllegalArgumentException("Missing value field in record")
            }
            val key = record["key"] ?: throw IllegalArgumentException("Missing key field in record")
            processKey(key, auth)
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class, NotAuthorizedException::class)
    private fun processKey(key: JsonNode, auth: AvroAuth) {
        if (!key.isObject) {
            throw IllegalArgumentException("Field key must be a JSON object")
        }

        val project = key["projectId"]
        if (project != null) {
            if (project.isNull) {
                // no project ID was provided, fill it in for the sender
                val newProject = Json.mapper.createObjectNode()
                newProject.put("string", auth.defaultProject)
                (key as ObjectNode).set("projectId", newProject)
            } else {
                // project ID was provided, it should match one of the validated project IDs.
                val projectId = project["string"]?.asText() ?:
                        throw IllegalArgumentException(
                                "Project ID should be wrapped in string union type")

                if (!auth.projectIds.contains(projectId)) {
                    throw NotAuthorizedException("record projectId '$projectId' does not match"
                           + " authenticated user ID '${auth.userId}'")
                }
            }
        }

        val user = key["userId"]?.asText()
        if (user != auth.userId) {
            throw NotAuthorizedException(
                    "record userId '$user' does not match authenticated user ID '${auth.userId}'")
        }
        val source = key["sourceId"]?.asText()
        if (!auth.sourceIds.contains(source)) {
            throw NotAuthorizedException(
                    "record sourceId '$source' has not been added to JWT allowed "
                            + "IDs ${auth.sourceIds}.")
        }
    }

    companion object Util {
        fun isNullField(field: JsonNode?): Boolean {
            return field == null || field.isNull
        }
    }
}