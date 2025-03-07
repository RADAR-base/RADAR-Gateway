package org.radarbase.gateway.service.storage

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.gateway.config.S3StorageConfig
import org.radarbase.gateway.config.S3StoragePathConfig
import org.radarbase.gateway.exception.InvalidFileDetailsException
import org.radarbase.gateway.service.storage.path.StoragePath
import java.io.ByteArrayInputStream

class S3StorageServiceTest {

    private val client: RadarMinioClient = mockk()
    private val minioClient: MinioClient = mockk()
    private val s3StorageService by lazy {
        S3StorageService(
            s3StorageConfig = S3StorageConfig(path = S3StoragePathConfig(prefix = "my-sub-path")),
            client = client,
        )
    }

    private val multipartFile = ByteArrayInputStream("radar-file-content".toByteArray())

    companion object {
        private const val PROJECT_ID = "projectId"
        private const val SUBJECT_ID = "subjectId"
        private const val TOPIC_ID = "topicId"
        private const val FILE_NAME = "radar-file.txt"
    }

    @BeforeEach
    fun setUp() {
        every { client.bucketName } returns "radar-bucket"
        every { client.loadClient() } returns minioClient
        every { minioClient.putObject(any<PutObjectArgs>()) } returns mockk()
    }

    @Test
    fun shouldThrowExceptionsForIllegalArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            val storagePath = StoragePath(
                projectId = PROJECT_ID,
                subjectId = SUBJECT_ID,
                topicId = TOPIC_ID,
                filename = FILE_NAME,
            )

            s3StorageService.store(
                null,
                storagePath,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = "",
                subjectId = SUBJECT_ID,
                topicId = TOPIC_ID,
            )
            s3StorageService.store(
                multipartFile,
                storagePath,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = "",
                topicId = TOPIC_ID,
            )
            s3StorageService.store(
                multipartFile,
                storagePath,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = SUBJECT_ID,
                topicId = "",
            )

            s3StorageService.store(
                multipartFile,
                storagePath,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = " ", // Empty but not blank
                subjectId = SUBJECT_ID,
                topicId = TOPIC_ID,
            )

            s3StorageService.store(
                multipartFile,
                storagePath,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = " ",
                topicId = TOPIC_ID,
            )

            s3StorageService.store(
                multipartFile,
                storagePath,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = SUBJECT_ID,
                topicId = " ",
            )

            s3StorageService.store(
                multipartFile,
                storagePath,
            )
        }
    }

    @Test
    fun `should store file and return valid path`() {
        val storagePath = StoragePath(
            filename = FILE_NAME,
            projectId = PROJECT_ID,
            subjectId = SUBJECT_ID,
            topicId = TOPIC_ID,
        )

        val path = s3StorageService.store(multipartFile, storagePath)

        verify { minioClient.putObject(any<PutObjectArgs>()) }

        assertThat(path).matches(Regex("my-sub-path/$PROJECT_ID/$SUBJECT_ID/$TOPIC_ID/[0-9]+/[0-9]+_[a-z0-9-]+\\.txt").pattern)
    }
}
