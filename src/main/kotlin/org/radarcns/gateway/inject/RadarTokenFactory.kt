package org.radarcns.gateway.inject

import org.radarcns.auth.token.RadarToken
import java.util.function.Supplier
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

class RadarTokenFactory : Supplier<RadarToken> {
    @Context
    private lateinit var context: ContainerRequestContext

    override fun get(): RadarToken {
        return (context.securityContext as? RadarSecurityContext)?.token
                ?: throw IllegalStateException("Created null wrapper")
    }
}
