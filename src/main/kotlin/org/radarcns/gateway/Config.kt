package org.radarcns.gateway

import org.radarcns.gateway.inject.ManagementPortalGatewayResources
import java.net.URI

class Config {
    var baseUri: URI = URI.create("http://0.0.0.0:8090/radar-gateway/")
    var restProxyUrl: String = "http://rest-proxy-1:8082"
    var schemaRegistryUrl: String = "http://schema-registry-1:8081"
    var managementPortalUrl: String = "http://managementportal-app:8080/managementportal/"
    var resourceConfig: Class<*> = ManagementPortalGatewayResources::class.java
    var jwtKeystorePath: String? = null
    var jwtKeystoreAlias: String? = null
    var jwtKeystorePassword: String? = null
    var jwtECPublicKeys: List<String>? = null
    var jwtRSAPublicKeys: List<String>? = null
    var jwtIssuer: String? = null
    var jwtResourceName: String = "res_gateway"
    var maxRequestSize: Long = 24*1024*1024
    var maxRequests: Int = 200
}
