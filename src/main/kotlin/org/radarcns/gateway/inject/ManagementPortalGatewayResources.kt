package org.radarcns.gateway.inject

import org.glassfish.jersey.server.ResourceConfig
import org.radarcns.gateway.auth.ManagementPortalAuthenticationFilter

/** This binder needs to register all non-Jersey classes, otherwise initialization fails. */
class ManagementPortalGatewayResources : GatewayResources {
    override fun registerAuthentication(resources: ResourceConfig) {
        resources.register(ManagementPortalAuthenticationFilter::class.java)
    }
}
