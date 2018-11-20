package org.radarcns.gateway.inject

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import org.radarcns.auth.authorization.Permission.MEASUREMENT_CREATE
import org.radarcns.gateway.Config
import org.radarcns.gateway.auth.Auth
import org.radarcns.gateway.auth.AuthValidator
import org.radarcns.gateway.auth.JwtAuth
import java.lang.Exception
import java.lang.IllegalStateException
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyStore
import java.security.interfaces.ECPublicKey
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

class EcdsaJwtTokenValidator constructor(@Context private val config: Config) : AuthValidator {
    private val verifier: JWTVerifier
    init {
        val algorithm = try {
            val pkcs12Store = KeyStore.getInstance("pkcs12")
            val keyStorePath = Paths.get(config.jwtKeystorePath)
            pkcs12Store.load(Files.newInputStream(keyStorePath), config.jwtKeystorePassword?.toCharArray())
            val publicKey: ECPublicKey = pkcs12Store.getCertificate(config.jwtKeystoreAlias).publicKey as ECPublicKey
            Algorithm.ECDSA256(publicKey, null)
        } catch (ex: Exception) {
            throw IllegalStateException("Failed to initialize JWT ECDSA public key", ex)
        }
        var jwtBuilder = JWT.require(algorithm)
                .withAudience(config.jwtResourceName)
                .withArrayClaim("scope", MEASUREMENT_CREATE.scopeName())

        config.jwtIssuer?.let {
            jwtBuilder.withIssuer(it)
        }
        verifier = jwtBuilder.build()
    }

    override fun verify(request: ContainerRequestContext): Auth? {
        val token = getToken(request) ?: return null
        val project = request.getHeaderString("RADAR-Project")
        return JwtAuth(project, verifier.verify(token))
    }
}
