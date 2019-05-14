package org.radarcns.gateway.exception

import org.radarcns.gateway.util.Json
import javax.ws.rs.WebApplicationException

class RequestEntityTooLarge(message: String): WebApplicationException(message,
        Json.jsonErrorResponse(413, "request_entity_too_large", message))
