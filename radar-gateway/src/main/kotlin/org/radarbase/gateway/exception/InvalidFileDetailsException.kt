package org.radarbase.gateway.exception

import jakarta.ws.rs.core.Response
import org.radarbase.jersey.exception.HttpApplicationException

class InvalidFileDetailsException(code: String, message: String) :
    HttpApplicationException(
        Response.Status.EXPECTATION_FAILED,
        Response.Status.EXPECTATION_FAILED.name.lowercase(),
        message,
    )
