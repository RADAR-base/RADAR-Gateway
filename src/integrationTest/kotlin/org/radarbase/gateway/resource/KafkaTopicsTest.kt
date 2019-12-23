package org.radarbase.gateway.resource

import com.fasterxml.jackson.databind.JsonNode
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.config.ServerConfig
import org.radarbase.gateway.Config
import org.radarbase.gateway.inject.ManagementPortalEnhancerFactory
import org.radarbase.gateway.resource.KafkaTopics.Companion.ACCEPT_AVRO_V2_JSON
import org.radarbase.gateway.util.Json
import org.radarbase.jersey.GrizzlyServer
import org.radarbase.jersey.auth.filter.AuthenticationFilter.Companion.BEARER
import org.radarbase.jersey.config.ConfigLoader
import org.radarbase.producer.rest.RestClient
import org.radarbase.producer.rest.RestSender
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration
import java.net.URI
import java.net.URL
import javax.ws.rs.core.MediaType.APPLICATION_JSON
import javax.ws.rs.core.Response.Status

class KafkaTopicsTest {
    private lateinit var config: Config
    private lateinit var baseUri: String
    private lateinit var server: GrizzlyServer

    @BeforeEach
    fun setUp() {
        baseUri = "http://localhost:8090/radar-gateway"
        config = Config()
        config.managementPortalUrl = "http://localhost:8080"
        config.schemaRegistryUrl = "http://localhost:8081"
        config.restProxyUrl = "http://localhost:8082"
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
    fun testListTopics() {
        val clientToken = httpClient.call( Status.OK, "access_token") {
            url("${config.managementPortalUrl}/oauth/token")
            addHeader("Authorization", Credentials.basic(MP_CLIENT, ""))
            post(FormBody.Builder()
                    .add("username", ADMIN_USER)
                    .add("password", ADMIN_PASSWORD)
                    .add("grant_type", "password")
                    .build())
        }

        val tokenUrl = httpClient.call(Status.OK, "tokenUrl") {
            addHeader("Authorization", BEARER + clientToken)
            url(config.managementPortalUrl.toHttpUrl()
                    .newBuilder("api/oauth-clients/pair")!!
                    .addEncodedQueryParameter("clientId", REST_CLIENT)
                    .addEncodedQueryParameter("login", USER)
                    .build())
        }

        val refreshToken = httpClient.call(Status.OK, "refreshToken") {
            url(tokenUrl)
        }

        val accessToken = httpClient.call(Status.OK, "access_token") {
            url("${config.managementPortalUrl}/oauth/token")
            addHeader("Authorization", Credentials.basic(REST_CLIENT, ""))
            post(FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build())
        }

        val retriever = SchemaRetriever(ServerConfig(URL("http://localhost:8081/")), 10)

        val topic = AvroTopic("test",
                ObservationKey.getClassSchema(), PhoneAcceleration.getClassSchema(),
                ObservationKey::class.java, PhoneAcceleration::class.java)

        val time = System.currentTimeMillis() / 1000.0
        val key = ObservationKey(PROJECT, USER, SOURCE)
        val value = PhoneAcceleration(time, time, 0.1f, 0.1f, 0.1f)

        val restClient = RestClient.global()
                .server(ServerConfig(URL(config.restProxyUrl + "/")))
                .build()

        val sender = RestSender.Builder()
                .httpClient(restClient)
                .schemaRetriever(retriever)
                .build()

        sender.sender(topic).use {
            it.send(key, value)
        }

        val topicList = httpClient.call(Status.OK) {
            url(config.restProxyUrl + "/topics")
        }!!
        assertThat(topicList.isArray, `is`(true))
        assertThat(topicList.toList().map { it.asText() }, hasItems("_schemas", "test"))

        Thread.sleep(2000)

        sender.headers = Headers.Builder()
                .add("Authorization", BEARER + accessToken)
                .build()
        sender.setKafkaConfig(ServerConfig(config.baseUri.toURL()))

        sender.sender(topic).use {
            it.send(key, value)
        }

        val gatewayTopicList = httpClient.call(Status.OK) {
            url(config.restProxyUrl + "/topics")
            addHeader("Authorization", BEARER + accessToken)
        }

        assertThat(gatewayTopicList, `is`(topicList))

        httpClient.call(Status.NO_CONTENT) {
            url("$baseUri/topics")
            method("OPTIONS", null)
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.NO_CONTENT) {
            url("$baseUri/topics/test")
            method("OPTIONS", null)
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.OK) {
            url(config.restProxyUrl + "/topics")
            head()
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.UNAUTHORIZED) {
            url("$baseUri/topics")
        }

        httpClient.call(Status.UNAUTHORIZED) {
            url("$baseUri/topics").head()
        }

        httpClient.call(Status.UNSUPPORTED_MEDIA_TYPE) {
            url("$baseUri/topics/test")
            post("{}".toRequestBody(JSON_TYPE))
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(422) {
            url("$baseUri/topics/test")
            post("{}".toRequestBody(KAFKA_JSON_TYPE))
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.BAD_REQUEST) {
            url("$baseUri/topics/test")
            post("".toRequestBody(KAFKA_JSON_TYPE))
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.OK) {
            url(config.restProxyUrl + "/topics/test")
            addHeader("Authorization", BEARER + accessToken)
        }

        httpClient.call(Status.OK) {
            url(config.restProxyUrl + "/topics/test")
            head()
            addHeader("Authorization", BEARER + accessToken)
        }
    }

    companion object {
        private lateinit var httpClient: OkHttpClient

        @BeforeAll
        @JvmStatic
        fun setUpClass() {
            httpClient = OkHttpClient()
        }

        const val MP_CLIENT = "ManagementPortalapp"
        const val REST_CLIENT = "pRMT"
        const val USER = "sub-1"
        const val PROJECT = "radar"
        const val SOURCE = "03d28e5c-e005-46d4-a9b3-279c27fbbc83"
        const val ADMIN_USER = "admin"
        const val ADMIN_PASSWORD = "admin"
        private val JSON_TYPE = APPLICATION_JSON.toMediaType()
        private val KAFKA_JSON_TYPE = ACCEPT_AVRO_V2_JSON.toMediaType()

        fun OkHttpClient.call(expectedStatus: Int, requestSupplier: Request.Builder.() -> Unit): JsonNode? {
            val request = Request.Builder().apply(requestSupplier).build()
            return newCall(request).execute().use { response ->
                println("${request.method} ${request.url}")
                assertThat(response.code, `is`(expectedStatus))
                println(response.headers)
                response.body?.let { responseBody ->
                    val string = responseBody.string()
                    println(string)
                    Json.mapper.readTree(string)
                }
            }
        }

        fun OkHttpClient.call(expectedStatus: Status, requestSupplier: Request.Builder.() -> Unit): JsonNode? {
            return call(expectedStatus.statusCode, requestSupplier)
        }

        fun OkHttpClient.call(expectedStatus: Status, stringProperty: String, requestSupplier: Request.Builder.() -> Unit): String {
            return call(expectedStatus, requestSupplier)?.get(stringProperty)?.asText()
                    ?: throw AssertionError("String property $stringProperty not found")
        }
    }
}
