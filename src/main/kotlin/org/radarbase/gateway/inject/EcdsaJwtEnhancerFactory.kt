package org.radarbase.gateway.inject

import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.jersey.auth.AuthConfig
import org.radarbase.jersey.auth.MPConfig
import org.radarbase.jersey.enhancer.EnhancerFactory
import org.radarbase.jersey.enhancer.Enhancers
import org.radarbase.jersey.enhancer.JerseyResourceEnhancer

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class EcdsaJwtEnhancerFactory(private val config: GatewayConfig) : EnhancerFactory {
    override fun createEnhancers(): List<JerseyResourceEnhancer> {
        val authConfig = AuthConfig(
            managementPortal = MPConfig(url = config.auth.managementPortalUrl),
            jwtResourceName = config.auth.resourceName,
            jwtIssuer = config.auth.issuer,
            jwtECPublicKeys = config.auth.publicKeys.ecdsa,
            jwtRSAPublicKeys = config.auth.publicKeys.rsa,
            jwtKeystoreAlias = config.auth.keyStore.alias,
            jwtKeystorePassword = config.auth.keyStore.password,
            jwtKeystorePath = config.auth.keyStore.path.toString(),
        )
        return listOf(
            GatewayResourceEnhancer(config),
            Enhancers.radar(authConfig),
            Enhancers.ecdsa,
            Enhancers.exception,
        )
    }
}
