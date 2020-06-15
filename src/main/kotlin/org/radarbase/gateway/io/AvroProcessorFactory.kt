package org.radarbase.gateway.io

import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.Config
import org.radarbase.gateway.service.SchedulingService
import org.radarbase.jersey.auth.Auth
import javax.ws.rs.core.Context

class AvroProcessorFactory(
        @Context private val config: Config,
        @Context private val auth: Auth,
        @Context private val schedulingService: SchedulingService
): DisposableSupplier<AvroProcessor> {
    override fun get(): AvroProcessor = AvroProcessor(config, auth, schedulingService)

    override fun dispose(instance: AvroProcessor?) {
        instance?.close()
    }
}
