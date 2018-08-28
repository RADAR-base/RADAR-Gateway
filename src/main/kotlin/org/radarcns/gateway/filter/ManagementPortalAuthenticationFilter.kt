package org.radarcns.gateway.filter

import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.exception.TokenValidationException
import org.radarcns.gateway.auth.Authenticated
import org.radarcns.gateway.auth.RadarSecurityContext
import org.slf4j.LoggerFactory
import javax.annotation.Priority
import javax.ws.rs.Priorities
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.container.ContainerRequestFilter
import javax.ws.rs.core.Context
import javax.ws.rs.core.Response
import javax.ws.rs.ext.Provider

/**
 * Authenticates user by a JWT in the bearer signed by the Management Portal.
 */
@Provider
@Authenticated
@Priority(Priorities.AUTHENTICATION)
class ManagementPortalAuthenticationFilter : ContainerRequestFilter {

    @Context
    private lateinit var validator: TokenValidator

    override fun filter(requestContext: ContainerRequestContext) {
        val token = getToken(requestContext)

        if (token == null) {
            logger.warn("[401] {}: No token bearer header provided in the request",
                    requestContext.uriInfo.path)
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .header("WWW-Authenticate", "Bearer")
                            .build())
            return
        }

        val radarToken = try {
            validator.validateAccessToken(token)
        } catch (ex: TokenValidationException) {
            logger.warn("[401] {}: {}", requestContext.uriInfo.path, ex.message, ex)
            requestContext.abortWith(
                    Response.status(Response.Status.UNAUTHORIZED)
                            .header("WWW-Authenticate",
                                    BEARER_REALM
                                            + " error=\"invalid_token\""
                                            + " error_description=\"${ex.message}\"")
                            .build())
            null
        } ?: return

        if (!radarToken.hasPermission(Permission.MEASUREMENT_CREATE)) {
            val message = "MEASUREMENT.CREATE permission not given"
            logger.warn("[403] {}: {}", requestContext.uriInfo.path, message)
            requestContext.abortWith(
                    Response.status(Response.Status.FORBIDDEN)
                                .header("WWW-Authenticate", getInvalidScopeChallenge(message))
                            .build())
            return
        }

        requestContext.securityContext = RadarSecurityContext(radarToken)
    }

    private fun getToken(request: ContainerRequestContext): String? {
        val authorizationHeader = request.getHeaderString("Authorization")

        // Check if the HTTP Authorization header is present and formatted correctly
        if (authorizationHeader == null
                || !authorizationHeader.startsWith(BEARER, ignoreCase = true)) {
            logger.info("No authorization header provided in the request")
            return null
        }

        // Extract the token from the HTTP Authorization header
        return authorizationHeader.substring(BEARER.length).trim { it <= ' ' }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(ManagementPortalAuthenticationFilter::class.java)

        const val BEARER_REALM: String = "Bearer realm=\"Kafka REST Proxy\""
        const val BEARER = "Bearer "

        fun getInvalidScopeChallenge(message: String) = BEARER_REALM +
                " error=\"insufficient_scope\"" +
                " error_description=\"$message\"" +
                " scope=\"${Permission.MEASUREMENT_CREATE}\""
    }
}
