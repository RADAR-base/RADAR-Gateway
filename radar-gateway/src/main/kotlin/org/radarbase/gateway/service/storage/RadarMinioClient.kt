package org.radarbase.gateway.service.storage

import io.minio.BucketExistsArgs
import io.minio.MinioClient
import org.radarbase.gateway.config.S3StorageConfig

class RadarMinioClient(
    private val s3StorageConfig: S3StorageConfig,
) {

    private var minioClient: MinioClient? = null
    var bucketName: String? = null
        private set
        get() = if (minioClient != null) {
            field!!
        } else {
            minioClient = initMinioClient()
            field!!
        }

    private fun initMinioClient(): MinioClient {
        return try {
            MinioClient.Builder()
                .endpoint(s3StorageConfig.url)
                .credentials(s3StorageConfig.accessKey, s3StorageConfig.secretKey)
                .region(s3StorageConfig.region)
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

    fun loadClient(): MinioClient {
        minioClient = initMinioClient()
        return minioClient!!
    }

    fun close() {
        minioClient?.close()
    }
}
