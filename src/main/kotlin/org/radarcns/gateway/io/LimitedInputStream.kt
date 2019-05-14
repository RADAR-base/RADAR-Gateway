package org.radarcns.gateway.io

import org.radarcns.gateway.exception.RequestEntityTooLarge
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

internal class LimitedInputStream(private val subStream: InputStream, private val limit: Long): InputStream() {
    private var count: Long = 0

    override fun available() = min(subStream.available(), (limit - count).toInt())

    override fun read(): Int {
        return subStream.read().also {
            if (it != -1) count++
            if (count > limit) throw RequestEntityTooLarge("Stream size exceeds limit $limit")
        }
    }

    override fun read(b: ByteArray?, off: Int, len: Int): Int {
        if (len == 0) return 0
        return subStream.read(b, off, min(max(limit - count, 1L), len.toLong()).toInt()).also {
            if (it != -1) count += it
            if (count > limit) throw RequestEntityTooLarge("Stream size exceeds limit $limit")
        }
    }

    override fun close() = subStream.close()
}
