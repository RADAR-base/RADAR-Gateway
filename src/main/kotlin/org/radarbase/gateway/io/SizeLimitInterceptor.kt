package org.radarbase.gateway.io

import org.radarbase.gateway.Config
import org.radarbase.gateway.inject.ProcessAvro
import javax.annotation.Priority
import javax.inject.Singleton
import javax.ws.rs.Priorities
import javax.ws.rs.core.Context
import javax.ws.rs.ext.Provider
import javax.ws.rs.ext.ReaderInterceptor
import javax.ws.rs.ext.ReaderInterceptorContext

/**
 * Limits data stream to a maximum request size.
 */
@Provider
@ProcessAvro
@Singleton
@Priority(Priorities.ENTITY_CODER + 100)
class SizeLimitInterceptor(@Context private val config: Config) : ReaderInterceptor {
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        context.inputStream = LimitedInputStream(context.inputStream, config.server.maxRequestSize)
        return context.proceed()
    }
}
