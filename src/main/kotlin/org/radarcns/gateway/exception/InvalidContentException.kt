package org.radarcns.gateway.exception

import org.radarcns.gateway.util.Json.jsonErrorResponse
import javax.ws.rs.WebApplicationException

class InvalidContentException(s: String) : WebApplicationException(s,
        jsonErrorResponse(422, "invalid_content", s))