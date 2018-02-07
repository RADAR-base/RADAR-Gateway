package org.radarcns.gateway.util

import java.io.IOException
import java.io.InputStream
import javax.servlet.ReadListener
import javax.servlet.ServletInputStream

/**
 * ServletInputStream wrapper for an InputStream.
 */
class ServletInputStreamWrapper @Throws(IOException::class)
constructor(private val stream: InputStream, private val baseStream: ServletInputStream) : ServletInputStream() {
    @Throws(IOException::class)
    override fun read() = stream.read()

    @Throws(IOException::class)
    override fun read(buf: ByteArray, off: Int, len: Int) = stream.read(buf, off, len)

    @Throws(IOException::class)
    override fun skip(off: Long) = stream.skip(off)

    override fun mark(readLimit: Int) = stream.mark(readLimit)

    override fun markSupported() = stream.markSupported()

    @Throws(IOException::class)
    override fun reset() = stream.reset()

    @Throws(IOException::class)
    override fun close() = stream.close()

    @Throws(IOException::class)
    override fun available() = stream.available()

    override fun isReady() = baseStream.isReady

    override fun isFinished() = baseStream.isFinished

    override fun setReadListener(readListener: ReadListener?) = baseStream.setReadListener(readListener)
}
