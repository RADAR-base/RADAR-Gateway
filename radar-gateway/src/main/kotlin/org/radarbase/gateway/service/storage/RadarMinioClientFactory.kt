package org.radarbase.gateway.service.storage

import jakarta.ws.rs.core.Context
import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.config.S3StorageConfig

class RadarMinioClientFactory(
    @Context private val s3StorageConfig: S3StorageConfig,
) : DisposableSupplier<RadarMinioClient> {
    override fun dispose(client: RadarMinioClient) {
        client.close()
    }

    override fun get(): RadarMinioClient {
        return RadarMinioClient(s3StorageConfig)
    }
}
