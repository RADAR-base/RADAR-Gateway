package org.radarcns.gateway.filter

import com.auth0.jwt.interfaces.DecodedJWT
import java.io.IOException
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale
import javax.servlet.Filter
import javax.servlet.FilterChain
import javax.servlet.FilterConfig
import javax.servlet.ServletContext
import javax.servlet.ServletException
import javax.servlet.ServletRequest
import javax.servlet.ServletResponse
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import org.apache.http.HttpHeaders
import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.authorization.RadarAuthorization
import org.radarcns.auth.config.ServerConfig
import org.radarcns.auth.config.YamlServerConfig
import org.radarcns.auth.exception.NotAuthorizedException
import org.radarcns.auth.exception.TokenValidationException

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
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            res.setHeader("WWW-Authenticate", "Bearer")
            return
        }

        try {
            val jwt = getValidator(context).validateAccessToken(token)
            RadarAuthorization.checkPermission(jwt, Permission.MEASUREMENT_CREATE)
            request.setAttribute("jwt", jwt)
            chain.doFilter(request, response)
        } catch (ex: TokenValidationException) {
            context.log("Failed to process token", ex)
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            res.setHeader("WWW-Authenticate", "Bearer")
        } catch (ex: NotAuthorizedException) {
            context.log("Failed to process token", ex)
            res.setStatus(HttpServletResponse.SC_UNAUTHORIZED)
            res.setHeader("WWW-Authenticate", "Bearer")
        }

    }

    override fun destroy() {
        // nothing to destroy
    }

    private fun getToken(request: ServletRequest): String? {
        val req = request as HttpServletRequest
        val authorizationHeader = req.getHeader(HttpHeaders.AUTHORIZATION)

        // Check if the HTTP Authorization header is present and formatted correctly
        if (authorizationHeader == null || !authorizationHeader.toLowerCase(Locale.US).startsWith("bearer ")) {
            this.context.log("No authorization header provided in the request")
            return null
        }

        // Extract the token from the HTTP Authorization header
        return authorizationHeader.substring("Bearer".length).trim { it <= ' ' }
    }

    companion object {

        private var validator: TokenValidator? = null

        @Synchronized private fun getValidator(context: ServletContext): TokenValidator {
            if (validator == null) {
                var config: ServerConfig? = null
                val mpUrlString = context.getInitParameter("managementPortalUrl")
                if (mpUrlString != null) {
                    try {
                        val cfg = YamlServerConfig()
                        cfg.publicKeyEndpoint = URI("$mpUrlString/oauth/token_key")
                        config = cfg
                    } catch (e: URISyntaxException) {
                        context.log("Failed to load Management Portal URL $mpUrlString", e)
                    }
                }

                validator = if (config == null) TokenValidator() else TokenValidator(config)
            }
            return validator!!
        }
    }
}
