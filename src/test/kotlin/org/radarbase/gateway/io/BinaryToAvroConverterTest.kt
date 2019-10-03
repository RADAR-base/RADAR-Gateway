package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.JsonNode
import com.nhaarman.mockitokotlin2.doReturn
import com.nhaarman.mockitokotlin2.mock
import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.radarbase.auth.jersey.Auth
import org.radarbase.data.AvroRecordData
import org.radarbase.producer.rest.BinaryRecordRequest
import org.radarbase.producer.rest.ParsedSchemaMetadata
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarcns.auth.authorization.Permission
import org.radarcns.auth.token.RadarToken
import org.radarcns.kafka.ObservationKey
import org.radarcns.passive.phone.PhoneAcceleration
import kotlin.text.Charsets.UTF_8

class BinaryToAvroConverterTest {
    @Test
    fun testConversion() {

        val topic = AvroTopic("test",
                ObservationKey.getClassSchema(), PhoneAcceleration.getClassSchema(),
                ObservationKey::class.java, PhoneAcceleration::class.java)

        val keySchemaMetadata = ParsedSchemaMetadata(1, 1, topic.keySchema)
        val valueSchemaMetadata = ParsedSchemaMetadata(2, 1, topic.valueSchema)

        val requestRecordData = AvroRecordData(topic, ObservationKey("p", "u", "s"), listOf(PhoneAcceleration(1.0, 1.1, 1.2f, 1.3f, 1.4f)))
        val binaryRequest = BinaryRecordRequest(topic)
        binaryRequest.prepare(keySchemaMetadata, valueSchemaMetadata, requestRecordData)
        val requestBuffer = Buffer()
        binaryRequest.writeToSink(requestBuffer)

        val schemaRetriever = mock<SchemaRetriever> {
            on { getSchemaMetadata("test", false, 1) } doReturn keySchemaMetadata
            on { getSchemaMetadata("test", true, 1) } doReturn valueSchemaMetadata
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
        val converter = BinaryToAvroConverter(schemaRetriever, auth)

        val proxyBuffer = Buffer()
        converter.process("test", requestBuffer.inputStream())(proxyBuffer)
        assertEquals("{\"key_schema_id\":1,\"value_schema_id\":2,\"records\":[{\"key\":{\"projectId\":{\"string\":\"p\"},\"userId\":\"u\",\"sourceId\":\"s\"},\"value\":{\"time\":1.0,\"timeReceived\":1.1,\"x\":1.2,\"y\":1.3,\"z\":1.4}}]}",
                proxyBuffer.readString(UTF_8))
    }
}
