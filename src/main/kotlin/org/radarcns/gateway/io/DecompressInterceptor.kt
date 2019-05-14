package org.radarcns.gateway.io

import org.radarbase.io.lzfse.LZFSEInputStream
import org.radarcns.gateway.Config
import org.radarcns.gateway.inject.ProcessAvro
import java.util.zip.GZIPInputStream
import javax.annotation.Priority
import javax.inject.Singleton
import javax.ws.rs.Priorities
import javax.ws.rs.core.Context
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
class DecompressInterceptor(@Context private val config: Config) : ReaderInterceptor {
    override fun aroundReadFrom(context: ReaderInterceptorContext): Any {
        val encoding = context.headers?.getFirst("Content-Encoding")?.toLowerCase()
        if (encoding == "gzip") {
            context.inputStream = GZIPInputStream(context.inputStream)
        } else if (encoding == "lzfse") {
            context.inputStream = LZFSEInputStream(context.inputStream)
        }
        context.inputStream = LimitedInputStream(context.inputStream, config.maxRequestSize)
        return context.proceed()
    }
}
