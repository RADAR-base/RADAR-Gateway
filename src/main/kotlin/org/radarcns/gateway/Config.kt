package org.radarcns.gateway

import org.radarcns.gateway.inject.ManagementPortalGatewayResources
import java.net.URI

class Config {
    var baseUri: URI = URI.create("http://0.0.0.0:8080/radar-gateway/")
    var restProxyUrl: String = "http://rest-proxy-1:8082"
    var schemaRegistryUrl: String = "http://schema-registry-1:8081"
    var managementPortalUrl: String = "http://managementportal-app:8080/managementportal/"
    var resourceConfig: Class<*> = ManagementPortalGatewayResources::class.java
    var keycloakKeystorePath: String = "/etc/radar-gateway/keystore.p12"
    var keycloakKeystoreAlias: String = "ecdsa2"
    var keycloakKeystorePassword: String? = null
}
