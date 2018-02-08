package org.radarcns.gateway.kafka

import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.exception.NotAuthorizedException
import org.radarcns.auth.token.RadarToken

class AvroAuth(jwt: RadarToken) {
    val projectIds = jwt.roles?.keys
            ?.filter { jwt.hasPermissionOnProject(Permission.MEASUREMENT_CREATE, it) }
            ?.toSet() ?: emptySet()
    val defaultProject = projectIds.firstOrNull()
    val userId = jwt.subject!!
    val sourceIds = jwt.sources?.toSet() ?: emptySet()

    init {
        if (projectIds.isEmpty()) {
            throw NotAuthorizedException(
                    "User $userId cannot create measurements in any project")
        }

        if (sourceIds.isEmpty()) {
            throw NotAuthorizedException(
                    "Request JWT of user $userId did not contain source IDs")
        }
    }
}