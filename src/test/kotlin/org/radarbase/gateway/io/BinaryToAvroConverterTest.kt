package org.radarbase.gateway.io

import okio.Buffer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock
import org.radarbase.data.AvroRecordData
import org.radarbase.producer.rest.BinaryRecordRequest
import org.radarbase.producer.rest.ParsedSchemaMetadata
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarcns.auth.authorization.Permission
import org.radarbase.gateway.auth.Auth
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

        val schemaRetriever = mock(SchemaRetriever::class.java)
        `when`(schemaRetriever.getSchemaMetadata("test", false, 1)).thenReturn(keySchemaMetadata)
        `when`(schemaRetriever.getSchemaMetadata("test", true, 1)).thenReturn(valueSchemaMetadata)

        val auth = object : Auth {
            override val defaultProject: String = "p"
            override val userId: String = "u"

            override fun checkPermission(projectId: String?, userId: String?, sourceId: String?) {}

            override fun hasRole(projectId: String, role: String) = true

            override fun hasPermission(permission: Permission) = true
        }
        val converter = BinaryToAvroConverter(schemaRetriever, auth)

        val proxyBuffer = Buffer()
        converter.process("test", requestBuffer.inputStream())(proxyBuffer)
        assertEquals("{\"key_schema_id\":1,\"value_schema_id\":2,\"records\":[{\"key\":{\"projectId\":{\"string\":\"p\"},\"userId\":\"u\",\"sourceId\":\"s\"},\"value\":{\"time\":1.0,\"timeReceived\":1.1,\"x\":1.2,\"y\":1.3,\"z\":1.4}}]}",
                proxyBuffer.readString(UTF_8))
    }
}
