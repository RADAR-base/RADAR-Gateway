package org.radarcns.gateway.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.databind.ObjectMapper
import java.io.IOException
import javax.servlet.http.HttpServletResponse

object Json {
    val factory = JsonFactory()
    val mapper = ObjectMapper(factory)

    /** Return a JSON error string.  */
    @Throws(IOException::class)
    fun jsonErrorResponse(response: HttpServletResponse, statusCode: Int, error: String,
                          errorDescription: String?) {
        response.setStatus(statusCode)
        response.setHeader("Content-Type", "application/json; charset=utf-8")
        response.outputStream.use { stream ->
            factory.createGenerator(stream).use {
                it.writeStartObject()
                it.writeNumberField("error_code", statusCode)
                it.writeStringField("error", error)
                it.writeStringField("message", "$error: $errorDescription")
                it.writeEndObject()
            }
        }
    }
}
