package org.radarbase.gateway.kafka

import jakarta.ws.rs.core.Context
import org.radarbase.jersey.service.HealthService

class KafkaHealthMetric(
    @Context private val kafkaAdminService: KafkaAdminService,
) : HealthService.Metric("kafka") {
    override suspend fun computeStatus(): HealthService.Status = try {
        kafkaAdminService.containsTopic("health")
        HealthService.Status.UP
    } catch (ex: Throwable) {
        HealthService.Status.DOWN
    }

    override suspend fun computeMetrics(): Map<String, Any> = mapOf("status" to computeStatus())
}
