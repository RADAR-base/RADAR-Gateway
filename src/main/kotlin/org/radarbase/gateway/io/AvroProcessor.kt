package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import org.radarbase.auth.jersey.Auth
import org.radarbase.gateway.exception.InvalidContentException
import org.radarbase.gateway.util.Json
import org.radarcns.auth.authorization.Permission
import java.io.IOException
import java.text.ParseException
import javax.ws.rs.core.Context

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
class AvroProcessor(@Context private val auth: Auth) {

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
     * @throws IOException if the data cannot be read
     */
    @Throws(ParseException::class, IOException::class)
    fun process(tree: JsonNode): JsonNode {
        if (!tree.isObject) {
            throw InvalidContentException("Expecting JSON object in payload")
        }
        if (tree["key_schema_id"].isMissing && tree["key_schema"].isMissing) {
            throw InvalidContentException("Missing key schema")
        }
        if (tree["value_schema_id"].isMissing && tree["value_schema"].isMissing) {
            throw InvalidContentException("Missing value schema")
        }

        val records = tree["records"] ?: throw InvalidContentException("Missing records")
        processRecords(records)
        return tree
    }

    @Throws(IOException::class)
    private fun processRecords(records: JsonNode) {
        if (!records.isArray) {
            throw InvalidContentException("Records should be an array")
        }

        records.forEach { record ->
            if (record["value"].isMissing) {
                throw InvalidContentException("Missing value field in record")
            }
            val key = record["key"] ?: throw InvalidContentException("Missing key field in record")
            processKey(key)
        }
    }

    /** Parse single record key.  */
    @Throws(IOException::class)
    private fun processKey(key: JsonNode) {
        if (!key.isObject) {
            throw InvalidContentException("Field key must be a JSON object")
        }

        val projectId = key["projectId"]?.let { project ->
            if (project.isNull) {
                // no project ID was provided, fill it in for the sender
                val newProject = Json.mapper.createObjectNode()
                newProject.put("string", auth.defaultProject)
                (key as ObjectNode).set("projectId", newProject)
                auth.defaultProject
            } else {
                // project ID was provided, it should match one of the validated project IDs.
                project["string"]?.asText() ?: throw InvalidContentException(
                        "Project ID should be wrapped in string union type")
            }
        } ?: auth.defaultProject

        auth.checkPermissionOnSource(Permission.MEASUREMENT_CREATE,
                projectId, key["userId"]?.asText(), key["sourceId"]?.asText())
    }

    companion object Util {
        val JsonNode?.isMissing: Boolean
            get() = this == null || this.isNull
    }
}
