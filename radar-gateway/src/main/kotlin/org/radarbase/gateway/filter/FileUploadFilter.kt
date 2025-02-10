package org.radarbase.gateway.filter

import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.inject.ProcessFileUpload

@Provider
@Singleton
@Priority(Priorities.USER)
@ProcessFileUpload
class FileUploadFilter(
    @Context private val config: GatewayConfig,
) : ContainerRequestFilter {

    override fun filter(requestContext: ContainerRequestContext?) {
        if (!config.storageCondition.fileUploadEnabled) {
            requestContext?.abortWith(
                Response.status(Response.Status.FORBIDDEN)
                    .entity("File uploading is not configured")
                    .build(),
            )
        }
    }
}
