package org.radarbase.gateway.exception

import org.radarbase.auth.jersey.exception.HttpApplicationException

class InvalidContentException(s: String)
    : HttpApplicationException(422, "invalid_content", s)
