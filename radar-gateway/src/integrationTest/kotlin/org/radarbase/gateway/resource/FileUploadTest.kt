package org.radarbase.gateway.resource

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.forms.formData
import io.ktor.client.request.forms.submitFormWithBinaryData
import io.ktor.client.request.header
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.minio.BucketExistsArgs
import io.minio.GetObjectArgs
import io.minio.MakeBucketArgs
import io.minio.MinioClient
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation
import org.junit.jupiter.api.Order
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestMethodOrder
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.config.S3StorageConfig
import org.radarbase.jersey.config.ConfigLoader
import java.io.File
import java.security.MessageDigest

@TestMethodOrder(OrderAnnotation::class)
class FileUploadTest {

    @Order(1)
    @Test
    fun testBucketExists() {
        assertTrue(
            BucketExistsArgs.builder().bucket(s3StorageConfig.bucketName).build().run(minioClient::bucketExists),
        )
    }

    @Test
    @Order(2)
    fun testCorrectStoredFilePath() = runBlocking {
        val accessToken = mpHelper.requestAccessToken()

        val testFile = File(FILE_PATH)

        require(testFile.exists()) { "Test file not found: ${testFile.absolutePath}" }

        val response: HttpResponse = httpClient.submitFormWithBinaryData(
            url = "radar/sub-1/test-topic/upload",
            formData = formData {
                append(
                    "file",
                    testFile.readBytes(),
                    Headers.build {
                        append(
                            HttpHeaders.ContentDisposition,
                            "filename=\"${testFile.name}\"",
                        )
                        append(HttpHeaders.ContentType, ContentType.Application.OctetStream.toString())
                    },
                )
            },
        ) {
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }

        assertEquals(HttpStatusCode.Created, response.status)

        val locationHeader = response.headers[HttpHeaders.Location]
        requireNotNull(locationHeader) { "Location header is missing" }

        storedFilePath = locationHeader.substringAfter("/radar-gateway/")
        val filePathPattern = Regex("""radar/sub-1/test-topic/\d{8}_\d{4}/\d{8}_\d{4}_[\w-]+\.txt""")
        assertTrue(filePathPattern.matches(storedFilePath!!), "File path format does not match: $storedFilePath")
    }

    @Test
    @Order(3)
    fun testFileContentsHash() {
        requireNotNull(storedFilePath) { "Stored file path was not set in the previous test." }

        val bucketName = s3StorageConfig.bucketName
        val objectKey = storedFilePath!!

        val inputStream = minioClient.getObject(
            GetObjectArgs.builder()
                .bucket(bucketName)
                .`object`(objectKey)
                .build(),
        )

        val remoteBytes = inputStream.readBytes()
        inputStream.close()

        val localFile = File(FILE_PATH)
        require(localFile.exists()) { "Local test file not found: ${localFile.absolutePath}" }

        val localHash = computeHash(localFile)
        val remoteHash = computeHash(remoteBytes)

        assertEquals(localHash, remoteHash, "The local file and remote file hashes do not match.")
    }

    private fun computeHash(file: File, algorithm: String = "SHA-256"): String {
        val bytes = file.readBytes()
        return computeHash(bytes, algorithm)
    }

    private fun computeHash(bytes: ByteArray, algorithm: String = "SHA-256"): String {
        val digest = MessageDigest.getInstance(algorithm).digest(bytes)
        return digest.joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val FILE_PATH = "/etc/radar-gateway/gateway-upload-file.txt"
        const val CONFIG_PATH = "/etc/radar-gateway/gateway.yml"
        const val GATEWAY_URL = "http://localhost:8090/radar-gateway"

        private lateinit var httpClient: HttpClient

        private val gatewayConfig = ConfigLoader.loadConfig<GatewayConfig>(
            listOf(
                CONFIG_PATH,
            ),
            arrayOf(),
        ).withDefaults()

        private val s3StorageConfig: S3StorageConfig = gatewayConfig.s3

        private lateinit var minioClient: MinioClient
        private lateinit var mpHelper: MPTestSupport
        private var storedFilePath: String? = null

        @JvmStatic
        @BeforeAll
        fun setUp() {
            httpClient = HttpClient(CIO) {
                install(ContentNegotiation) {
                    json(
                        Json {
                            ignoreUnknownKeys = true
                            coerceInputValues = true
                        },
                    )
                }
                defaultRequest {
                    url("${GATEWAY_URL}/")
                }
            }

            mpHelper = MPTestSupport().apply {
                init()
            }

            minioClient = MinioClient.Builder()
                .endpoint(s3StorageConfig.url)
                .credentials(s3StorageConfig.accessKey, s3StorageConfig.secretKey)
                .region(s3StorageConfig.region)
                .build()

            val bucketExists =
                BucketExistsArgs.builder().bucket(s3StorageConfig.bucketName).build().run(minioClient::bucketExists)
            if (!bucketExists) {
                setUpBucketForMinio()
            }
        }

        fun setUpBucketForMinio() {
            val makeBucketArgs = MakeBucketArgs.builder()
                .bucket(s3StorageConfig.bucketName)
                .build()

            minioClient.makeBucket(makeBucketArgs)
        }
    }
}
