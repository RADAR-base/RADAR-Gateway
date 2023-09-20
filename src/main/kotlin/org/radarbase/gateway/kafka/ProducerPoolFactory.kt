package org.radarbase.gateway.kafka

import jakarta.ws.rs.core.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.glassfish.jersey.internal.inject.DisposableSupplier
import org.radarbase.gateway.config.GatewayConfig

class ProducerPoolFactory(
    @Context private val config: GatewayConfig,
) : DisposableSupplier<ProducerPool> {
    override fun get(): ProducerPool = ProducerPool(config)

    override fun dispose(instance: ProducerPool?) {
        instance ?: return
        runBlocking(Dispatchers.Unconfined) {
            instance.close()
        }
    }
}
