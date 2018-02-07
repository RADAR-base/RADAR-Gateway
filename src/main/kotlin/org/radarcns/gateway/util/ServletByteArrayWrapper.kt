package org.radarcns.gateway.util

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.util.function.Supplier
import javax.servlet.ReadListener
import javax.servlet.ServletInputStream

/**
 * ServletInputStream wrapper for an InputStream.
 */
class ServletByteArrayWrapper @Throws(IOException::class)
constructor(private val stream: ByteArrayInputStream) : ServletInputStream() {
    private var listener: ReadListener? = null

    @Throws(IOException::class)
    override fun read() = process { stream.read() }

    @Throws(IOException::class)
    override fun read(buf: ByteArray, off: Int, len: Int) = process { stream.read(buf, off, len) }

    @Throws(IOException::class)
    override fun skip(off: Long) = process { stream.skip(off) }

    override fun mark(readLimit: Int) = stream.mark(readLimit)

    override fun markSupported() = stream.markSupported()

    @Throws(IOException::class)
    override fun reset() = process { stream.reset() }

    @Throws(IOException::class)
    override fun close() = stream.close()

    @Throws(IOException::class)
    override fun available() = stream.available()

    @Throws(IOException::class)
    override fun isReady(): Boolean = available() > 0

    @Throws(IOException::class)
    override fun isFinished(): Boolean = available() == 0

    @Throws(IOException::class)
    override fun setReadListener(readListener: ReadListener?) {
        listener = readListener
        if (isReady) {
            listener?.onDataAvailable()
        }
    }

    private fun <T> process(proc: () -> T): T {
        val wasReady = isReady
        val ret = proc()
        if (wasReady && !isReady) {
            listener?.onAllDataRead()
        } else if (!wasReady && isReady) {
            listener?.onDataAvailable()
        }
        return ret
    }
}
