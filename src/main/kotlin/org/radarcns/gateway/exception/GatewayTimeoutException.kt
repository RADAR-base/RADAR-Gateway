package org.radarcns.gateway.exception

import org.radarcns.gateway.util.Json.jsonErrorResponse
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response.Status

class GatewayTimeoutException(message: String) : WebApplicationException(
        jsonErrorResponse(Status.GATEWAY_TIMEOUT, "gateway_timeout", message))
