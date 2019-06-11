package org.radarbase.gateway.exception

import javax.ws.rs.core.Response.Status

class BadGatewayException(message: String) : HttpApplicationException(Status.BAD_GATEWAY, "bad_gateway", message)
