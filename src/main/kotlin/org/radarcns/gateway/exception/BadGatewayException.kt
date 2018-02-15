package org.radarcns.gateway.exception

import org.radarcns.gateway.util.Json.jsonErrorResponse
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response.Status

class BadGatewayException(message: String) : WebApplicationException(
        jsonErrorResponse(Status.BAD_GATEWAY, "bad_gateway", message))
