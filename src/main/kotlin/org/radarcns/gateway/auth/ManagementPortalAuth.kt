package org.radarcns.gateway.auth

import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.authorization.Permission.MEASUREMENT_CREATE
import org.radarcns.auth.token.RadarToken
import javax.ws.rs.BadRequestException
import javax.ws.rs.NotAuthorizedException

/**
 * Parsed JWT for validating authorization of data contents.
 */
class ManagementPortalAuth(private val token: RadarToken) : Auth {
    override val defaultProject = token.roles.keys
            .firstOrNull { token.hasPermissionOnProject(MEASUREMENT_CREATE, it) }
    override val userId: String? = if (token.subject.isNotEmpty()) token.subject else null

    override fun checkPermission(projectId: String?, userId: String?, sourceId: String?) {
        if (!token.hasPermissionOnSource(MEASUREMENT_CREATE,
                        projectId ?: throw BadRequestException("Missing project ID in request"),
                        userId ?: throw BadRequestException("Missing user ID in request"),
                        sourceId ?: throw BadRequestException("Missing source ID in request"))) {
            throw NotAuthorizedException(
                    "No permission to create measurement for project $projectId with user $userId")
        }
    }

    override fun hasRole(projectId: String, role: String) = token.roles
            .getOrDefault(projectId, emptyList())
            .contains(role)

    override fun hasPermission(permission: Permission) = token.hasPermission(permission)
}
