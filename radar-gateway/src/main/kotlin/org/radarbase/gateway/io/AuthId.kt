package org.radarbase.gateway.io

import org.radarbase.auth.authorization.entityDetails

data class AuthId(
    val projectId: String?,
    val userId: String?,
    val sourceId: String?,
) {
    fun toEntity(includeSourceId: Boolean) = entityDetails {
        project(projectId)
        subject(userId)
        if (includeSourceId) source(sourceId)
    }
}
