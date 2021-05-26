package org.radarbase.gateway.inject

import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.auth.MPConfig
import org.radarbase.jersey.config.ConfigLoader
import org.radarbase.jersey.config.EnhancerFactory
import org.radarbase.jersey.config.JerseyResourceEnhancer

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class ManagementPortalEnhancerFactory(private val config: Config) : EnhancerFactory {
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
            ConfigLoader.Enhancers.radar(authConfig),
            ConfigLoader.Enhancers.managementPortal(authConfig),
            ConfigLoader.Enhancers.health,
            ConfigLoader.Enhancers.httpException,
            ConfigLoader.Enhancers.generalException
        )
    }
}
