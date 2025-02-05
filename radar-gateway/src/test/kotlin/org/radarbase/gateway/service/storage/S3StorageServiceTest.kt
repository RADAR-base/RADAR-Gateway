package org.radarbase.gateway.service.storage

import org.junit.jupiter.api.Assertions.assertThrows
import org.assertj.core.api.Assertions.assertThat
import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.gateway.config.S3StorageConfig
import org.radarbase.gateway.config.S3StoragePathConfig
import org.radarbase.gateway.exception.InvalidFileDetailsException
import java.io.ByteArrayInputStream

class S3StorageServiceTest {

    private val minioInit: MinioClientLoader = mockk()
    private val minioClient: MinioClient = mockk()
    private val s3StorageService = S3StorageService(
        s3StorageConfig = S3StorageConfig(path = S3StoragePathConfig(prefix = "my-sub-path")),
        minioClientLoader = minioInit,
    )

    private val multipartFile = ByteArrayInputStream("radar-file-content".toByteArray())

    companion object {
        private const val PROJECT_ID = "projectId"
        private const val SUBJECT_ID = "subjectId"
        private const val TOPIC_ID = "topicId"
        private const val FILE_NAME = "radar-file.txt"
    }

    @BeforeEach
    fun setUp() {
        every { minioInit.getBucketName() } returns "radar-bucket"
        every { minioInit.loadClient() } returns minioClient
        every { minioClient.putObject(any<PutObjectArgs>()) } returns mockk()
    }

    @Test
    fun shouldThrowExceptionsForIllegalArguments() {
        assertThrows(IllegalArgumentException::class.java) {
            s3StorageService.store(
                null,
                FILE_NAME,
                PROJECT_ID,
                SUBJECT_ID,
                TOPIC_ID,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            s3StorageService.store(
                multipartFile,
                FILE_NAME,
                null,
                SUBJECT_ID,
                TOPIC_ID,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            s3StorageService.store(
                multipartFile,
                FILE_NAME,
                PROJECT_ID,
                null,
                TOPIC_ID,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            s3StorageService.store(
                multipartFile,
                FILE_NAME,
                PROJECT_ID,
                SUBJECT_ID,
                null,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            s3StorageService.store(
                multipartFile,
                FILE_NAME,
                "",
                SUBJECT_ID,
                TOPIC_ID,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            s3StorageService.store(
                multipartFile,
                FILE_NAME,
                PROJECT_ID,
                "",
                TOPIC_ID,
            )
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            s3StorageService.store(
                multipartFile,
                FILE_NAME,
                PROJECT_ID,
                SUBJECT_ID,
                "",
            )
        }
    }

    @Test
    fun `should store file and return valid path`() {
        val path = s3StorageService.store(multipartFile, FILE_NAME, PROJECT_ID, SUBJECT_ID, TOPIC_ID)

        verify { minioClient.putObject(any<PutObjectArgs>()) }

        assertThat(path).matches(Regex("my-sub-path/$PROJECT_ID/$SUBJECT_ID/$TOPIC_ID/[0-9]+/[0-9]+_[a-z0-9-]+\\.txt").pattern)
    }
}
