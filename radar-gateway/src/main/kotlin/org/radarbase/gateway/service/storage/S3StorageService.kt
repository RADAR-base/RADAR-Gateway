package org.radarbase.gateway.service.storage

import io.minio.PutObjectArgs
import jakarta.ws.rs.core.Context
import org.apache.avro.generic.GenericRecordBuilder
import org.radarbase.gateway.exception.FileStorageException
import org.radarbase.gateway.exception.InvalidPathDetailsException
import org.radarbase.gateway.path.FormattedPathFactory
import org.radarbase.gateway.path.PathFormatParameters
import org.radarbase.gateway.path.RecordPathFactory.Companion.observationKeySchema
import org.radarbase.gateway.path.config.PathConfig
import org.radarbase.gateway.path.config.PathFormatterConfig
import org.radarbase.gateway.path.config.PathFormatterConfig.Companion.DEFAULT_FORMAT
import org.radarbase.gateway.service.storage.path.StoragePath
import org.radarbase.gateway.utils.requiresListNonNullOrBlank
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.time.Instant

class S3StorageService(
    @Context private val client: RadarMinioClient,
) : StorageService {

    val pathConfig = PathConfig(
        factory = FormattedPathFactory::class.java.name,
        path = PathFormatterConfig(
            format = DEFAULT_FORMAT,
            plugins = "fixed time",
        ),
    )

    var factory = FormattedPathFactory()

    init {
        factory.init(pathConfig)
    }

    override suspend fun store(
        fileInputStream: InputStream?,
        path: StoragePath,
        time: Instant,
    ): String {
        requireNotNull(fileInputStream) { "fileStream must not be null" }
        requiresListNonNullOrBlank(
            listOf(path.filename, path.projectId, path.subjectId, path.topicId),
        ) {
            "File, project, subject and topic id must not be null or blank"
        }

        val extension = path.getFileExtension(path.filename)

        val record = GenericRecordBuilder(observationKeySchema).apply {
            set("projectId", path.projectId)
            set("userId", path.subjectId)
            set("sourceId", "unknown")
        }.build()

        try {
            val filePath: String = factory.relativePath(
                PathFormatParameters(
                    path.topicId,
                    key = record,
                    time = time,
                    extension = extension,
                ),
            )

            logger.debug("Attempt storing file at path: {}", filePath)

            val fileBytes: ByteArray = fileInputStream.use { input ->
                input.readBytes()
            }
            val actualLength = fileBytes.size.toLong()
            val uploadStream = ByteArrayInputStream(fileBytes)

            client.loadClient()
                .putObject(
                    PutObjectArgs.builder()
                        .bucket(client.bucketName)
                        .`object`(filePath)
                        .stream(uploadStream, actualLength, -1)
                        .build(),
                )
            return filePath
        } catch (e: IllegalArgumentException) {
            throw InvalidPathDetailsException("There is a problem resolving the path on the object storage ${e.message ?: ""}")
        } catch (e: Exception) {
            throw FileStorageException("There is a problem storing the file on the object storage ${e.message ?: ""}")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(S3StorageService::class.java)
    }
}
