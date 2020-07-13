package org.radarbase.gateway.io

import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.Config
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.jersey.auth.Auth
import org.radarbase.producer.rest.SchemaRetriever
import javax.ws.rs.core.Context

class AvroProcessorFactory(
        @Context private val config: Config,
        @Context private val auth: Auth,
        @Context private val schedulingService: SchedulingService,
        @Context private val schemaRetriever: SchemaRetriever
): DisposableSupplier<AvroProcessor> {
    override fun get() = AvroProcessor(config, auth, schemaRetriever, schedulingService)

    override fun dispose(instance: AvroProcessor?) {
        instance?.close()
    }
}
