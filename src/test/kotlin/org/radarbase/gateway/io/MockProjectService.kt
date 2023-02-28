/*
 * Copyright (c) 2019. The Hyve
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * See the file LICENSE in the root of this repository.
 */

package org.radarbase.gateway.io

import org.radarbase.auth.authorization.AuthorityReference
import org.radarbase.auth.authorization.Permission
import org.radarbase.auth.authorization.RoleAuthority
import org.radarbase.auth.token.DataRadarToken
import org.radarbase.jersey.auth.AuthService
import org.radarbase.jersey.auth.disabled.DisabledAuthorizationOracle
import org.radarbase.jersey.exception.HttpNotFoundException
import org.radarbase.jersey.service.ProjectService
import java.time.Instant

fun mockAuthService() = AuthService(
    DisabledAuthorizationOracle(),
    {
        DataRadarToken(
            roles = setOf(AuthorityReference(RoleAuthority.PARTICIPANT, "p")),
            scopes = setOf(Permission.MEASUREMENT_CREATE.scope()),
            subject = "u",
            username = "u",
            sources = listOf("s"),
            expiresAt = Instant.MAX,
            grantType = "refresh_token",
        )
    },
    projectService = MockProjectService(mapOf("main" to listOf("p"))),
)

class MockProjectService(
    private val projects: Map<String, List<String>>,
) : ProjectService {
    override suspend fun ensureOrganization(organizationId: String) {
        if (organizationId !in projects) {
            throw HttpNotFoundException("organization_not_found", "Project $organizationId not found.")
        }
    }

    override suspend fun listProjects(organizationId: String): List<String> = projects[organizationId]
        ?: throw HttpNotFoundException("organization_not_found", "Project $organizationId not found.")

    override suspend fun projectOrganization(projectId: String): String = projects.entries
        .firstOrNull { (_, ps) -> projectId in ps }
        ?.key
        ?: throw HttpNotFoundException("project_not_found", "Project $projectId not found.")

    override suspend fun ensureProject(projectId: String) {
        if (projects.values.none { projectId in it }) {
            throw HttpNotFoundException("project_not_found", "Project $projectId not found.")
        }
    }

    override suspend fun ensureSubject(projectId: String, userId: String) {
        ensureProject(projectId)
    }
}
