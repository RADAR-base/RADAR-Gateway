package org.radarbase.gateway.io

import org.radarbase.auth.jersey.exception.HttpApplicationException
import org.radarbase.gateway.inject.ProcessAvro
import org.radarbase.io.lzfse.LZFSEInputStream
import java.util.*
import java.util.zip.GZIPInputStream
import javax.annotation.Priority
import javax.inject.Singleton
import javax.ws.rs.Priorities
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider
import javax.ws.rs.ext.ReaderInterceptor
import javax.ws.rs.ext.ReaderInterceptorContext

/**
 * Decompresses GZIP-compressed data. Uncompressed data is not modified. The data is decompressed
 * lazily, when request data read by subsequent filters and servlets.
 */
@Provider
@ProcessAvro
@Singleton
@Priority(Priorities.ENTITY_CODER)
class DecompressInterceptor : ReaderInterceptor {
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        val encoding = context.headers?.getFirst("Content-Encoding")?.toLowerCase(Locale.US)
        context.inputStream = when(encoding) {
            "gzip" -> GZIPInputStream(context.inputStream)
            "lzfse" -> LZFSEInputStream(context.inputStream)
            "identity", null -> context.inputStream
            else -> throw HttpApplicationException(Response.Status.BAD_REQUEST, "unknown_encoding",
                            "Encoding $encoding unknown." +
                                    " This server supports gzip, lzfse and identity compression")
        }
        return context.proceed()
    }
}
