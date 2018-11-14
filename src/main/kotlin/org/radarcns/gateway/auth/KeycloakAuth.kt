package org.radarcns.gateway.auth

import com.auth0.jwt.interfaces.DecodedJWT
import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.authorization.Permission.MEASUREMENT_CREATE
import javax.ws.rs.ForbiddenException

/**
 * Parsed JWT for validating authorization of data contents.
 */
class KeycloakAuth(project: String?, private val token: DecodedJWT) : Auth {
    override val defaultProject = token.getClaim("project").asString() ?: project
    override val userId: String? = if (token.subject.isNotEmpty()) token.subject else null

    override fun checkPermission(projectId: String?, userId: String?, sourceId: String?) {
        if (!hasPermission(MEASUREMENT_CREATE)) {
            throw ForbiddenException("No permission to create measurement for " +
                    "project $projectId with user $userId and source $sourceId " +
                    "using token ${token.token}")
        }
    }

    override fun hasRole(projectId: String, role: String) = projectId == defaultProject

    override fun hasPermission(permission: Permission) = token.getClaim("scope")
            .asList(String::class.java)
            ?.contains(permission.scopeName())
            ?: false
}
