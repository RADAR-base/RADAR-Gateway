package org.radarbase.gateway.inject

import org.glassfish.jersey.internal.inject.AbstractBinder
import org.radarbase.auth.jersey.*
import org.radarbase.gateway.Config

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class ManagementPortalGatewayResources : GatewayResources {
    override fun createEnhancers(config: Config): List<JerseyResourceEnhancer> = listOf(
            RadarJerseyResourceEnhancer(AuthConfig(
                    managementPortalUrl = config.managementPortalUrl,
                    jwtResourceName = config.jwtResourceName,
                    jwtIssuer = config.jwtIssuer)),
            ManagementPortalResourceEnhancer())

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
