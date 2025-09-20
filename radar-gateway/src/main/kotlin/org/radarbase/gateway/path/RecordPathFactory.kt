package org.radarbase.gateway.path

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.radarbase.gateway.path.config.PathConfig
import java.nio.file.Paths
import java.util.regex.Pattern

abstract class RecordPathFactory {
    lateinit var pathConfig: PathConfig
        private set

    open fun init(
        config: PathConfig,
    ) {
        this.pathConfig = config.copy(
            path = config.path.copy(
                properties = buildMap {
                    putAll(config.path.properties)
                },
            ),
        )
    }

    /**
     * Get the relative path corresponding to given record on given topic.
     * @param pathParameters Parameters needed to determine the path
     * @return relative path corresponding to given parameters.
     */
    abstract suspend fun relativePath(
        pathParameters: PathFormatParameters,
    ): String

    companion object {
        private val ILLEGAL_CHARACTER_PATTERN = Pattern.compile("[^a-zA-Z0-9_-]+")
        private val rootPath = Paths.get("/")

        fun sanitizeId(id: Any?, defaultValue: String): String = id
            ?.let { ILLEGAL_CHARACTER_PATTERN.matcher(it.toString()).replaceAll("") }
            ?.takeIf { it.isNotEmpty() }
            ?: defaultValue

        val observationKeySchema: Schema = Schema.Parser().parse(
            """
            {
              "namespace": "org.radarcns.kafka",
              "type": "record",
              "name": "ObservationKey",
              "doc": "Key of an observation.",
              "fields": [
                {"name": "projectId", "type": ["null", "string"], "doc": "Project identifier. Null if unknown or the user is not enrolled in a project.", "default": null},
                {"name": "userId", "type": "string", "doc": "User Identifier created during the enrolment."},
                {"name": "sourceId", "type": "string", "doc": "Unique identifier associated with the source."}
              ]
            }
            """.trimIndent(),
        )

        fun GenericRecord.getFieldOrNull(fieldName: String): Schema.Field? {
            return schema.fields
                .find { it.name().equals(fieldName, ignoreCase = true) }
        }

        fun GenericRecord.getOrNull(fieldName: String): Any? = getFieldOrNull(fieldName)
            ?.let { get(it.pos()) }
    }
}
