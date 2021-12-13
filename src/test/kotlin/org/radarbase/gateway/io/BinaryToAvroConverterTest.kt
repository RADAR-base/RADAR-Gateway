package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import okio.Buffer
import org.apache.avro.generic.GenericRecordBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.radarbase.auth.authorization.Permission
import org.radarbase.auth.token.RadarToken
import org.radarbase.data.AvroRecordData
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.jersey.auth.Auth
import org.radarbase.producer.rest.BinaryRecordRequest
import org.radarbase.producer.rest.ParsedSchemaMetadata
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration

class BinaryToAvroConverterTest {
    @Test
    fun testConversion() {

        val topic = AvroTopic(
            "test",
            ObservationKey.getClassSchema(), PhoneAcceleration.getClassSchema(),
            ObservationKey::class.java, PhoneAcceleration::class.java
        )

        val keySchemaMetadata = ParsedSchemaMetadata(1, 1, topic.keySchema)
        val valueSchemaMetadata = ParsedSchemaMetadata(2, 1, topic.valueSchema)

        val requestRecordData = AvroRecordData(
            topic,
            ObservationKey("p", "u", "s"),
            listOf(
                PhoneAcceleration(1.0, 1.1, 1.2f, 1.3f, 1.4f),
                PhoneAcceleration(2.0, 2.1, 2.2f, 2.3f, 2.4f),
            )
        )
        val binaryRequest = BinaryRecordRequest(topic)
        binaryRequest.prepare(keySchemaMetadata, valueSchemaMetadata, requestRecordData)
        val requestBuffer = Buffer()
        binaryRequest.writeToSink(requestBuffer)

        val schemaRetriever = mock<SchemaRetriever> {
            on { getBySubjectAndVersion("test", false, 1) } doReturn keySchemaMetadata
            on { getBySubjectAndVersion("test", true, 1) } doReturn valueSchemaMetadata
        }

        val token = mock<RadarToken> {
            on { hasPermissionOnSource(Permission.MEASUREMENT_CREATE, "p", "u", "s") } doReturn true
        }

        val auth = object : Auth {
            override val token: RadarToken = token

            override fun getClaim(name: String): JsonNode {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override val defaultProject: String = "p"
            override val userId: String = "u"

            override fun hasRole(projectId: String, role: String) = true
        }
        val converter = BinaryToAvroConverter(schemaRetriever, auth, GatewayConfig())

        val genericKey = GenericRecordBuilder(ObservationKey.getClassSchema()).apply {
            this["projectId"] = "p"
            this["userId"] = "u"
            this["sourceId"] = "s"
        }.build()
        val genericValue1 = GenericRecordBuilder(PhoneAcceleration.getClassSchema()).apply {
            set("time", 1.0)
            set("timeReceived", 1.1)
            set("x", 1.2f)
            set("y", 1.3f)
            set("z", 1.4f)
        }.build()
        val genericValue2 = GenericRecordBuilder(PhoneAcceleration.getClassSchema()).apply {
            set("time", 2.0)
            set("timeReceived", 2.1)
            set("x", 2.2f)
            set("y", 2.3f)
            set("z", 2.4f)
        }.build()

        assertEquals(
            AvroProcessingResult(
                1, 2, listOf(
                Pair(genericKey, genericValue1),
                Pair(genericKey, genericValue2),
            )
            ),
            converter.process("test", requestBuffer.inputStream())
        )
    }
}
