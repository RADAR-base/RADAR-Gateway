package org.radarcns.gateway

import com.fasterxml.jackson.databind.JsonNode
import okhttp3.*
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.hasItems
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.radarcns.config.ServerConfig
import org.radarcns.gateway.filter.ManagementPortalAuthenticationFilter.Companion.BEARER
import org.radarcns.gateway.util.Json
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration
import org.radarcns.producer.rest.ManagedConnectionPool
import org.radarcns.producer.rest.RestSender
import org.radarcns.producer.rest.SchemaRetriever
import org.radarcns.topic.AvroTopic
import java.net.URI
import java.net.URL
import javax.ws.rs.core.MediaType.APPLICATION_JSON
import javax.ws.rs.core.Response
import javax.ws.rs.core.Response.Status

const val MP_CLIENT = "ManagementPortalapp"
const val MP_SECRET = "my-secret"
const val REST_CLIENT = "pRMT"
const val SECRET = "secret"
const val USER = "sub-1"
const val PROJECT = "radar"
const val SOURCE = "03d28e5c-e005-46d4-a9b3-279c27fbbc83"
const val ADMIN_USER = "admin"
const val ADMIN_PASSWORD = "admin"

class KafkaTopicList {
    @Test
    fun testListTopics() {
        val baseUri = "http://localhost:8080/radar-gateway"
        val config = Config()
        config.managementPortalUrl = "http://localhost:8090"
        config.restProxyUrl = "http://localhost:8082"
        config.baseUri = URI.create(baseUri)

        val httpClient = OkHttpClient.Builder()
                .connectionPool(ManagedConnectionPool.GLOBAL_POOL.acquire())
                .build()

        val clientToken = call(httpClient, Status.OK, "access_token") {
            it.url("${config.managementPortalUrl}/oauth/token")
                    .addHeader("Authorization", Credentials.basic(MP_CLIENT, MP_SECRET))
                    .post(FormBody.Builder()
                            .add("username", ADMIN_USER)
                            .add("password", ADMIN_PASSWORD)
                            .add("grant_type", "password")
                            .build())
        }

        val refreshToken = call(httpClient, Status.OK, "refreshToken") {
            val pairUrl = HttpUrl.parse(config.managementPortalUrl)!!
                    .newBuilder("api/oauth-clients/pair")!!
                    .addEncodedQueryParameter("clientId", REST_CLIENT)
                    .addEncodedQueryParameter("login", USER)
                    .build()

            it.addHeader("Authorization", BEARER + clientToken)
                    .url(pairUrl)
        }

        val accessToken = call(httpClient, Status.OK, "access_token") {
            val formBody = FormBody.Builder()
                    .add("grant_type", "refresh_token")
                    .add("refresh_token", refreshToken)
                    .build()

            it.url("${config.managementPortalUrl}/oauth/token")
                    .addHeader("Authorization", Credentials.basic(REST_CLIENT, SECRET))
                    .post(formBody)
        }

        val retriever = SchemaRetriever(ServerConfig(URL("http://localhost:8081/")), 10)

        val topic = AvroTopic("test",
                ObservationKey.getClassSchema(), PhoneAcceleration.getClassSchema(),
                ObservationKey::class.java, PhoneAcceleration::class.java)

        val time = System.currentTimeMillis() / 1000.0
        val key = ObservationKey(PROJECT, USER, SOURCE)
        val value = PhoneAcceleration(time, time, 0.1f, 0.1f, 0.1f)

        val sender = RestSender.Builder()
                .server(ServerConfig(URL(config.restProxyUrl + "/")))
                .schemaRetriever(retriever)
                .connectionPool(ManagedConnectionPool.GLOBAL_POOL)
                .build()

        sender.sender(topic).use {
            it.send(key, value)
        }

        val topicList = call(httpClient, Status.OK) {
            it.url(config.restProxyUrl + "/topics")
        }!!
        assertThat(topicList.isArray, `is`(true))
        assertThat(topicList.toList().map { it.asText() }, hasItems("_schemas", "test"))

        Thread.sleep(2000)

        val server = GrizzlyServer(config)
        server.start()

        sender.headers = Headers.Builder()
                .add("Authorization", BEARER + accessToken)
                .build()
        sender.setKafkaConfig(ServerConfig(config.baseUri.toURL()))

        sender.sender(topic).use {
            it.send(key, value)
        }

        val gatewayTopicList = call(httpClient, Status.OK) {
            it.url(config.restProxyUrl + "/topics")
                    .addHeader("Authorization", BEARER + accessToken)
        }

        assertThat(gatewayTopicList, `is`(topicList))

        call(httpClient, Status.UNAUTHORIZED) {
            it.url("$baseUri/topics")
        }

        call(httpClient, Status.UNSUPPORTED_MEDIA_TYPE) {
            it.url("$baseUri/topics/test")
                    .post(RequestBody.create(MediaType.parse(APPLICATION_JSON), "{}"))
                    .addHeader("Authorization", BEARER + accessToken)
        }

        call(httpClient, 422) {
            it.url("$baseUri/topics/test")
                    .post(RequestBody.create(MediaType.parse("application/vnd.kafka.avro.v2+json"), "{}"))
                    .addHeader("Authorization", BEARER + accessToken)
        }

        call(httpClient, Status.BAD_REQUEST) {
            it.url("$baseUri/topics/test")
                    .post(RequestBody.create(MediaType.parse("application/vnd.kafka.avro.v2+json"), ""))
                    .addHeader("Authorization", BEARER + accessToken)
        }
    }

    companion object {
        fun call(httpClient: OkHttpClient, expectedStatus: Int, requestSupplier: (Request.Builder) -> Request.Builder): JsonNode? {
            val request = requestSupplier(Request.Builder()).build()
            println(request.url())
            return httpClient.newCall(request).execute().use { response ->
                val body = response.body()?.let {
                    val tree = Json.mapper.readTree(it.byteStream())
                    println(tree)
                    tree
                }
                assertThat(response.code(), `is`(expectedStatus))
                body
            }
        }

        fun call(httpClient: OkHttpClient, expectedStatus: Response.Status, requestSupplier: (Request.Builder) -> Request.Builder): JsonNode? {
            return call(httpClient, expectedStatus.statusCode, requestSupplier)
        }

        fun call(httpClient: OkHttpClient, expectedStatus: Response.Status, stringProperty: String, requestSupplier: (Request.Builder) -> Request.Builder): String {
            return call(httpClient, expectedStatus, requestSupplier)?.get(stringProperty)?.asText()
                    ?: throw AssertionError("String property $stringProperty not found")
        }
    }
}
