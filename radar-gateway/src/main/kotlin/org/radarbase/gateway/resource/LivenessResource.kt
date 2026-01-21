package org.radarbase.gateway.resource

import jakarta.inject.Singleton
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.core.Response

/**
 * Simple liveness probe endpoint.
 * Returns 200 if the HTTP server is up and responding.
 */
@Path("/liveness")
@Singleton
class LivenessResource {
    @GET
    fun liveness(): Response = Response.ok().build()
}

