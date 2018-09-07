package org.radarcns.gateway.filter

import okhttp3.OkHttpClient
import okhttp3.Request
import org.radarcns.gateway.Config
import org.radarcns.gateway.exception.BadGatewayException
import org.radarcns.gateway.inject.ProcessAvro
import org.radarcns.gateway.util.CachedSet
import org.radarcns.gateway.util.Json
import org.radarcns.gateway.util.Json.jsonErrorResponse
import java.io.IOException
import java.time.Duration
import javax.annotation.Priority
import javax.inject.Singleton
import javax.ws.rs.Priorities
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response.Status
import javax.ws.rs.ext.Provider

/**
 * Asserts that data is only submitted to Kafka topics that already exist.
 */
@Provider
@ProcessAvro
@Priority(Priorities.USER)
@Singleton
class KafkaTopicFilter constructor(@Context config: Config, @Context private val client: OkHttpClient) : ContainerRequestFilter {
    private val cachedTopics = CachedSet(SUCCESS_TIMEOUT, FAILURE_TIMEOUT, this::getSubjects)
    private val stringArrayReader = Json.mapper.readerFor(Array<String>::class.java)
    private val request = Request.Builder().url("${config.restProxyUrl}/topics").build()

    override fun filter(requestContext: ContainerRequestContext) {
        val topic = requestContext.uriInfo.pathParameters.getFirst("topic_name")

        // topic exists or exists after an update
        if (!cachedTopics.contains(topic)) {
            requestContext.abortWith(
                    jsonErrorResponse(Status.NOT_FOUND, "not_found",
                            "Topic $topic not present in Kafka."))
        }
    }

    private fun getSubjects(): Set<String> {
        try {
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw BadGatewayException("Cannot query rest proxy url: " + response.code())
                }
                val input = response.body()?.byteStream() ?: throw BadGatewayException(
                        "Rest proxy did not return any data")

                stringArrayReader.readValue<Array<String>>(input).toSet()
            }
        } catch (ex: IOException) {
            throw BadGatewayException(
                    "Failed to retrieve topics from Kafka REST PROXY: ${ex.message}")
        }
    }

    companion object Constants {
        private val SUCCESS_TIMEOUT = Duration.ofHours(1)
        private val FAILURE_TIMEOUT = Duration.ofMinutes(1)
    }
}
