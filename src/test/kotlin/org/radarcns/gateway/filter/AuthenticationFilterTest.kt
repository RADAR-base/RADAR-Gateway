package org.radarcns.gateway.filter

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.interfaces.DecodedJWT
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import java.security.KeyStore
import java.security.PrivateKey
import java.security.interfaces.RSAPrivateKey
import java.security.interfaces.RSAPublicKey
import java.time.Duration
import java.time.Instant
import java.util.*
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletContext
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


class AuthenticationFilterTest {
    lateinit var server: MockWebServer
    lateinit var filter: AuthenticationFilter
    lateinit var request: HttpServletRequest
    lateinit var response: HttpServletResponse
    lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start(34101)

        filter = AuthenticationFilter()
        val config = mock(FilterConfig::class.java)
        val context = mock(ServletContext::class.java)
        `when`(config.servletContext).thenReturn(context)
        `when`(context.log(ArgumentMatchers.anyString())).then { System.out.println(it) }
        filter.init(config)
        request = mock(HttpServletRequest::class.java)
        response = mock(HttpServletResponse::class.java)
        filterChain = mock(FilterChain::class.java)

    }

    @AfterEach
    fun tearDown() {
        server.close()
    }

    @Test
    fun authenticate() {
        val keyStore = KeyStore.getInstance("JKS")
        this.javaClass.getResourceAsStream("/keystore.jks").use {
            assertNotNull(it)
            keyStore.load(it, "spassword".toCharArray())
        }
        val privateKey = keyStore.getKey("test", "password".toCharArray()) as PrivateKey
        // Get public key
        val publicKey = keyStore.getCertificate("test").publicKey

        server.enqueue(MockResponse().setBody("{" +
                "\"alg\":\"SHA256withRSA\"," +
                "\"value\":\"-----BEGIN PUBLIC KEY-----\\n"
                + Base64.getEncoder().encodeToString(publicKey.encoded)
                + "\\n-----END PUBLIC KEY-----\"}"))
        // fail on futher download attempts
        server.enqueue(MockResponse().setResponseCode(500))

        val algorithm = Algorithm.RSA256(publicKey as RSAPublicKey?, privateKey as RSAPrivateKey?)
        val token: String = JWT.create()
                .withIssuedAt(Date())
                .withAudience("res_ManagementPortal")
                .withSubject("user1")
                .withArrayClaim("sources", arrayOf("a", "b"))
                .withExpiresAt(Date.from(Instant.now().plus(Duration.ofSeconds(60))))
                .sign(algorithm)

        `when`(request.getHeader(eq("Authorization"))).thenReturn("Bearer " + token)
        `when`(request.setAttribute(eq("jwt"), any())).then { invocation ->
            val jwt = invocation.getArgument<DecodedJWT>(1)!!
            assertEquals(listOf("a", "b"),
                    jwt.getClaim("sources").asList(String::class.java))
            assertEquals("user1", jwt.subject)
        }

        filter.doFilter(request, response, filterChain)
        verify(response, times(0)).setStatus(anyInt())
        verify(request, times(1)).setAttribute(eq("jwt"), any())
    }

}