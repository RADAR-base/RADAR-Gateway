package org.radarbase.gateway.service.storage

import io.minio.MinioClient
import io.minio.PutObjectArgs
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.gateway.exception.InvalidFileDetailsException
import org.radarbase.gateway.service.storage.path.StoragePath
import java.io.ByteArrayInputStream
import java.time.Instant

class S3StorageServiceTest {

    private val client: RadarMinioClient = mockk()
    private val minioClient: MinioClient = mockk()
    private val s3StorageService by lazy {
        S3StorageService(
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

            runBlocking {
                s3StorageService.store(
                    null,
                    storagePath,
                )
            }
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = "",
                subjectId = SUBJECT_ID,
                topicId = TOPIC_ID,
            )
            runBlocking {
                s3StorageService.store(
                    multipartFile,
                    storagePath,
                )
            }
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = "",
                topicId = TOPIC_ID,
            )
            runBlocking {
                s3StorageService.store(
                    multipartFile,
                    storagePath,
                )
            }
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = SUBJECT_ID,
                topicId = "",
            )
            runBlocking {
                s3StorageService.store(
                    multipartFile,
                    storagePath,
                )
            }
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = " ", // Empty but not blank
                subjectId = SUBJECT_ID,
                topicId = TOPIC_ID,
            )

            runBlocking {
                s3StorageService.store(
                    multipartFile,
                    storagePath,
                )
            }
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = " ",
                topicId = TOPIC_ID,
            )

            runBlocking {
                s3StorageService.store(
                    multipartFile,
                    storagePath,
                )
            }
        }
        assertThrows(InvalidFileDetailsException::class.java) {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = SUBJECT_ID,
                topicId = " ",
            )

            runBlocking {
                s3StorageService.store(
                    multipartFile,
                    storagePath,
                )
            }
        }
    }

    @Test
    fun `should store file and return valid path`() {
        runBlocking {
            val storagePath = StoragePath(
                filename = FILE_NAME,
                projectId = PROJECT_ID,
                subjectId = SUBJECT_ID,
                topicId = TOPIC_ID,
            )

            val path = s3StorageService.store(multipartFile, storagePath, Instant.parse("2025-03-15T11:00:00Z"))

            verify { minioClient.putObject(any<PutObjectArgs>()) }
            val regex = Regex("""projectId/subjectId/topicId/20250315_1100/20250315_1100(_[a-f0-9\-]+)?\.txt""")
            assertTrue(regex.matches(path), "Path format is incorrect: $path")
        }
    }
}
