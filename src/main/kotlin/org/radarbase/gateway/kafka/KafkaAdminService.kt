package org.radarbase.gateway.kafka

import jakarta.ws.rs.core.Context
import jakarta.ws.rs.core.Response
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.TopicDescription
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpNotFoundException
import org.radarbase.kotlin.coroutines.CacheConfig
import org.radarbase.kotlin.coroutines.CachedSet
import org.radarbase.kotlin.coroutines.CachedValue
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class KafkaAdminService(@Context private val config: GatewayConfig) : Closeable {
    private val adminClient: AdminClient = AdminClient.create(config.kafka.admin)

    private val listCache = CachedSet<String>(listCacheConfig) {
        withContext(Dispatchers.IO) {
            try {
                adminClient.listTopics()
                    .names()
                    .get(3L, TimeUnit.SECONDS)
                    .filterTo(LinkedHashSet()) { !it.startsWith('_') }
            } catch (ex: Exception) {
                logger.error("Failed to list Kafka topics", ex)
                throw KafkaUnavailableException(ex)
            }
        }
    }
    private val topicInfo: ConcurrentMap<String, CachedValue<TopicInfo>> = ConcurrentHashMap()

    suspend fun containsTopic(topic: String): Boolean = listCache.contains(topic)

    suspend fun listTopics(): Collection<String> = listCache.get()

    suspend fun topicInfo(topic: String): TopicInfo {
        if (!containsTopic(topic)) {
            throw HttpNotFoundException("topic_not_found", "Topic $topic does not exist")
        }
        return topicInfo.computeIfAbsent(topic) {
            CachedValue(describeCacheConfig) {
                val topicDescriptions = withContext(Dispatchers.IO) {
                    try {
                        adminClient.describeTopics(listOf(topic))
                            .allTopicNames()
                            .get(3L, TimeUnit.SECONDS)
                    } catch (ex: Exception) {
                        logger.error("Failed to describe topics", ex)
                        throw KafkaUnavailableException(ex)
                    }
                }

                topicDescriptions[topic]?.toTopicInfo()
                    ?: throw HttpNotFoundException("topic_not_found", "Topic $topic does not exist")
            }
        }.get()
    }

    override fun close() = adminClient.close()

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaAdminService::class.java)

        private val listCacheConfig = CacheConfig(
            refreshDuration = 10.seconds,
            retryDuration = 2.seconds,
            maxSimultaneousCompute = 3,
        )
        private val describeCacheConfig = CacheConfig(
            refreshDuration = 30.minutes,
            retryDuration = 2.seconds,
            maxSimultaneousCompute = 2,
        )

        private fun org.apache.kafka.common.TopicPartitionInfo.toTopicPartitionInfo(): TopicPartitionInfo {
            return TopicPartitionInfo(partition = partition())
        }

        private fun TopicDescription.toTopicInfo() = TopicInfo(
            name(),
            partitions().map { it.toTopicPartitionInfo() },
        )

        class KafkaUnavailableException(ex: Exception) : HttpApplicationException(
            Response.Status.SERVICE_UNAVAILABLE,
            "kafka_unavailable",
            ex.message ?: ex.cause?.message ?: ex.javaClass.name,
        )
    }

    data class TopicInfo(
        val name: String,
        val partitions: List<TopicPartitionInfo>,
    )

    data class TopicPartitionInfo(
        val partition: Int,
    )
}
