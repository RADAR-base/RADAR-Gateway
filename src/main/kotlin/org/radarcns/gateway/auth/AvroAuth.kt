package org.radarcns.gateway.auth

import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.filter.ManagementPortalAuthenticationFilter
import javax.ws.rs.NotAuthorizedException

/**
 * Parsed JWT for validating authorization of data contents.
 */
class AvroAuth(jwt: RadarToken) {
    val projectIds = jwt.roles?.keys
            ?.filter { jwt.hasPermissionOnProject(Permission.MEASUREMENT_CREATE, it) }
            ?.toSet() ?: emptySet()
    val defaultProject = projectIds.firstOrNull()
            ?: throw NotAuthorizedException(ManagementPortalAuthenticationFilter.getInvalidScopeChallenge("No project given"))
    val userId = jwt.subject
            ?: throw NotAuthorizedException(ManagementPortalAuthenticationFilter.getInvalidScopeChallenge("No subject given"))
    val sourceIds = jwt.sources?.toSet() ?: emptySet()

    init {
        if (sourceIds.isEmpty()) {
            throw NotAuthorizedException(
                    ManagementPortalAuthenticationFilter.getInvalidScopeChallenge(
                            "Request JWT of user $userId did not contain source IDs"))
        }
    }
}