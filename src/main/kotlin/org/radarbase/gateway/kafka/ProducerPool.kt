package org.radarbase.gateway.kafka

import org.apache.avro.generic.GenericRecord
import org.apache.kafka.common.KafkaException
import org.apache.kafka.common.errors.*
import org.radarbase.gateway.Config
import org.radarbase.jersey.exception.HttpApplicationException
import org.radarbase.jersey.exception.HttpBadGatewayException
import org.radarbase.jersey.exception.HttpBadRequestException
import org.radarbase.jersey.exception.HttpInternalServerException
import org.slf4j.LoggerFactory
import java.io.Closeable
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Semaphore
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response

class ProducerPool(
        @Context private val config: Config
): Closeable {
    private val semaphore = Semaphore(config.server.maxRequests)
    private val pool = ArrayBlockingQueue<KafkaAvroProducer>(config.kafka.poolSize)

    fun produce(topic: String, records: List<Pair<GenericRecord, GenericRecord>>) {
        if (!semaphore.tryAcquire()) throw HttpApplicationException(Response.Status.SERVICE_UNAVAILABLE, "Too many open Kafka requests")
        try {
            val producer = pool.poll() ?: KafkaAvroProducer(config)
            var reuse = true
            try {
                producer.produce(topic, records)
            } catch (ex: KafkaException) {
                when (ex) {
                    is ProducerFencedException,
                    is OutOfOrderSequenceException,
                    is AuthorizationException,
                    is AuthenticationException -> {
                        logger.error("Unrecoverable failure to send data: {}", ex.toString())
                        reuse = false
                        throw HttpBadGatewayException("Kafka cannot be reached: ${ex.javaClass}")
                    }
                    is TimeoutException -> {
                        logger.error("Cannot reach Kafka within time: {}", ex.toString())
                        throw HttpApplicationException(Response.Status.GATEWAY_TIMEOUT, "kafka_timeout", "Cannot reach Kafka to send data")
                    }
                    is SerializationException -> {
                        logger.error("Cannot serialize message: {}", ex.toString())
                        throw HttpBadRequestException("bad_serialization", "Cannot serialize message to Kafka")
                    }
                    else -> {
                        logger.error("Retryable failure to send data", ex)
                        throw HttpInternalServerException("kafka_send_failure", "Failed to send data to kafka: ${ex.javaClass}")
                    }
                }
            } finally {
                if (reuse && pool.offer(producer)) {
                    // reused
                } else {
                    producer.close()
                }
            }
        } finally {
            semaphore.release()
        }
    }

    override fun close() {
        generateSequence { pool.poll() }
                .forEach { it.close() }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ProducerPool::class.java)
    }
}
