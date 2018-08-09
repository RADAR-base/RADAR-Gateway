package org.radarcns.gateway.inject

import org.radarcns.auth.authentication.TokenValidator
import org.radarcns.auth.config.YamlServerConfig
import org.radarcns.gateway.Config
import java.net.URI
import java.util.function.Supplier
import javax.ws.rs.core.Context

class TokenValidatorFactory: Supplier<TokenValidator> {
    @Context
    private lateinit var config: Config

    override fun get(): TokenValidator {
        return try {
            TokenValidator()
        } catch (e: RuntimeException) {
            val cfg = YamlServerConfig()
            cfg.publicKeyEndpoints = listOf(URI("${config.managementPortalUrl}/oauth/token_key"))
            cfg.resourceName = "res_gateway"
            TokenValidator(cfg)
        }
    }
}