package org.radarbase.gateway

import org.radarbase.gateway.inject.ManagementPortalEnhancerFactory
import org.radarbase.jersey.config.EnhancerFactory
import java.net.URI

data class Config(
        val resourceConfig: Class<out EnhancerFactory> = ManagementPortalEnhancerFactory::class.java,
        val auth: AuthConfig = AuthConfig(),
        val kafka: KafkaConfig = KafkaConfig(),
        val server: GatewayServerConfig = GatewayServerConfig())

data class GatewayServerConfig(
        val baseUri: URI = URI.create("http://0.0.0.0:8090/radar-gateway/"),
        val maxRequests: Int = 200,
        val maxRequestSize: Long = 24*1024*1024,
        val isJmxEnabled: Boolean = true)

data class KafkaConfig(
        val poolSize: Int = 20,
        val maxProducers: Int = 200,
        val producer: Map<String, String> = mapOf(),
        val admin: Map<String, String> = mapOf(),
        val serialization: Map<String, String> = mapOf())

data class AuthConfig(
        val resourceName: String = "res_gateway",
        val issuer: String? = null,
        val keyStore: KeyStoreConfig = KeyStoreConfig(),
        val publicKeys: KeyConfig = KeyConfig(),
        val managementPortalUrl: String? = "http://managementportal-app:8080/managementportal/")

data class KeyStoreConfig(
        val path: String? = null,
        val alias: String? = null,
        val password: String? = null)

data class KeyConfig(
        val ecdsa: List<String>? = null,
        val rsa: List<String>? = null)
