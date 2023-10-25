package org.radarbase.gateway.resource

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.basicAuth
import io.ktor.client.request.forms.submitForm
import io.ktor.client.request.get
import io.ktor.client.request.head
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.CoreMatchers.hasItem
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarbase.data.AvroRecordData
import org.radarbase.gateway.resource.KafkaRootTest.Companion.BASE_URI
import org.radarbase.gateway.resource.KafkaTopics.Companion.ACCEPT_AVRO_V2_JSON
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
import kotlin.system.measureNanoTime
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds
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
            }
            defaultRequest {
                url("$MANAGEMENTPORTAL_URL/")
            }
        }
    }

    @Test
    fun testListTopics() = runBlocking {
        val accessTokenJob = async(Dispatchers.IO) {
            requestAccessToken()
        }

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
        val schemaJobs = listOf(
            async(Dispatchers.IO) {
                retriever.addSchema(topic.name, false, topic.keySchema)
            },
            async(Dispatchers.IO) {
                retriever.addSchema(topic.name, true, topic.valueSchema)
            },
        )

        val time = System.currentTimeMillis() / 1000.0
        val key = ObservationKey(PROJECT, USER, SOURCE)
        val value = PhoneAcceleration(time, time, 0.1f, 0.1f, 0.1f)

        val restProxyContext = RequestContext(
            REST_PROXY_URL,
            retriever,
            topic,
            accessTokenJob.await(),
            key,
            value,
        )

        schemaJobs.awaitAll()

        // initialize topic and schema
        val restProxyResult = restProxyContext.sendData(
            binary = false,
            gzip = false,
        )

        println("Initialized kafka brokers")
        println(restProxyResult)
        println("=============================================")

        delay(2.seconds)

        val gatewayContext = restProxyContext.copy(url = BASE_URI)

        try {
            gatewayContext.testTopicList()
        } catch (ex: AuthenticationException) {
            // try again
            gatewayContext.testTopicList()
        }
        val results = mutableListOf<String>()
        gatewayContext.sendData(binary = true, gzip = true)
        results += gatewayContext.sendData(
            binary = true,
            gzip = true,
        )
        results += gatewayContext.sendData(
            binary = true,
            gzip = false,
        )
        results += gatewayContext.sendData(
            binary = false,
            gzip = true,
        )
        results += gatewayContext.sendData(
            binary = false,
            gzip = false,
        )
        results.forEach { println(it) }

        httpClient.head("$BASE_URI/topics") {
            bearer(gatewayContext.accessToken)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.OK))
        }

        httpClient.head("$BASE_URI/topics").let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.Unauthorized))
        }

        httpClient.get("$BASE_URI/topics").let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.Unauthorized))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(gatewayContext.accessToken)
            setBody("{}")
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.UnprocessableEntity))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(gatewayContext.accessToken)
            setBody("")
            contentType(ContentType.Application.Json)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.BadRequest))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(gatewayContext.accessToken)
            setBody("{}")
            contentType(KAFKA_JSON_TYPE)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.UnprocessableEntity))
        }

        httpClient.post("$BASE_URI/topics/test") {
            bearer(gatewayContext.accessToken)
            setBody("")
            contentType(KAFKA_JSON_TYPE)
        }.let { response ->
            assertThat(response.status, equalTo(HttpStatusCode.BadRequest))
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

        println("Requesting refresh token")
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

    private suspend fun RequestContext.testTopicList() = withContext(Dispatchers.IO) {
        val gatewayTopicList = httpClient.get("$url/topics") {
            bearer(accessToken)
        }.body<List<String>>()

        println("Found topics $gatewayTopicList")

        assertThat(gatewayTopicList, hasItem("test"))
    }

    private suspend fun RequestContext.sendData(
        binary: Boolean,
        gzip: Boolean,
    ): String {
        val sender = restKafkaSender {
            baseUrl = url
            headers["Authorization"] = "Bearer $accessToken"
            httpClient {
                timeout(10.seconds)
            }
            if (gzip) {
                contentEncoding = "gzip"
            }
            contentType = if (binary) {
                KAFKA_REST_BINARY_ENCODING
            } else {
                KAFKA_CONTENT_TYPE
            }
            schemaRetriever = retriever
        }

        val recordData = AvroRecordData(topic, key, List(1000) { value })
        val numThreads = 1
        val numRepeat = 2
        val totalRequestTime: Duration

        val totalWallTime = measureNanoTime {
            totalRequestTime = (0 until numThreads)
                .forkJoin {
                    val topicSender = sender.sender(topic)
                    (0 until numRepeat).sumOf {
                        measureNanoTime {
                            withContext(Dispatchers.IO) {
                                topicSender.send(recordData)
                            }
                        }
                    }
                }
                .sum()
                .nanoseconds

            assertThat(sender.connectionState.firstOrNull(), `is`(ConnectionState.State.CONNECTED))
        }.nanoseconds

        val totalRequests = numThreads * numRepeat
        val timePerRequest = totalRequestTime / totalRequests

        return """
            =============================================
            url: $url, binary: $binary, gzip: $gzip, threads: $numThreads, requests: $totalRequests
            Time per request $timePerRequest
            Time to send data: $totalWallTime
        """.trimIndent()
    }

    data class RequestContext(
        val url: String,
        val retriever: SchemaRetriever,
        val topic: AvroTopic<ObservationKey, PhoneAcceleration>,
        val accessToken: String,
        val key: ObservationKey,
        val value: PhoneAcceleration,
    )

    companion object {
        private const val MANAGEMENTPORTAL_URL = "http://localhost:8080/managementportal"
        private const val SCHEMA_REGISTRY_URL = "http://localhost:8081/"
        private const val REST_PROXY_URL = "http://localhost:8082"
        const val MP_CLIENT = "ManagementPortalapp"
        const val REST_CLIENT = "pRMT"
        const val USER = "sub-1"
        const val PROJECT = "radar"
        const val SOURCE = "03d28e5c-e005-46d4-a9b3-279c27fbbc83"
        const val ADMIN_USER = "admin"
        const val ADMIN_PASSWORD = "admin"
        private val KAFKA_JSON_TYPE = ContentType.parse(ACCEPT_AVRO_V2_JSON)
        private val KAFKA_CONTENT_TYPE = ContentType("application", "vnd.kafka.avro.v2+json")
    }
}
