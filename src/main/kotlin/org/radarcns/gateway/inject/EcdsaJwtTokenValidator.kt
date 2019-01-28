package org.radarcns.gateway.inject

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTVerifier
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import org.bouncycastle.util.io.pem.PemReader
import org.radarcns.auth.authorization.Permission.MEASUREMENT_CREATE
import org.radarcns.auth.exception.ConfigurationException
import org.radarcns.gateway.Config
import org.radarcns.gateway.auth.Auth
import org.radarcns.gateway.auth.AuthValidator
import org.radarcns.gateway.auth.JwtAuth
import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Paths
import java.security.KeyFactory
import java.security.KeyStore
import java.security.PublicKey
import java.security.interfaces.ECPublicKey
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import javax.ws.rs.container.ContainerRequestContext
import javax.ws.rs.core.Context

class EcdsaJwtTokenValidator constructor(@Context private val config: Config) : AuthValidator {
    private val verifiers: List<JWTVerifier>
    init {

        var algorithms = mutableListOf<Algorithm>()

        config.jwtECPublicKeys?.let { keys ->
            algorithms.addAll(keys.map { Algorithm.ECDSA256(parseKey(it, "EC") as ECPublicKey, null) })
        }
        config.jwtRSAPublicKeys?.let { keys ->
            algorithms.addAll(keys.map { Algorithm.RSA256(parseKey(it, "RSA") as RSAPublicKey, null) })
        }

        config.jwtKeystorePath?.let { keyStorePathString ->
            algorithms.add(try {
                val pkcs12Store = KeyStore.getInstance("pkcs12")
                val keyStorePath = Paths.get(keyStorePathString)
                pkcs12Store.load(Files.newInputStream(keyStorePath), config.jwtKeystorePassword?.toCharArray())
                val publicKey: ECPublicKey = pkcs12Store.getCertificate(config.jwtKeystoreAlias).publicKey as ECPublicKey
                Algorithm.ECDSA256(publicKey, null)
            } catch (ex: Exception) {
                throw IllegalStateException("Failed to initialize JWT ECDSA public key", ex)
            })
        }

        if (algorithms.isEmpty()) {
            throw ConfigurationException("No verification algorithms given")
        }

        verifiers = algorithms.map { algorithm ->
            val builder = JWT.require(algorithm)
                    .withAudience(config.jwtResourceName)
                    .withArrayClaim("scope", MEASUREMENT_CREATE.scopeName())
            config.jwtIssuer?.let {
                builder.withIssuer(it)
            }
            builder.build()
        }
    }

    private fun parseKey(publicKey: String, algorithm: String): PublicKey {
        var trimmedPublicKey = publicKey.trim()
        if (!trimmedPublicKey.contains("-----BEGIN")) {
            trimmedPublicKey = "-----BEGIN PUBLIC KEY-----\n$trimmedPublicKey\n-----END PUBLIC KEY-----"
        }
        try {
            val keyBytes = PemReader(StringReader(trimmedPublicKey)).use { pemReader ->
                pemReader.readPemObject().content
            }
            val spec = X509EncodedKeySpec(keyBytes)
            val kf = KeyFactory.getInstance(algorithm)
            return kf.generatePublic(spec)
        } catch (ex: Exception) {
            throw ConfigurationException(ex)
        }
    }

    override fun verify(request: ContainerRequestContext): Auth? {
        val token = getToken(request) ?: return null
        val project = request.getHeaderString("RADAR-Project")

        for (verifier in verifiers) {
            try {
                return JwtAuth(project, verifier.verify(token))
            } catch (ex: JWTVerificationException) {
                // not the right verifier
            }
        }
        return null
    }
}
