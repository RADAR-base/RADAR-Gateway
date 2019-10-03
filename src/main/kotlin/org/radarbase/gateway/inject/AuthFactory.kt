package org.radarbase.gateway.inject

import org.radarbase.gateway.auth.Auth
import org.radarbase.gateway.auth.RadarSecurityContext
import java.util.function.Supplier
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

/** Generates radar tokens from the security context. */
class AuthFactory : Supplier<Auth> {
    @Context
    private lateinit var context: ContainerRequestContext

    override fun get() = (context.securityContext as? RadarSecurityContext)?.auth
                ?: throw IllegalStateException("Created null wrapper")
}
