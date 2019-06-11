package org.radarbase.gateway.exception

import org.slf4j.LoggerFactory
import javax.inject.Singleton
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

/** Handle exceptions without a specific mapper. */
@Provider
@Singleton
class UnhandledThrowableMapper : ExceptionMapper<Throwable> {
    @Context
    private lateinit var uriInfo: UriInfo

    init {
        logger.debug("Ready to map unmapped exceptions")
    }

    override fun toResponse(exception: Throwable): Response {
        logger.error("[500] {}", uriInfo.absolutePath, exception)
        return Response.serverError().build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UnhandledThrowableMapper::class.java)
    }
}
