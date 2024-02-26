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
