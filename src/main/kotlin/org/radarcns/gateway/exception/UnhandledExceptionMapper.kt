package org.radarcns.gateway.exception

import org.slf4j.LoggerFactory
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.core.UriInfo
import javax.ws.rs.ext.ExceptionMapper
import javax.ws.rs.ext.Provider

@Provider
class UnhandledExceptionMapper : ExceptionMapper<Throwable> {
    @Context
    private lateinit var uriInfo: UriInfo

    override fun toResponse(exception: Throwable): Response {
        logger.error("[500] {}", uriInfo.absolutePath, exception)
        return Response.serverError().build()
    }

    companion object {
        private val logger = LoggerFactory.getLogger(UnhandledExceptionMapper::class.java)
    }
}
