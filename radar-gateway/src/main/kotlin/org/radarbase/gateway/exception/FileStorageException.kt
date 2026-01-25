package org.radarbase.gateway.exception

import jakarta.ws.rs.core.Response
import org.radarbase.jersey.exception.HttpApplicationException

class FileStorageException(message: String) :
    HttpApplicationException(
        Response.Status.INTERNAL_SERVER_ERROR,
        Response.Status.INTERNAL_SERVER_ERROR.name.lowercase(),
        message,
    )
