package org.radarbase.gateway.resource

import kotlinx.serialization.Serializable

@Serializable
data class MPMetaToken(
    val refreshToken: String,
)
