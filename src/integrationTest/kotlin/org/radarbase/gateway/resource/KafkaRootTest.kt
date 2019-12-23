package org.radarbase.gateway.resource

import okhttp3.OkHttpClient
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.gateway.Config
import org.radarbase.gateway.inject.ManagementPortalEnhancerFactory
import org.radarbase.gateway.resource.KafkaTopicsTest.Companion.call
import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.config.ConfigLoader
import java.net.URI
import javax.ws.rs.core.Response.Status

class KafkaRootTest {
    private lateinit var baseUri: String
    private lateinit var server: GrizzlyServer

    @BeforeEach
    fun setUp() {
        baseUri = "http://localhost:8090/radar-gateway"
        val config = Config()
        config.restProxyUrl = "http://localhost:8082"
        config.schemaRegistryUrl = "http://localhost:8081"
        config.baseUri = URI.create(baseUri)

        val resourceConfig = ConfigLoader.loadResources(ManagementPortalEnhancerFactory::class.java, config)
        server = GrizzlyServer(config.baseUri, resourceConfig)
        server.start()
    }

    @AfterEach
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun queryRoot() {
        httpClient.call(Status.OK) {
            url(baseUri)
        }
        httpClient.call(Status.OK) {
            url(baseUri)
            head()
        }
        httpClient.call(Status.NO_CONTENT) {
            url(baseUri)
            method("OPTIONS", null)
        }
    }

    companion object {
        private lateinit var httpClient: OkHttpClient

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
            httpClient = OkHttpClient()
        }
    }
}
