package org.radarbase.gateway.filter

import org.radarbase.gateway.inject.ProcessAvro
import org.radarbase.gateway.kafka.KafkaAdminService
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpNotFoundException
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException
import javax.annotation.Priority
import javax.inject.Singleton
import javax.ws.rs.Priorities
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider

/**
 * Asserts that data is only submitted to Kafka topics that already exist.
 */
@Provider
@ProcessAvro
@Priority(Priorities.USER)
@Singleton
class KafkaTopicFilter constructor(
        @Context private val kafkaAdmin: KafkaAdminService
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val topic = requestContext.uriInfo.pathParameters.getFirst("topic_name")

        try {
            // topic exists or exists after an update
            if (!kafkaAdmin.containsTopic(topic)) {
                throw HttpNotFoundException("not_found", "Topic $topic not present in Kafka.")
            }
        } catch (ex: ExecutionException) {
            logger.error("Failed to list topics", ex)
            throw HttpApplicationException(Response.Status.SERVICE_UNAVAILABLE, "Cannot complete topic list operation")
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTopicFilter::class.java)
    }
}
