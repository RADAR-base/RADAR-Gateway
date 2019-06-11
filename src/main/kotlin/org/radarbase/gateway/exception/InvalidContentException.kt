package org.radarbase.gateway.exception

class InvalidContentException(s: String) : HttpApplicationException(422, "invalid_content", s)
