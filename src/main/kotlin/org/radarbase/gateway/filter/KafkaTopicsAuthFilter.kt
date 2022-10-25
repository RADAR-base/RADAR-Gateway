package org.radarbase.gateway.filter

import jakarta.ws.rs.container.DynamicFeature
import jakarta.ws.rs.container.ResourceInfo
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.FeatureContext
import org.radarbase.gateway.resource.KafkaTopics
import org.radarbase.jersey.auth.filter.AuthenticationFilter

class KafkaTopicsAuthFilter(
    @Context private val authFilter: AuthenticationFilter,
) : DynamicFeature {
    override fun configure(resourceInfo: ResourceInfo, context: FeatureContext) {
        if (resourceInfo.resourceClass == KafkaTopics::class.java &&
            resourceInfo.resourceMethod.name == "topics"
        ) {
            context.register(authFilter)
        }
    }
}
