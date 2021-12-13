package org.radarbase.gateway.config

import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig
import org.apache.kafka.clients.CommonClientConfigs
import java.util.*

data class KafkaConfig(
    /** Number of Kafka brokers to keep in a pool for reuse in multiple requests. */
    val poolSize: Int = 20,
    /** Kafka producer settings. Read from https://kafka.apache.org/documentation/#producerconfigs. */
    val producer: Map<String, Any> = mapOf(),
    /** Kafka Admin Client settings. Read from https://kafka.apache.org/documentation/#adminclientconfigs. */
    val admin: Map<String, Any> = mapOf(),
    /** Kafka serialization settings, used in KafkaAvroSerializer. Read from [io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig]. */
    val serialization: Map<String, Any> = mapOf(),
) {
    fun withDefaults(): KafkaConfig = copy(
        producer = producerDefaults + producer + propertiesFromEnv(KAFKA_PRODUCER_PREFIX),
        admin = mutableMapOf<String, Any>().apply {
            producer[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG]?.let {
                this[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] = it
            }
            this += adminDefaults
            this += admin
            this += propertiesFromEnv(KAFKA_ADMIN_PREFIX)
        },
        serialization = serializationDefaults + serialization + propertiesFromEnv(KAFKA_SERIALIZATION_PREFIX)
    )

    fun validate() {
        check(producer[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] is String) { "${CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG} missing in kafka: producer: {} configuration" }
        check(admin[CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG] is String) { "${CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG} missing in kafka: admin: {} configuration" }
        val schemaRegistryUrl = serialization[AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG]
        check(schemaRegistryUrl is String || schemaRegistryUrl is List<*>) {
            "${AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG} missing in kafka: serialization: {} configuration"
        }
    }

    companion object {
        private val producerDefaults = mapOf(
            "security.protocol" to "PLAINTEXT",
            "request.timeout.ms" to 3000,
            "max.block.ms" to 6000,
            "linger.ms" to 10,
            "retries" to 5,
            "acks" to "all",
            "delivery.timeout.ms" to 6000,
        )
        private val adminDefaults = mapOf(
            "security.protocol" to "PLAINTEXT",
            "default.api.timeout.ms" to 6000,
            "request.timeout.ms" to 3000,
            "retries" to 5,
        )

        private const val KAFKA_PRODUCER_PREFIX = "KAFKA_PRODUCER_"
        private const val KAFKA_ADMIN_PREFIX = "KAFKA_ADMIN_"
        private const val KAFKA_SERIALIZATION_PREFIX = "KAFKA_SERIALIZATION_"

        private fun propertiesFromEnv(prefix: String): Map<String, String> = System.getenv()
            .filterKeys { it.startsWith(prefix) }
            .mapKeys { (k, _) ->
                k.removePrefix(prefix)
                    .lowercase(Locale.US)
                    .replace('_', '.')
            }

        private val serializationDefaults = mapOf<String, Any>(
            AbstractKafkaSchemaSerDeConfig.MAX_SCHEMAS_PER_SUBJECT_CONFIG to 10_000
        )
    }
}
