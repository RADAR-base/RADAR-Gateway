package org.radarbase.gateway.filter

import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.container.ContainerRequestContext
import jakarta.ws.rs.container.ContainerRequestFilter
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import jakarta.ws.rs.ext.Provider
import kotlinx.coroutines.runBlocking
import org.radarbase.gateway.inject.ProcessAvro
import org.radarbase.gateway.kafka.KafkaAdminService
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpNotFoundException
import org.slf4j.LoggerFactory
import java.util.concurrent.ExecutionException

/**
 * Asserts that data is only submitted to Kafka topics that already exist.
 */
@Provider
@ProcessAvro
@Priority(Priorities.USER)
@Singleton
class KafkaTopicFilter constructor(
    @Context private val kafkaAdmin: KafkaAdminService,
) : ContainerRequestFilter {
    override fun filter(requestContext: ContainerRequestContext) {
        val topic = requestContext.uriInfo.pathParameters.getFirst("topic_name")

        runBlocking {
            try {
                // topic exists or exists after an update
                if (!kafkaAdmin.containsTopic(topic)) {
                    throw HttpNotFoundException("not_found", "Topic $topic not present in Kafka.")
                }
            } catch (ex: ExecutionException) {
                logger.error("Failed to list topics", ex)
                throw HttpApplicationException(
                    Response.Status.SERVICE_UNAVAILABLE,
                    "Cannot complete topic list operation",
                )
            }
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaTopicFilter::class.java)
    }
}
