package org.radarbase.gateway.inject

import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.auth.MPConfig
import org.radarbase.jersey.enhancer.EnhancerFactory
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer
import org.radarbase.jersey.enhancer.Enhancers

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class ManagementPortalEnhancerFactory(private val config: GatewayConfig) : EnhancerFactory {
    override fun createEnhancers(): List<JerseyResourceEnhancer> {
        val authConfig = AuthConfig(
            managementPortal = MPConfig(
                url = config.auth.managementPortalUrl,
            ),
            jwtResourceName = config.auth.resourceName,
            jwtIssuer = config.auth.issuer,
        )
        return listOf(
            GatewayResourceEnhancer(config),
            Enhancers.radar(authConfig),
            Enhancers.managementPortal(authConfig),
            Enhancers.health,
            Enhancers.exception,
        )
    }
}
