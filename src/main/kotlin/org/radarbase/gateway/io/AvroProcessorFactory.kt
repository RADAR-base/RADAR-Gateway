package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.ws.rs.core.Context
import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.jersey.auth.AuthService
import org.radarbase.producer.rest.SchemaRetriever

class AvroProcessorFactory(
    @Context private val config: GatewayConfig,
    @Context private val authService: AuthService,
    @Context private val objectMapper: ObjectMapper,
    @Context private val schedulingService: SchedulingService,
    @Context private val schemaRetriever: SchemaRetriever,
) : DisposableSupplier<AvroProcessor> {
    override fun get() = AvroProcessor(config, authService, schemaRetriever, objectMapper, schedulingService)

    override fun dispose(instance: AvroProcessor?) {
        instance?.close()
    }
}
