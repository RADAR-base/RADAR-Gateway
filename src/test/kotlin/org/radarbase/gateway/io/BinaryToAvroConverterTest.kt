package org.radarbase.gateway.io

import kotlinx.coroutines.runBlocking
import okio.Buffer
import org.apache.avro.generic.GenericRecordBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.radarbase.data.AvroRecordData
import org.radarbase.gateway.config.GatewayConfig
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
            ObservationKey.getClassSchema(),
            PhoneAcceleration.getClassSchema(),
            ObservationKey::class.java,
            PhoneAcceleration::class.java,
        )

        val keySchemaMetadata = ParsedSchemaMetadata(1, 1, topic.keySchema)
        val valueSchemaMetadata = ParsedSchemaMetadata(2, 1, topic.valueSchema)

        val requestRecordData = AvroRecordData(
            topic,
            ObservationKey("p", "u", "s"),
            listOf(
                PhoneAcceleration(1.0, 1.1, 1.2f, 1.3f, 1.4f),
                PhoneAcceleration(2.0, 2.1, 2.2f, 2.3f, 2.4f),
            ),
        )

        val requestBuffer = Buffer()
        BinaryRecordRequest(topic).run {
            prepare(keySchemaMetadata, valueSchemaMetadata, requestRecordData)
            writeToSink(requestBuffer)
        }

        val schemaRetriever = mock<SchemaRetriever> {
            on { getBySubjectAndVersion("test", false, 1) } doReturn keySchemaMetadata
            on { getBySubjectAndVersion("test", true, 1) } doReturn valueSchemaMetadata
        }

        val converter = BinaryToAvroConverter(schemaRetriever, mockAuthService(), GatewayConfig())

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

        runBlocking {
            assertEquals(
                AvroProcessingResult(
                    keySchemaId = 1,
                    valueSchemaId = 2,
                    records = listOf(
                        Pair(genericKey, genericValue1),
                        Pair(genericKey, genericValue2),
                    ),
                ),
                converter.process("test", requestBuffer.inputStream()),
            )
        }
    }
}
