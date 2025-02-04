package org.radarbase.gateway.config

import org.radarbase.gateway.inject.ManagementPortalEnhancerFactory
import org.radarbase.jersey.enhancer.EnhancerFactory

data class GatewayConfig(
    /** Radar-jersey resource configuration class. */
    val resourceConfig: Class<out EnhancerFactory> = ManagementPortalEnhancerFactory::class.java,
    /** Authorization configurations. */
    val auth: AuthConfig = AuthConfig(),
    /** Kafka configurations. */
    val kafka: KafkaConfig = KafkaConfig(),
    /** Server configurations. */
    val server: GatewayServerConfig = GatewayServerConfig(),
    /** AWS s3 storage configuration */
    val s3: S3StorageConfig = S3StorageConfig(),
    /** Whether to enable or disable the configurations based on the storage conditions */
    val storageCondition: StorageConditionConfig = StorageConditionConfig(),
) {
    /** Fill in some default values for the configuration. */
    fun withDefaults(): GatewayConfig = copy(kafka = kafka.withDefaults())

    /**
     * Validate the configuration.
     * @throws IllegalStateException if the configuration is incorrect
     */
    fun validate() {
        kafka.validate()
        auth.validate()
    }
}
