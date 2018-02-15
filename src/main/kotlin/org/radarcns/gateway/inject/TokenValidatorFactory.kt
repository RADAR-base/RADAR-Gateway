package org.radarcns.gateway.inject

import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.config.YamlServerConfig
import org.radarcns.gateway.Config
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.URISyntaxException
import java.util.function.Supplier
import javax.ws.rs.core.Context

class TokenValidatorFactory: Supplier<TokenValidator> {

    @Context
    private lateinit var config: Config

    override fun get(): TokenValidator {
        val publicKey = try {
            URI("${config.managementPortalUrl}/oauth/token_key")
        } catch (e: URISyntaxException) {
            logger.info("Failed to load Management Portal URL ${config.managementPortalUrl}", e)
            null
        }

        return if (publicKey == null) TokenValidator() else {
            val cfg = YamlServerConfig()
            cfg.publicKeyEndpoint = publicKey
            cfg.resourceName = "res_gateway"
            TokenValidator(cfg)
        }
    }

    companion object {
        private val logger = LoggerFactory.getLogger(TokenValidatorFactory::class.java)
    }
}