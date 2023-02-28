package org.radarbase.gateway.kafka

import io.confluent.kafka.schemaregistry.client.SchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import kotlinx.coroutines.*
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.util.toCoroutine
import org.radarbase.kotlin.coroutines.forkJoin
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ExecutionException
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class KafkaAvroProducer(
    config: GatewayConfig,
    schemaRegistryClient: SchemaRegistryClient,
) {
    private val producer: Producer<Any, Any>

    init {
        logger.info("Creating Kafka producer")

        val keySerializer = KafkaAvroSerializer(schemaRegistryClient).apply {
            configure(config.kafka.serialization, true)
        }
        val valueSerializer = KafkaAvroSerializer(schemaRegistryClient).apply {
            configure(config.kafka.serialization, false)
        }
        producer = KafkaProducer(config.kafka.producer, keySerializer, valueSerializer)
        logger.info("Created Kafka producer")
    }

    @Throws(KafkaException::class)
    suspend fun produce(topic: String, records: List<Pair<GenericRecord, GenericRecord>>) = records
        .forkJoin(Dispatchers.IO) { (key, value) ->
            producer.send(ProducerRecord(topic, key, value)).toCoroutine()
        }

    suspend fun close() = withContext(Dispatchers.IO) {
        producer.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaAvroProducer::class.java)
    }
}
