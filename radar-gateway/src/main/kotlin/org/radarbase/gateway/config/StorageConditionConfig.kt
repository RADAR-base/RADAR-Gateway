package org.radarbase.gateway.config

data class StorageConditionConfig(
    val fileUploadEnabled: Boolean = true,
    val radarStorageType: String = "s3",
)
