package org.radarcns.gateway.filter

import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.authorization.RadarAuthorization
import org.radarcns.auth.config.YamlServerConfig
import org.radarcns.auth.exception.NotAuthorizedException
import org.radarcns.auth.exception.TokenValidationException
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.*
import javax.servlet.*
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse

/**
 * Authenticates user by a JWT in the bearer signed by the Management Portal.
 */
class ManagementPortalAuthenticationFilter : Filter {
    private lateinit var context: ServletContext

    @Throws(ServletException::class)
    override fun init(filterConfig: FilterConfig) {
        this.context = filterConfig.servletContext
        this.context.log("Authentication filter initialized")
    }

    @Throws(IOException::class, ServletException::class)
    override fun doFilter(request: ServletRequest, response: ServletResponse, chain: FilterChain) {
        val token = getToken(request)
        val res = response as HttpServletResponse
        if (token == null) {
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            res.setHeader("WWW-Authenticate", BEARER_REALM)
            return
        }

        val jwt = try {
            getValidator(context).validateAccessToken(token)
        } catch (ex: TokenValidationException) {
            context.log("Failed to process token", ex)
            res.status = HttpServletResponse.SC_UNAUTHORIZED
            res.setHeader("WWW-Authenticate",
                    "$BEARER_REALM error=\"invalid_token\" error_description=\"${ex.message}\"")
            null
        } ?: return

        if (!jwt.hasPermission(Permission.MEASUREMENT_CREATE)) {
            context.log("Insufficient scope")
            res.status = HttpServletResponse.SC_FORBIDDEN
            res.setHeader("WWW-Authenticate", "$BEARER_REALM error=\"insufficient_scope\""
                    + " error_description=\"MEASUREMENT.CREATE permission not given.\""
                    + " scope=\"${Permission.MEASUREMENT_CREATE}\"")
            return
        }

        chain.doFilter(request, response)
    }

    override fun destroy() {
        // nothing to destroy
    }

    private fun getToken(request: ServletRequest): String? {
        val req = request as HttpServletRequest
        val authorizationHeader = req.getHeader("Authorization")

        // Check if the HTTP Authorization header is present and formatted correctly
        if (authorizationHeader == null || !authorizationHeader.toLowerCase(Locale.US).startsWith("bearer ")) {
            this.context.log("No authorization header provided in the request")
            return null
        }

        // Extract the token from the HTTP Authorization header
        return authorizationHeader.substring("Bearer".length).trim { it <= ' ' }
    }

    companion object {
        const val BEARER_REALM: String = "Bearer realm=\"Kafka REST Proxy\""
        private var validator: TokenValidator? = null

        @Synchronized private fun getValidator(context: ServletContext): TokenValidator {
            if (validator == null) {
                val mpUrlString = context.getInitParameter("managementPortalUrl")
                val publicKey = if (mpUrlString != null) {
                    try {
                        URI("$mpUrlString/oauth/token_key")
                    } catch (e: URISyntaxException) {
                        context.log("Failed to load Management Portal URL $mpUrlString", e)
                        null
                    }
                } else null

                validator = if (publicKey == null) TokenValidator() else {
                    val cfg = YamlServerConfig()
                    cfg.publicKeyEndpoint = publicKey
                    TokenValidator(cfg)
                }
            }
            return validator!!
        }
    }
}
