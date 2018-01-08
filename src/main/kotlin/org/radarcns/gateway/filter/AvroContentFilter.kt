package org.radarcns.gateway.filter

import com.auth0.jwt.interfaces.DecodedJWT
import org.apache.http.auth.AuthenticationException
import org.radarcns.gateway.kafka.AvroProcessor
import org.radarcns.gateway.kafka.AvroProcessor.Util
import org.radarcns.gateway.util.ServletInputStreamWrapper
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStreamReader
import java.text.ParseException
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse

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
            Util.jsonErrorResponse(res, HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "unsupported_media_type", "Only Avro JSON messages are supported")
            return
        }

        val tokenObj = request.getAttribute("jwt")
        if (tokenObj == null) {
            this.context.log("Request was not authenticated by a previous filter: "
                    + "no token attribute found or no user found")
            Util.jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_error", "configuration error")
            res.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR)
            return
        }
        val token = tokenObj as DecodedJWT

        try {
            request.getInputStream().use { stream ->
                val data = processor.process(stream, token)

                chain.doFilter(object : HttpServletRequestWrapper(req) {
                    @Throws(IOException::class)
                    override fun getInputStream(): ServletInputStream {
                        return ServletInputStreamWrapper(
                                ByteArrayInputStream(data))
                    }

                    @Throws(IOException::class)
                    override fun getReader(): BufferedReader {
                        return BufferedReader(InputStreamReader(
                                ByteArrayInputStream(data)))
                    }
                }, response)
            }
        } catch (ex: ParseException) {
            Util.jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST, "malformed_content",
                    ex.message)
        } catch (ex: AuthenticationException) {
            Util.jsonErrorResponse(res, HttpServletResponse.SC_FORBIDDEN,
                    "authentication_mismatch", ex.message)
        } catch (ex: IOException) {
            context.log("IOException", ex)
            Util.jsonErrorResponse(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "server_exception",
                    "Failed to process message: " + ex.message)
        } catch (ex: IllegalArgumentException) {
            Util.jsonErrorResponse(res, 422, "invalid_content", ex.message)
        }

    }

    override fun destroy() {
        // nothing to destroy
    }
}
