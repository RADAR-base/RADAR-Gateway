package org.radarcns.gateway.filter

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers
import org.mockito.Mockito.*
import org.radarcns.auth.token.JwtRadarToken.*
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.kafka.AvroAuth
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


class ManagementPortalAuthenticationFilterTest {
    lateinit var server: MockWebServer
    lateinit var filter: ManagementPortalAuthenticationFilter
    lateinit var request: HttpServletRequest
    lateinit var response: HttpServletResponse
    lateinit var filterChain: FilterChain

    @BeforeEach
    fun setUp() {
        server = MockWebServer()
        server.start(34101)

        filter = ManagementPortalAuthenticationFilter()
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
                .withArrayClaim(SOURCES_CLAIM, arrayOf("a", "b"))
                .withExpiresAt(Date.from(Instant.now().plus(Duration.ofSeconds(60))))
                .withArrayClaim(SCOPE_CLAIM, arrayOf("MEASUREMENT.CREATE"))
                .withArrayClaim(ROLES_CLAIM, arrayOf("test:ROLE_ADMIN", "p:ROLE_PARTICIPANT"))
                .withArrayClaim(AUTHORITIES_CLAIM, arrayOf("res_pRMT"))
                .withClaim(GRANT_TYPE_CLAIM, "code")
                .sign(algorithm)

        `when`(request.getHeader(eq("Authorization"))).thenReturn("Bearer " + token)
        `when`(request.setAttribute(eq("token"), any())).then { invocation ->
            val jwt = invocation.getArgument<RadarToken>(1)!!
            assertEquals(listOf("a", "b"), jwt.sources)
            assertEquals("user1", jwt.subject)
            val expectedRoles = mapOf(
                    Pair("test", listOf("ROLE_ADMIN")),
                    Pair("p", listOf("ROLE_PARTICIPANT")))
            assertEquals(expectedRoles, jwt.roles)

            val auth = AvroAuth(jwt)
            assertEquals(setOf("p"), auth.projectIds)
            assertEquals(setOf("a", "b"), auth.sourceIds)
            assertEquals("user1", auth.userId)
            assertEquals("p", auth.defaultProject)
        }

        filter.doFilter(request, response, filterChain)
        @Suppress("UsePropertyAccessSyntax")
        verify(response, times(0)).setStatus(anyInt())
        verify(request, times(1)).setAttribute(eq("token"), any())
    }

}