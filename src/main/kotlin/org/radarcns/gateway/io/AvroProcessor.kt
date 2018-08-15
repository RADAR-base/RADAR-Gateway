package org.radarcns.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.auth.AvroAuth
import org.radarcns.gateway.exception.InvalidContentException
import org.radarcns.gateway.util.Json
import java.io.IOException
import java.text.ParseException
import javax.inject.Singleton
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.core.Context
import javax.ws.rs.ext.Provider

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
@Provider
@Singleton
class AvroProcessor {
    @Context
    private lateinit var token: RadarToken

    /**
     * Validates given data with given access token and returns a modified output array.
     * The Avro content validation consists of testing whether both keys and values are being sent,
     * both with Avro schema. The authentication validation checks that all records contain a key
     * with a project ID, user ID and source ID that is also listed in the access token. If no
     * project ID is given in the key, it will be set to the first project ID where the user has
     * role {@code ROLE_PARTICIPANT}.
     *
     * @throws ParseException if the data does not contain valid JSON
     * @throws InvalidContentException if the data does not contain semantically correct Kafka Avro data.
     * @throws NotAuthorizedException if the data does not correspond to the access token.
     * @throws IOException if the data cannot be read
     */
    @Throws(ParseException::class, IOException::class)
    fun process(tree: JsonNode): JsonNode {
        println("auth $token with tree $tree")
        if (!tree.isObject) {
            throw ParseException("Expecting JSON object in payload", 0)
        }
        if (isNullField(tree["key_schema_id"]) && isNullField(tree["key_schema"])) {
            throw InvalidContentException("Missing key schema")
        }
        if (isNullField(tree["value_schema_id"]) && isNullField(tree["value_schema"])) {
            throw InvalidContentException("Missing value schema")
        }

        val records = tree["records"] ?: throw InvalidContentException("Missing records")
        processRecords(records, AvroAuth(token))
        return tree
    }

    @Throws(IOException::class, NotAuthorizedException::class)
    private fun processRecords(records: JsonNode, auth: AvroAuth) {
        if (!records.isArray) {
            throw InvalidContentException("Records should be an array")
        }

        records.forEach { record ->
            if (isNullField(record["value"])) {
                throw InvalidContentException("Missing value field in record")
            }
            val key = record["key"] ?: throw InvalidContentException("Missing key field in record")
            processKey(key, auth)
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class, NotAuthorizedException::class)
    private fun processKey(key: JsonNode, auth: AvroAuth) {
        if (!key.isObject) {
            throw InvalidContentException("Field key must be a JSON object")
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
                throw InvalidContentException(
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
