package org.radarcns.gateway.filter

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.radarcns.gateway.util.ServletInputStreamWrapper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.concurrent.ThreadLocalRandom
import java.util.zip.GZIPOutputStream
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

class GzipUncompressTest {
    lateinit var filter: GzipUncompressFilter
    lateinit var request: HttpServletRequest
    lateinit var response: HttpServletResponse
    lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        filter = GzipUncompressFilter()
        val config = mock(FilterConfig::class.java)
        val context = mock(ServletContext::class.java)
        `when`(config.servletContext).thenReturn(context)
        `when`(context.log(anyString())).then { System.out.println(it) }
        filter.init(config)
        request = mock(HttpServletRequest::class.java)
        response = mock(HttpServletResponse::class.java)
        filterChain = mock(FilterChain::class.java)
    }

    @Test
    fun uncompress() {
        `when`(request.getHeader("Content-Encoding")).thenReturn("gzip")
        val bytes = ByteArray(100)
        val actual = ByteArray(120)
        ThreadLocalRandom.current().nextBytes(bytes)
        val byteOut = ByteArrayOutputStream()
        GZIPOutputStream(byteOut).use { gzOut -> gzOut.write(bytes) }
        val gzipIn = ByteArrayInputStream(byteOut.toByteArray())
        `when`(request.inputStream).thenReturn(ServletInputStreamWrapper(gzipIn))
        `when`(filterChain.doFilter(any(), any())).then { invocation ->
            val numRead = invocation.getArgument<HttpServletRequest>(0).inputStream.read(actual)
            assertEquals(100, numRead)
            assertArrayEquals(bytes, actual.sliceArray(0 .. 99))
        }
        filter.doFilter(request, response, filterChain)
        verify(filterChain, times(1))!!.doFilter(any(), any())
    }

    @Test
    fun nocompress() {
        `when`(request.getHeader("Content-Encoding")).thenReturn("identity")
        val bytes = ByteArray(100)
        val actual = ByteArray(120)
        ThreadLocalRandom.current().nextBytes(bytes)
        val bytesIn = ByteArrayInputStream(bytes)
        `when`(request.inputStream).thenReturn(ServletInputStreamWrapper(bytesIn))
        `when`(filterChain.doFilter(any(), any())).then { invocation ->
            val req : HttpServletRequest = invocation.getArgument(0)
            val numRead = req.inputStream.read(actual)
            assertEquals(100, numRead)
            assertArrayEquals(bytes, actual.sliceArray(0 .. 99))
        }
        filter.doFilter(request, response, filterChain)
        verify(filterChain, times(1))!!.doFilter(any(), any())
    }
}