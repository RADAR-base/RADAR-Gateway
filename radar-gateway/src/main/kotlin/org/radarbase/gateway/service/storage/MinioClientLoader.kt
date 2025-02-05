package org.radarbase.gateway.service.storage

import io.minio.MinioClient

interface MinioClientLoader {
    fun loadClient(): MinioClient
    fun getBucketName(): String
}
