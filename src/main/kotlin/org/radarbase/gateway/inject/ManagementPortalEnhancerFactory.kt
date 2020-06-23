package org.radarbase.gateway.inject

import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.config.*

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class ManagementPortalEnhancerFactory(private val config: Config) : EnhancerFactory {
    override fun createEnhancers() = listOf(
            GatewayResourceEnhancer(config),
            ConfigLoader.Enhancers.radar(AuthConfig(
                    managementPortalUrl = config.auth.managementPortalUrl,
                    jwtResourceName = config.auth.resourceName,
                    jwtIssuer = config.auth.issuer)),
            ConfigLoader.Enhancers.managementPortal,
            ConfigLoader.Enhancers.httpException,
            ConfigLoader.Enhancers.generalException)
}
