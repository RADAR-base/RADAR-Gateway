package org.radarbase.gateway.service.storage

import io.minio.PutObjectArgs
import jakarta.ws.rs.core.Context
import org.radarbase.gateway.config.S3StorageConfig
import org.radarbase.gateway.exception.FileStorageException
import org.radarbase.gateway.exception.InvalidPathDetailsException
import org.radarbase.gateway.service.storage.path.StoragePath
import org.radarbase.gateway.utils.requiresListNonNullOrBlank
import org.slf4j.LoggerFactory
import java.io.InputStream

class S3StorageService(
    @Context private val s3StorageConfig: S3StorageConfig,
    @Context private val client: RadarMinioClient,
) : StorageService {

    override fun store(
        fileInputStream: InputStream?,
        path: StoragePath,
    ): String {
        requireNotNull(fileInputStream) { "fileStream must not be null" }
        requiresListNonNullOrBlank(
            listOf(path.filename, path.projectId, path.subjectId, path.topicId),
        ) {
            "File, project, subject and topic id must not be null or blank"
        }

        try {
            val filePath = StoragePath(
                prefix = s3StorageConfig.path.prefix ?: "",
                projectId = path.projectId,
                subjectId = path.subjectId,
                topicId = path.topicId,
                collectPerDay = s3StorageConfig.path.collectPerDay,
                filename = path.filename,
            )

            logger.debug("Attempt storing file at path: {}", filePath.fullPath)

            client.loadClient()
                .putObject(
                    PutObjectArgs.builder()
                        .bucket(client.bucketName)
                        .`object`(filePath.fullPath)
                        .stream(fileInputStream, fileInputStream.available().toLong(), -1)
                        .build(),
                )
            return filePath.fullPath
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
