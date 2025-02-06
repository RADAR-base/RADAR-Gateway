package org.radarbase.gateway.inject

import jakarta.inject.Singleton
import org.glassfish.jersey.internal.inject.AbstractBinder
import org.glassfish.jersey.media.multipart.MultiPartFeature
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.config.S3StorageConfig
import org.radarbase.gateway.service.storage.RadarMinioClient
import org.radarbase.gateway.service.storage.RadarMinioClientFactory
import org.radarbase.gateway.service.storage.S3StorageService
import org.radarbase.gateway.service.storage.StorageService
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer

class FileStorageEnhancer(
    private val config: GatewayConfig,
) : JerseyResourceEnhancer {

    override val classes: Array<Class<*>> = buildList(1) {
        add(MultiPartFeature::class.java)
    }.toTypedArray()

    override fun AbstractBinder.enhance() {
        bind(config.s3)
            .to(S3StorageConfig::class.java)
            .`in`(Singleton::class.java)

        if (config.storageCondition.radarStorageType == "s3") {
            bindFactory(RadarMinioClientFactory::class.java)
                .to(RadarMinioClient::class.java)
                .`in`(Singleton::class.java)

            bind(S3StorageService::class.java)
                .to(StorageService::class.java)
                .`in`(Singleton::class.java)
        }
    }
}
