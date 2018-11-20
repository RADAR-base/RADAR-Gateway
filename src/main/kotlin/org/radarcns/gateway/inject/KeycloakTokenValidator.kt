package org.radarcns.gateway.inject

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.radarcns.gateway.Config
import org.radarcns.gateway.auth.Auth
import org.radarcns.gateway.auth.AuthValidator
import org.radarcns.gateway.auth.AuthenticationFilter
import org.radarcns.gateway.auth.KeycloakAuth
import org.slf4j.LoggerFactory
import java.lang.Exception
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

class KeycloakTokenValidator constructor(@Context private val config: Config) : AuthValidator {
    private val verifier: JWTVerifier
    init {
        val algorithm = try {
            val pkcs12Store = KeyStore.getInstance("pkcs12")
            val keyStorePath = Paths.get(config.keycloakKeystorePath)
            pkcs12Store.load(Files.newInputStream(keyStorePath), config.keycloakKeystorePassword?.toCharArray())
            val publicKey: ECPublicKey = pkcs12Store.getCertificate(config.keycloakKeystoreAlias).publicKey as ECPublicKey
            Algorithm.ECDSA256(publicKey, null)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to initialize keycloak key", ex)
        }
        var jwtBuilder = JWT.require(algorithm)
                .withAudience(config.resourceName)
                .withArrayClaim("scope", "MEASUREMENT.CREATE")

        config.issuer?.let {
            jwtBuilder.withIssuer(it)
        }
        verifier = jwtBuilder.build()
    }

    override fun verify(request: ContainerRequestContext): Auth? {
        val token = getToken(request) ?: return null
        val project = request.getHeaderString("RADAR-Project")
        return KeycloakAuth(project, verifier.verify(token))
    }
}
