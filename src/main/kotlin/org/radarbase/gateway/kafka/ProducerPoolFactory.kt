package org.radarbase.gateway.kafka

import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.Config
import javax.ws.rs.core.Context

class ProducerPoolFactory(
    @Context private val config: Config,
) : DisposableSupplier<ProducerPool> {
    override fun get(): ProducerPool = ProducerPool(config)

    override fun dispose(instance: ProducerPool?) {
        instance?.close()
    }
}
