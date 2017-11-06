package org.radarcns.gateway.util

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*;
import java.io.ByteArrayInputStream
import java.util.concurrent.ThreadLocalRandom

class ServletInputStreamWrapperTest {

    @Test
    fun matchInputStream() {
        val bytes = ByteArray(100)
        ThreadLocalRandom.current().nextBytes(bytes)
        ByteArrayInputStream(bytes).use { input ->
            val actual = ServletInputStreamWrapper(input).readBytes()
            assertArrayEquals(bytes, actual)
        }
    }

    @Test
    fun readLine() {
        ServletInputStreamWrapper("hello\nyou\n".byteInputStream()).use {input ->
            val buf = ByteArray(10)
            var numRead = input.readLine(buf, 0, buf.size)
            assertEquals(6, numRead)
            assertEquals("hello\n", String(buf.sliceArray(0 .. 5)))
            numRead = input.readLine(buf, 0, buf.size)
            assertEquals(4, numRead)
            assertEquals("you\n", String(buf.sliceArray(0 .. 3)))
        }
    }
}