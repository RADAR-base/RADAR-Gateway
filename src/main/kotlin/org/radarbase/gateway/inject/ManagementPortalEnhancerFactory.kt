package org.radarbase.gateway.inject

import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.config.*

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class ManagementPortalEnhancerFactory(private val config: Config) : EnhancerFactory {
    override fun createEnhancers() = listOf(
            GatewayResourceEnhancer(config),
            RadarJerseyResourceEnhancer(AuthConfig(
                    managementPortalUrl = config.managementPortalUrl,
                    jwtResourceName = config.jwtResourceName,
                    jwtIssuer = config.jwtIssuer)),
            ManagementPortalResourceEnhancer(),
            HttpExceptionResourceEnhancer(),
            GeneralExceptionResourceEnhancer())
}
