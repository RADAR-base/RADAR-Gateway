package org.radarbase.gateway.kafka

import io.confluent.kafka.schemaregistry.client.CachedSchemaRegistryClient
import io.confluent.kafka.serializers.KafkaAvroSerializer
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.Producer
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.KafkaException
import org.radarbase.gateway.Config
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ExecutionException

class KafkaAvroProducer(
        config: Config
): Closeable {
    private val producer: Producer<Any, Any>

    init {
        logger.info("Creating Kafka producer")
        val props = HashMap<String, Any?>(config.kafka.producer).apply {
            this["schema.registry.url"] = config.kafka.schemaRegistryUrl
        }
        val schemaRegistryClient = CachedSchemaRegistryClient(config.kafka.schemaRegistryUrl, 10_000)
        val keySerializer = KafkaAvroSerializer(schemaRegistryClient)
        keySerializer.configure(props, true)
        val valueSerializer = KafkaAvroSerializer(schemaRegistryClient)
        valueSerializer.configure(props, false)
        producer = KafkaProducer(props, keySerializer, valueSerializer)
        logger.info("Created Kafka producer")
    }

    @Throws(KafkaException::class)
    fun produce(topic: String, records: List<Pair<GenericRecord, GenericRecord>>) {
        records
                .map { (key, value) -> producer.send(ProducerRecord(topic, key, value)) }
                .forEach {
                    try {
                        it.get() // asserts that the send completed and was successful
                    } catch (ex: ExecutionException) {
                        throw ex.cause!!
                    }
                }
    }

    override fun close() {
        producer.close()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(KafkaAvroProducer::class.java)
    }
}
