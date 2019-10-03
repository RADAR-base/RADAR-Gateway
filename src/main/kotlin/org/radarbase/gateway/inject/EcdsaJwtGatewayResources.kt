package org.radarbase.gateway.inject

import org.glassfish.jersey.internal.inject.AbstractBinder
import org.radarbase.auth.jersey.*
import org.radarbase.gateway.Config

class EcdsaJwtGatewayResources : GatewayResources {
    override fun createEnhancers(config: Config): List<JerseyResourceEnhancer> = listOf(
            RadarJerseyResourceEnhancer(AuthConfig(
                    managementPortalUrl = config.managementPortalUrl,
                    jwtResourceName = config.jwtResourceName,
                    jwtIssuer = config.jwtIssuer,
                    jwtECPublicKeys = config.jwtECPublicKeys,
                    jwtRSAPublicKeys = config.jwtRSAPublicKeys,
                    jwtKeystoreAlias = config.jwtKeystoreAlias,
                    jwtKeystorePassword = config.jwtKeystorePassword,
                    jwtKeystorePath = config.jwtKeystorePath)),
            EcdsaResourceEnhancer())

    override fun enhanceBinder(binder: AbstractBinder) {
        binder.apply {
            val unverifiedProjectService = object : ProjectService {
                // no validation done
                override fun ensureProject(projectId: String) = Unit
            }
            bind(unverifiedProjectService)
                    .to(ProjectService::class.java)
        }
    }
}
