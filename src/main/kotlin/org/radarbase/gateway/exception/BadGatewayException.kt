package org.radarbase.gateway.exception

import org.radarbase.auth.jersey.exception.HttpApplicationException
import javax.ws.rs.core.Response.Status

class BadGatewayException(message: String)
    : HttpApplicationException(Status.BAD_GATEWAY, "bad_gateway", message)
