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
    @Context private val minioClientLoader: MinioClientLoader,
) : StorageService {

    override fun store(
        fileInputStream: InputStream?,
        filename: String?,
        projectId: String?,
        subjectId: String?,
        topicId: String?,
    ): String {
        requireNotNull(fileInputStream) { "fileStream must not be null" }
        requiresListNonNullOrBlank(
            listOf(filename, projectId, subjectId, topicId),
        ) {
            "File, project, subject and topic id must not be null or blank"
        }

        try {
            val filePath = StoragePath.builder()
                .prefix(s3StorageConfig.path.prefix)
                .projectId(projectId!!)
                .subjectId(subjectId!!)
                .topicId(topicId!!)
                .collectPerDay(s3StorageConfig.path.collectPerDay)
                .filename(filename!!)
                .build()

            logger.debug("Attempt storing file at path: {}", filePath.fullPath)

            minioClientLoader
                .loadClient().putObject(
                    PutObjectArgs.builder()
                        .bucket(minioClientLoader.getBucketName())
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
