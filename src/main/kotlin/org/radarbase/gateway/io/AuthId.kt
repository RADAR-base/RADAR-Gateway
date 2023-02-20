package org.radarbase.gateway.io

import org.radarbase.auth.authorization.Permission
import org.radarbase.jersey.auth.Auth

data class AuthId(
    val projectId: String?,
    val userId: String?,
    val sourceId: String?,
) {
    fun checkPermission(auth: Auth, checkSourceId: Boolean, topic: String) {
        if (checkSourceId) {
            auth.checkPermissionOnSource(
                Permission.MEASUREMENT_CREATE,
                projectId,
                userId,
                sourceId,
                "POST $topic",
            )
        } else {
            auth.checkPermissionOnSubject(
                Permission.MEASUREMENT_CREATE,
                projectId,
                userId,
                "POST $topic",
            )
        }
    }
}
