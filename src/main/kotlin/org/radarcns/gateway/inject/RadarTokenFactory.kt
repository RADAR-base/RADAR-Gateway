package org.radarcns.gateway.inject

import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.auth.RadarSecurityContext
import java.util.function.Supplier
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

/** Generates radar tokens from the security context. */
class RadarTokenFactory : Supplier<RadarToken> {
    @Context
    private lateinit var context: ContainerRequestContext

    override fun get() = (context.securityContext as? RadarSecurityContext)?.token
                ?: throw IllegalStateException("Created null wrapper")
}
