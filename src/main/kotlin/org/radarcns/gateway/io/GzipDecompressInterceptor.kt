package org.radarcns.gateway.io

import org.radarbase.io.lzfse.LZFSEInputStream
import org.radarcns.gateway.Config
import org.radarcns.gateway.inject.ProcessAvro
import java.io.IOException
import java.io.InputStream
import java.util.zip.GZIPInputStream
import javax.annotation.Priority
import javax.inject.Singleton
import javax.ws.rs.Priorities
import javax.ws.rs.core.Context
import javax.ws.rs.ext.Provider
import javax.ws.rs.ext.ReaderInterceptor
import javax.ws.rs.ext.ReaderInterceptorContext
import kotlin.math.min

/**
 * Decompresses GZIP-compressed data. Uncompressed data is not modified. The data is decompressed
 * lazily, when request data read by subsequent filters and servlets.
 */
@Provider
@ProcessAvro
@Singleton
@Priority(Priorities.ENTITY_CODER)
class GzipDecompressInterceptor(@Context private val config: Config) : ReaderInterceptor {
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

    class LimitedInputStream(private val subStream: InputStream, private val limit: Long): InputStream() {
        private var count: Long = 0
        override fun available() = min(subStream.available(), (limit - count).toInt())

        override fun read(): Int {
            return subStream.read().also {
                if (it != -1) count++
                if (count > limit) throw IOException("Stream size exceeds limit $limit")
            }
        }

        override fun read(b: ByteArray?, off: Int, len: Int): Int {
            if (len > 0 && count == limit) throw IOException("Stream size exceeds limit $limit")
            return subStream.read(b, off, min((limit - count).toInt(), len)).also {
                if (it != -1) count += it
                if (count > limit) throw IOException("Stream size exceeds limit $limit")
            }
        }

        override fun close() {
            subStream.close()
        }
    }
}
