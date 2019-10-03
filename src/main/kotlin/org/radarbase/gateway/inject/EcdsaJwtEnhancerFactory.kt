package org.radarbase.gateway.inject

import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.config.GeneralExceptionResourceEnhancer
import org.radarbase.jersey.config.HttpExceptionResourceEnhancer
import org.radarbase.jersey.config.ManagementPortalResourceEnhancer
import org.radarbase.jersey.config.RadarJerseyResourceEnhancer


/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class EcdsaJwtEnhancerFactory : EnhancerFactory {
    override fun createEnhancers(config: Config) = listOf(
            GatewayResourceEnhancer(config),
            RadarJerseyResourceEnhancer(AuthConfig(
                    managementPortalUrl = config.managementPortalUrl,
                    jwtResourceName = config.jwtResourceName,
                    jwtIssuer = config.jwtIssuer,
                    jwtECPublicKeys = config.jwtECPublicKeys,
                    jwtRSAPublicKeys = config.jwtRSAPublicKeys,
                    jwtKeystoreAlias = config.jwtKeystoreAlias,
                    jwtKeystorePassword = config.jwtKeystorePassword,
                    jwtKeystorePath = config.jwtKeystorePath)),
            ManagementPortalResourceEnhancer(),
            HttpExceptionResourceEnhancer(),
            GeneralExceptionResourceEnhancer())
}
