package org.radarbase.gateway.config

data class S3StorageConfig(
    val url: String = "http://localhost:9000",
    val accessKey: String = "access-key",
    val secretKey: String = "secret-key",
    val bucketName: String = "radar",
    val path: S3StoragePathConfig = S3StoragePathConfig(),
)
