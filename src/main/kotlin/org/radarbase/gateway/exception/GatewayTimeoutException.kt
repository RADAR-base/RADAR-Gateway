package org.radarbase.gateway.exception

import org.radarbase.auth.jersey.exception.HttpApplicationException
import javax.ws.rs.core.Response.Status

class GatewayTimeoutException(message: String)
    : HttpApplicationException(Status.GATEWAY_TIMEOUT, "gateway_timeout", message)
