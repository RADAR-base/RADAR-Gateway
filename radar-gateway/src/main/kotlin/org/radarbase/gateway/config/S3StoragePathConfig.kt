package org.radarbase.gateway.config

data class S3StoragePathConfig (
    val prefix: String = "",
    val collectPerDay: Boolean = true,
)
