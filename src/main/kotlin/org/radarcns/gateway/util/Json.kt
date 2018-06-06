package org.radarcns.gateway.util

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.util.BufferRecyclers
import com.fasterxml.jackson.databind.ObjectMapper
import org.glassfish.jersey.message.internal.ReaderWriter.UTF8
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status

object Json {
    val factory = JsonFactory()
    val mapper = ObjectMapper(factory)

    /** Return a JSON error string.  */
    fun jsonErrorResponse(statusCode: Status, error: String, errorDescription: String?)
            = jsonErrorResponse(statusCode.statusCode, error, errorDescription)

    /** Return a JSON error string.  */
    fun jsonErrorResponse(statusCode: Int, error: String, errorDescription: String?): Response {
        val stringEncoder = BufferRecyclers.getJsonStringEncoder()
        val quotedError = stringEncoder.quoteAsUTF8(error).toString(UTF8)
        val quotedDescription = stringEncoder.quoteAsUTF8(errorDescription).toString(UTF8)
        return Response.status(statusCode)
                .header("Content-Type", "application/json; charset=utf-8")
                .entity("{\"error\":\"$quotedError\","
                        + "\"error_description\":\"$quotedDescription\"}")
                .build()
    }
}
