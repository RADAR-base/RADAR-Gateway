package org.radarbase.gateway.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.radarbase.gateway.Config
import org.radarbase.gateway.util.CachedValue
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpNotFoundException
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response

class KafkaAdminService(@Context private val config: Config): Closeable {
    private val adminClient: AdminClient = AdminClient.create(config.kafka.admin)

    private val listCache = CachedValue<Set<String>>(LIST_REFRESH_DURATION, LIST_RETRY_DURATION) {
        try {
            adminClient.listTopics().names().get(3L, TimeUnit.SECONDS).toSet()
        } catch (ex: Exception) {
            throw HttpApplicationException(Response.Status.SERVICE_UNAVAILABLE, "zookeeper_unavailable", ex.message ?: ex.cause?.message)
        }
    }
    private val topicInfo: ConcurrentMap<String, CachedValue<TopicInfo>> = ConcurrentHashMap()

    fun containsTopic(topic: String): Boolean = listCache.compute({ it.contains(topic) }, { it })

    fun listTopics(): List<String> = listCache.retrieve().filter { !it.startsWith('_') }

    fun topicInfo(topic: String): TopicInfo {
        if (!containsTopic(topic)) {
            throw HttpNotFoundException("topic_not_found", "Topic $topic does not exist")
        }
        return topicInfo.computeIfAbsent(topic) { _ ->
            CachedValue(DESCRIBE_REFRESH_DURATION, DESCRIBE_RETRY_DURATION) {
                val topicDescription = try {
                    adminClient.describeTopics(listOf(topic))
                            .values()
                            .values
                            .first()
                            .get(3L, TimeUnit.SECONDS)

                } catch (ex: Exception) {
                    logger.error("Failed to describe topics", ex)
                    throw HttpApplicationException(Response.Status.SERVICE_UNAVAILABLE, "zookeeper_unavailable", ex.message ?: ex.cause?.message)
                }

                TopicInfo(topic, topicDescription.partitions().map { TopicPartitionInfo(it.partition()) })
            }
        }.retrieve()
    }

    override fun close() {
        adminClient.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaAdminService::class.java)

        private val DESCRIBE_REFRESH_DURATION = Duration.ofMinutes(30)
        private val DESCRIBE_RETRY_DURATION = Duration.ofSeconds(2)
        private val LIST_REFRESH_DURATION = Duration.ofSeconds(10)
        private val LIST_RETRY_DURATION = Duration.ofSeconds(2)
    }

    data class TopicInfo(
            val name: String,
            val partitions: List<TopicPartitionInfo>
    )

    data class TopicPartitionInfo(
            val partition: Int
    )
}
