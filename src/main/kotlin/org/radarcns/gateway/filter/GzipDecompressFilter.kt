package org.radarcns.gateway.filter

import org.radarcns.gateway.kafka.AvroProcessor
import org.radarcns.gateway.kafka.AvroProcessor.Util.jsonErrorResponse
import org.radarcns.gateway.util.ServletInputStreamWrapper
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.util.zip.GZIPInputStream
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletRequestWrapper
import javax.servlet.http.HttpServletResponse

/**
 * Decompresses GZIP-compressed data. Uncompressed data is not modified. The data is decompressed
 * lazily, when request data read by subsequent filters and servlets.
 */
class GzipDecompressFilter : Filter {

    private lateinit var context: ServletContext

    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig) {
        context = filterConfig.servletContext
        context.log("GzipUncompressFilter initialized")
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val req = request as HttpServletRequest
        val res = response as HttpServletResponse

        val encoding = req.getHeader("Content-Encoding")?.toLowerCase() ?: "identity"
        when (encoding) {
            "identity" -> chain.doFilter(request, response)
            "gzip" -> {
                context.log("Decompressing input")
                chain.doFilter(object : HttpServletRequestWrapper(req) {
                    @Throws(IOException::class)
                    override fun getInputStream(): ServletInputStream {
                        val gzipStream = GZIPInputStream(super.getInputStream())
                        return ServletInputStreamWrapper(gzipStream)
                    }

                    @Throws(IOException::class)
                    override fun getReader(): BufferedReader {
                        val gzipStream = GZIPInputStream(super.getInputStream())
                        return BufferedReader(InputStreamReader(gzipStream))
                    }

                    override fun getContentLength() = -1
                }, response)
            }
            else ->
                jsonErrorResponse(res, HttpServletResponse.SC_BAD_REQUEST, "invalid_encoding",
                        "Content encoding $encoding unknown. Please use gzip or no encoding.")
        }
    }

    override fun destroy() {
        // nothing to destroy
    }
}
