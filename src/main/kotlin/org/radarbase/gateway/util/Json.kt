package org.radarbase.gateway.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper

object Json {
    val factory = JsonFactory()
    val mapper = ObjectMapper(factory)
}
