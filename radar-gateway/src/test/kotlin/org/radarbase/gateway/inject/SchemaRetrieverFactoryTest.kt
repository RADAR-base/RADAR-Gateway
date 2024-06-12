import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_URL_CONFIG
import io.confluent.kafka.serializers.AbstractKafkaSchemaSerDeConfig.SCHEMA_REGISTRY_USER_INFO_CONFIG
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.gateway.config.KafkaConfig
import org.radarbase.gateway.inject.SchemaRetrieverFactory
import org.radarbase.topic.AvroTopic
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration

class SchemaRetrieverFactoryTest {
    @Test
    fun `test basic auth credentials are correctly passed`() {
        val mockConfig: GatewayConfig = mock()
        // Create a new MockWebServer
        val server = MockWebServer()

        val topic = AvroTopic(
            "test",
            ObservationKey.getClassSchema(),
            PhoneAcceleration.getClassSchema(),
            ObservationKey::class.java,
            PhoneAcceleration::class.java,
        )

        // Schedule a response

        server.enqueue(
            MockResponse()
                .setHeader(HttpHeaders.ContentType, "application/json")
                .setBody(
                    """
                    {
                        "id": 1
                    }
                    """.trimIndent(),
                ),
        )

        // Start the server
        server.start()

        // Update the SCHEMA_REGISTRY_URL_CONFIG to use the mock server's URL
        val serializationConfig = mapOf(
            SCHEMA_REGISTRY_URL_CONFIG to server.url("/").toString(),
            SCHEMA_REGISTRY_USER_INFO_CONFIG to "username:password",
        )

        val mockKafkaConfig: KafkaConfig = mock()
        whenever(mockConfig.kafka).doReturn(mockKafkaConfig)
        whenever(mockKafkaConfig.serialization).doReturn(serializationConfig)

        val factory = SchemaRetrieverFactory(mockConfig)
        val retriever = factory.get()
        runBlocking {
            retriever.addSchema(topic.name, false, topic.keySchema)
        }

        // Get the request that was received by the MockWebServer
        val request = server.takeRequest()

        // Verify the Basic Auth credentials
        val authHeader = request.getHeader("Authorization")
        assertEquals("Basic dXNlcm5hbWU6cGFzc3dvcmQ=", authHeader)

        // Shut down the server. Instances cannot be reused.
        server.shutdown()
    }
}
