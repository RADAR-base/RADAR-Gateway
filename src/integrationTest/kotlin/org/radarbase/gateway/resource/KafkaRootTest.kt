package org.radarbase.gateway.resource

import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.gateway.AuthConfig
import org.radarbase.gateway.Config
import org.radarbase.gateway.GatewayServerConfig
import org.radarbase.gateway.KafkaConfig
import org.radarbase.gateway.inject.ManagementPortalEnhancerFactory
import org.radarbase.gateway.resource.KafkaTopicsTest.Companion.call
import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.config.ConfigLoader
import java.net.URI
import javax.ws.rs.core.Response.Status

class KafkaRootTest {
    private lateinit var server: GrizzlyServer

    @BeforeEach
    fun setUp() {
        val config = baseConfig

        val resourceConfig = ConfigLoader.loadResources(ManagementPortalEnhancerFactory::class.java, config)
        server = GrizzlyServer(config.server.baseUri, resourceConfig)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun queryRoot() {
        httpClient.call(Status.OK) {
            url(BASE_URI)
        }
        httpClient.call(Status.OK) {
            url(BASE_URI)
            head()
        }
    }

    companion object {
        private lateinit var httpClient: OkHttpClient

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
            httpClient = OkHttpClient()
        }

        const val BASE_URI = "http://localhost:8090/radar-gateway"

        val baseConfig: Config
            get() = Config(
                    kafka = KafkaConfig(
                            producer = mapOf(
                                    "bootstrap.servers" to "kafka-1:9092",
                                    "max.block.ms" to "6000",
                                    "timeout.ms" to "3000",
                                    "linger.ms" to "10",
                                    "retries" to "5",
                                    "acks" to "all",
                                    "delivery.timeout.ms" to "6000",
                                    "request.timeout.ms" to "3000"),
                            admin = mapOf(
                                    "bootstrap.servers" to "kafka-1:9092",
                                    "request.timeout.ms" to "3000",
                                    "retries" to "5",
                                    "default.api.timeout.ms" to "6000"),
                            schemaRegistryUrl = "http://localhost:8081"),
                    auth = AuthConfig(
                            managementPortalUrl = "http://localhost:8080"),
                    server = GatewayServerConfig(
                            baseUri = URI.create(BASE_URI)))
    }
}
