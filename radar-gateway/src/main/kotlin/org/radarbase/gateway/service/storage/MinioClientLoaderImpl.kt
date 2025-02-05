package org.radarbase.gateway.service.storage

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import jakarta.ws.rs.core.Context
import org.radarbase.gateway.config.S3StorageConfig

class MinioClientLoaderImpl(
    @Context private val s3StorageConfig: S3StorageConfig,
) : MinioClientLoader {

    private var minioClient: MinioClient? = null
    private var bucketName: String? = null

    private fun initMinioClient() {
        try {
            minioClient = MinioClient.Builder()
                .endpoint(s3StorageConfig.url)
                .credentials(s3StorageConfig.accessKey, s3StorageConfig.secretKey)
                .build().also { minio ->
                    s3StorageConfig.bucketName.let {
                        BucketExistsArgs.builder().bucket(it).build().run(minio::bucketExists)
                            .also { bucketExists ->
                                if (!bucketExists) {
                                    throw RuntimeException("S3 bucket $bucketName does not exist")
                                }
                            }
                        bucketName = it
                    }
                }
        } catch (ex: Exception) {
            ex.printStackTrace()
            throw RuntimeException("Could not connect to s3.", ex)
        }
    }

    override fun loadClient(): MinioClient {
        return minioClient ?: initMinioClient().run {
            minioClient!!
        }
    }

    override fun getBucketName(): String = bucketName!!
}
