package org.radarbase.gateway.inject

import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.config.*


/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class EcdsaJwtEnhancerFactory(private val config: Config) : EnhancerFactory {
    override fun createEnhancers() = listOf(
            GatewayResourceEnhancer(config),
            RadarJerseyResourceEnhancer(AuthConfig(
                    managementPortalUrl = config.auth.managementPortalUrl,
                    jwtResourceName = config.auth.resourceName,
                    jwtIssuer = config.auth.issuer,
                    jwtECPublicKeys = config.auth.publicKeys.ecdsa,
                    jwtRSAPublicKeys = config.auth.publicKeys.rsa,
                    jwtKeystoreAlias = config.auth.keyStore.alias,
                    jwtKeystorePassword = config.auth.keyStore.password,
                    jwtKeystorePath = config.auth.keyStore.path)),
            ManagementPortalResourceEnhancer(),
            HttpExceptionResourceEnhancer(),
            GeneralExceptionResourceEnhancer())
}
