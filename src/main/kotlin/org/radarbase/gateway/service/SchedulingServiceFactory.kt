package org.radarbase.gateway.service

import org.glassfish.jersey.internal.inject.DisposableSupplier

class SchedulingServiceFactory: DisposableSupplier<SchedulingService> {
    override fun get() = SchedulingService()

    override fun dispose(instance: SchedulingService?) {
        instance?.close()
    }
}
