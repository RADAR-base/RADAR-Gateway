package org.radarbase.gateway.io

import jakarta.annotation.Priority
import jakarta.inject.Singleton
import jakarta.ws.rs.Priorities
import jakarta.ws.rs.core.Context
import jakarta.ws.rs.ext.Provider
import jakarta.ws.rs.ext.ReaderInterceptor
import jakarta.ws.rs.ext.ReaderInterceptorContext
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.inject.ProcessAvro

/**
 * Limits data stream to a maximum request size.
 */
@Provider
@ProcessAvro
@Singleton
@Priority(Priorities.ENTITY_CODER + 100)
class SizeLimitInterceptor(@Context private val config: GatewayConfig) : ReaderInterceptor {
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any? {
        context.inputStream = LimitedInputStream(context.inputStream, config.server.maxRequestSize)
        return context.proceed()
    }
}
