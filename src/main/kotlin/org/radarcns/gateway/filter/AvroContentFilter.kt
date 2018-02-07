package org.radarcns.gateway.filter

import com.auth0.jwt.interfaces.DecodedJWT
import org.radarcns.auth.exception.NotAuthorizedException
import org.radarcns.gateway.kafka.AvroProcessor
import org.radarcns.gateway.util.Json.jsonErrorResponse
import org.radarcns.gateway.util.ServletByteArrayWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.text.ParseException
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse

/**
 * Reads messages as semantically valid and authenticated Avro for the RADAR platform. Amends
 * unfilled security metadata as necessary.
 */
class AvroContentFilter : Filter {
    private lateinit var context: ServletContext
    private lateinit var processor: AvroProcessor

    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig) {
        this.context = filterConfig.servletContext!!
        this.context.log("AvroContentFilter initialized")

        this.processor = AvroProcessor()
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest

        // Only process POST requests
        if (!req.method.equals("POST", ignoreCase = true)) {
            chain.doFilter(request, response)
            return
        }

        val res = response as HttpServletResponse

        if (!req.contentType.startsWith("application/vnd.kafka.avro.v1+json")
                && !req.contentType.startsWith("application/vnd.kafka.avro.v2+json")) {
            this.context.log("Got incompatible media type")
            jsonErrorResponse(res, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_media_type", "Only Avro JSON messages are supported")
            return
        }

        val token = request.getAttribute("jwt") as? DecodedJWT
        if (token == null) {
            this.context.log("Request was not authenticated by a previous filter: "
                    + "no token attribute found or no user found")
            jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "configuration error")
            return
        }

        try {
            request.getInputStream().use { stream ->
                val data = ByteArrayInputStream(processor.process(stream, token))

                chain.doFilter(object : HttpServletRequestWrapper(req) {
                    @Throws(IOException::class)
                    override fun getInputStream(): ServletInputStream = ServletByteArrayWrapper(data)

                    @Throws(IOException::class)
                    override fun getReader(): BufferedReader = BufferedReader(InputStreamReader(data))
                }, response)
            }
        } catch (ex: ParseException) {
            jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST,
                    "malformed_content", ex.message)
        } catch (ex: NotAuthorizedException) {
            response.status = HttpServletResponse.SC_FORBIDDEN
            jsonErrorResponse(res, HttpServletResponse.SC_FORBIDDEN,
                    "authentication_mismatch", ex.message)
        } catch (ex: IOException) {
            context.log("IOException", ex)
            jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "Failed to process message: ${ex.message}")
        } catch (ex: IllegalArgumentException) {
            jsonErrorResponse(res, 422, "invalid_content", ex.message)
        }
    }

    override fun destroy() {
        // nothing to destroy
    }
}
