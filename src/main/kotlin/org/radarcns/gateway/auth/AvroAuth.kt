package org.radarcns.gateway.auth

import org.radarcns.auth.authorization.Permission.MEASUREMENT_CREATE
import org.radarcns.auth.token.RadarToken
import javax.ws.rs.NotAuthorizedException

/**
 * Parsed JWT for validating authorization of data contents.
 */
class AvroAuth(private val token: RadarToken) {
    val defaultProject = token.roles.keys
            .firstOrNull { token.hasPermissionOnProject(MEASUREMENT_CREATE, it) }
    val defaultUserId: String? = if (token.subject.isNotEmpty()) token.subject else null
    private val sourceIds = token.sources.toSet()

    fun checkPermission(projectId: String?, userId: String?, sourceId: String?) {
        if (!token.hasPermissionOnSubject(MEASUREMENT_CREATE, projectId, userId)) {
            throw NotAuthorizedException(
                    "No permission to create measurement for project $projectId and user $userId")
        }

        if (sourceId != null && !sourceIds.contains(sourceId)) {
            throw NotAuthorizedException("No permission to create measurement for source $sourceId")
        }
    }
}
