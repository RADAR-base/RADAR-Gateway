package org.radarbase.gateway.resource

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.auth.*
import io.ktor.client.plugins.auth.providers.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.*
import io.ktor.utils.io.core.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.lastOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.data.AvroRecordData
import org.radarbase.gateway.resource.KafkaRootTest.Companion.BASE_URI
import org.radarbase.gateway.resource.KafkaTopics.Companion.ACCEPT_AVRO_V2_JSON
import org.radarbase.jersey.auth.filter.AuthenticationFilter.Companion.BEARER
import org.radarbase.kotlin.coroutines.forkJoin
import org.radarbase.ktor.auth.OAuth2AccessToken
import org.radarbase.ktor.auth.bearer
import org.radarbase.producer.AuthenticationException
import org.radarbase.producer.io.timeout
import org.radarbase.producer.rest.ConnectionState
import org.radarbase.producer.rest.RestKafkaSender.Companion.KAFKA_REST_BINARY_ENCODING
import org.radarbase.producer.rest.RestKafkaSender.Companion.restKafkaSender
import org.radarbase.producer.schema.SchemaRetriever
import org.radarbase.producer.schema.SchemaRetriever.Companion.schemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration
import java.util.concurrent.atomic.LongAdder
import kotlin.time.Duration.Companion.seconds

class KafkaTopicsTest {
    private lateinit var httpClient: HttpClient

    @BeforeEach
    fun setUpClass() {
        httpClient = HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        ignoreUnknownKeys = true
                        coerceInputValues = true
                    },
                )

                defaultRequest {
                    url("$MANAGEMENTPORTAL_URL/")
                }
            }
        }
    }

    private suspend fun requestAccessToken(): String {
        val response = httpClient.submitForm(
            url = "oauth/token",
            formParameters = Parameters.build {
                append("username", ADMIN_USER)
                append("password", ADMIN_PASSWORD)
                append("grant_type", "password")
            },
        ) {
            basicAuth(username = MP_CLIENT, password = "")
        }
        assertThat(response.status, equalTo(HttpStatusCode.OK))
        val token = response.body<OAuth2AccessToken>()

        val tokenUrl = httpClient.get("api/oauth-clients/pair") {
            url {
                parameters.append("clientId", REST_CLIENT)
                parameters.append("login", USER)
                parameters.append("persistent", "false")
            }
            bearer(requireNotNull(token.accessToken))
        }.body<MPPairResponse>().tokenUrl

        val refreshToken = httpClient.get(tokenUrl).body<MPMetaToken>().refreshToken

        return requireNotNull(
            httpClient.submitForm(
                url = "oauth/token",
                formParameters = Parameters.build {
                    append("grant_type", "refresh_token")
                    append("refresh_token", refreshToken)
                },
            ) {
                basicAuth(REST_CLIENT, "")
            }.body<OAuth2AccessToken>().accessToken,
        )
    }

    @Test
    fun testListTopics() = runBlocking {
        val accessToken = requestAccessToken()

        val retriever = schemaRetriever(SCHEMA_REGISTRY_URL) {
            httpClient {
                timeout(10.seconds)
            }
        }

        val topic = AvroTopic(
            "test",
            ObservationKey.getClassSchema(),
            PhoneAcceleration.getClassSchema(),
            ObservationKey::class.java,
            PhoneAcceleration::class.java,
        )

        val time = System.currentTimeMillis() / 1000.0
        val key = ObservationKey(PROJECT, USER, SOURCE)
        val value = PhoneAcceleration(time, time, 0.1f, 0.1f, 0.1f)

        // initialize topic and schema
        sendData(
            REST_PROXY_URL,
            retriever,
            topic,
            accessToken,
            key,
            value,
            binary = false,
            gzip = false,
        )

        println("Initialized kafka brokers")

        delay(2.seconds)

        try {
            testTopicList(accessToken)
        } catch (ex: AuthenticationException) {
            // try again
            testTopicList(accessToken)
        }
        val results = mutableListOf<String>()
        sendData(BASE_URI, retriever, topic, accessToken, key, value, binary = true, gzip = true)
        results += sendData(
            BASE_URI,
            retriever,
            topic,
            accessToken,
            key,
            value,
            binary = true,
            gzip = true,
        )
        results += sendData(
            BASE_URI,
            retriever,
            topic,
            accessToken,
            key,
            value,
            binary = true,
            gzip = false,
        )
        results += sendData(
            BASE_URI,
            retriever,
            topic,
            accessToken,
            key,
            value,
            binary = false,
            gzip = true,
        )
        results += sendData(
            BASE_URI,
            retriever,
            topic,
            accessToken,
            key,
            value,
            binary = false,
            gzip = false,
        )
        results.forEach { println(it) }

        httpClient.head("$BASE_URI/topics") {
            bearer(accessToken)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.OK))
        }

        httpClient.get("$BASE_URI/topics").let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.Unauthorized))
        }

        httpClient.get("$BASE_URI/topics").let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.Unauthorized))
        }

        httpClient.head("$BASE_URI/topics").let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.Unauthorized))
        }

        httpClient.post("$BASE_URI/topics") {
            bearer(accessToken)
            setBody("{}")
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.UnprocessableEntity))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(accessToken)
            setBody("{}")
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.UnprocessableEntity))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(accessToken)
            setBody("{}")
            contentType(KAFKA_JSON_TYPE)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.UnprocessableEntity))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(accessToken)
            setBody("")
            contentType(KAFKA_JSON_TYPE)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.BadRequest))
        }
    }

    private fun testTopicList(accessToken: String) = runBlocking {
        val gatewayTopicList = httpClient.get("$BASE_URI/topics") {
            bearer(accessToken)
        }.body<List<String>>()

        assertThat(gatewayTopicList, hasItem("test"))
    }

    private fun sendData(
        url: String,
        retriever: SchemaRetriever,
        topic: AvroTopic<ObservationKey, PhoneAcceleration>,
        accessToken: String,
        key: ObservationKey,
        value: PhoneAcceleration,
        binary: Boolean,
        gzip: Boolean,
    ): String {
        val sender = restKafkaSender {
            baseUrl = url
            httpClient {
                headers["Authorization"] = BEARER + accessToken
                timeout(10.seconds)
            }
            if (gzip) {
                contentEncoding = "gzip"
            }
            if (binary) {
                contentType = KAFKA_REST_BINARY_ENCODING
            }
            schemaRetriever = retriever
        }

        val numRequests = LongAdder()
        val numTime = LongAdder()
        val recordData = AvroRecordData(topic, key, List(1000) { value })
        val timeStart = System.nanoTime()
        runBlocking {
            (0 until 1)
                .forkJoin {
                    val topicSender = sender.sender(topic)
                    repeat(2) {
                        val timeRequestStart = System.nanoTime()
                        topicSender.send(recordData)
                        numRequests.increment()
                        numTime.add(System.nanoTime() - timeRequestStart)
                    }
                }

            assertThat(sender.connectionState.lastOrNull(), `is`(ConnectionState.State.CONNECTED))
        }
        val timeEnd = System.nanoTime()

        val timePerRequest = numTime.sum() / (numRequests.sum() * 1_000_000)
        val totalTime = (timeEnd - timeStart) / 1_000_000_000.0

        return """
            =============================================
            url: $url, binary: $binary, gzip: $gzip
            Time per request $timePerRequest milliseconds
            Time to send data: $totalTime seconds
        """.trimIndent()
    }

    companion object {
        private const val MANAGEMENTPORTAL_URL = "http://localhost:8080"
        private const val SCHEMA_REGISTRY_URL = "http://localhost:8081/"
        private const val REST_PROXY_URL = "http://localhost:8082/"
        const val MP_CLIENT = "ManagementPortalapp"
        const val REST_CLIENT = "pRMT"
        const val USER = "sub-1"
        const val PROJECT = "radar"
        const val SOURCE = "03d28e5c-e005-46d4-a9b3-279c27fbbc83"
        const val ADMIN_USER = "admin"
        const val ADMIN_PASSWORD = "admin"
        private val KAFKA_JSON_TYPE = ContentType.parse(ACCEPT_AVRO_V2_JSON)
    }
}
