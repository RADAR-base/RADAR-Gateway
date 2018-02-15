package org.radarcns.gateway.inject

import org.radarcns.auth.token.RadarToken
import java.security.Principal
import javax.ws.rs.core.SecurityContext

/**
 * Security context from a [RadarToken].
 */
class RadarSecurityContext(
        /** Get the RadarToken parsed from the bearer token.  */
        val token: RadarToken) : SecurityContext {

    override fun getUserPrincipal(): Principal {
        return Principal { token.subject }
    }

    /**
     * Maps roles in the shape `"project:role"` to a Management Portal role. Global roles
     * take the shape of `":global_role"`. This allows for example a
     * `@RolesAllowed(":SYS_ADMIN")` annotation to resolve correctly.
     * @param role role to be mapped
     * @return `true` if the RadarToken contains given project/role,
     * `false` otherwise
     */
    override fun isUserInRole(role: String): Boolean {
        val projectRole = role.split(":")
        return projectRole.size == 2 && token.roles
                .getOrDefault(projectRole[0], emptyList())
                .contains(projectRole[1])
    }

    override fun isSecure(): Boolean {
        return true
    }

    override fun getAuthenticationScheme(): String {
        return "JWT"
    }
}
