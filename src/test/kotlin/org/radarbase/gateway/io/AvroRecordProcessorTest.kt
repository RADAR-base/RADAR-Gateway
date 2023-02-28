package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.avro.Schema
import org.apache.avro.generic.GenericArray
import org.apache.avro.generic.GenericRecord
import org.hamcrest.CoreMatchers.*
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.radarbase.producer.rest.AvroDataMapperFactory.IDENTITY_MAPPER
import org.radarcns.active.questionnaire.Questionnaire
import org.radarcns.passive.phone.PhoneBluetoothDevices

internal class AvroRecordProcessorTest {
    @Test
    fun processPhoneBluetooth() {
        val objectMapper = ObjectMapper()
        val processor = AvroRecordProcessor(
            false,
            mockAuthService(),
            objectMapper,
        )

        // language=json
        val valueString = """
            {
                "time": 1.0,
                "timeReceived": 1.1,
                "pairedDevices": {"int": 2},
                "nearbyDevices": null,
                "bluetoothEnabled": true
            }
        """.trimIndent()

        val result = processor.processValue(
            objectMapper.readTree(valueString),
            AvroProcessor.JsonToObjectMapping(
                sourceSchema = PhoneBluetoothDevices.getClassSchema(),
                targetSchema = PhoneBluetoothDevices.getClassSchema(),
                targetSchemaId = 100,
                mapper = IDENTITY_MAPPER,
            ),
            AvroParsingContext(Schema.Type.MAP, "value", AvroParsingContext(Schema.Type.ARRAY, "records[0]")),
        )

        assertThat(result.get("time"), `is`(1.0))
        assertThat(result.get("timeReceived"), `is`(1.1))
        assertThat(result.get("pairedDevices"), `is`(2))
        assertThat(result.get("nearbyDevices"), nullValue())
        assertThat(result.get("bluetoothEnabled"), `is`(true))
    }

    @Test
    fun processQuestionnaire() {
        val objectMapper = ObjectMapper()
        val processor = AvroRecordProcessor(
            false,
            mockAuthService(),
            objectMapper,
        )

        // language=json
        val valueString = """
            {
                "time": 1.0,
                "timeCompleted": 1.1,
                "timeNotification": null,
                "name": "a",
                "version": "1",
                "answers": [
                    {
                        "questionId": {"string": "Q1"},
                        "value": {"int": 5},
                        "startTime": 1.2,
                        "endTime": 1.3
                    }
                ]
            }
        """.trimIndent()

        val result = processor.processValue(
            objectMapper.readTree(valueString),
            AvroProcessor.JsonToObjectMapping(
                sourceSchema = Questionnaire.getClassSchema(),
                targetSchema = Questionnaire.getClassSchema(),
                targetSchemaId = 101,
                mapper = IDENTITY_MAPPER,
            ),
            AvroParsingContext(Schema.Type.MAP, "value", AvroParsingContext(Schema.Type.ARRAY, "records[0]")),
        )

        assertThat(result.get("time"), `is`(1.0))
        assertThat(result.get("timeCompleted"), `is`(1.1))
        assertThat(result.get("timeNotification"), nullValue())
        assertThat(result.get("name"), `is`("a"))
        assertThat(result.get("version"), `is`("1"))
        val answers = result.get("answers") as? GenericArray<*>
        assertThat(answers, not(nullValue()))
        answers ?: throw AssertionError("answers is null")
        assertThat(answers.size, `is`(1))
        val firstAnswer = answers[0] as GenericRecord
        assertThat(firstAnswer.get("questionId"), `is`("Q1"))
        assertThat(firstAnswer.get("value"), `is`(5))
        assertThat(firstAnswer.get("startTime"), `is`(1.2))
        assertThat(firstAnswer.get("endTime"), `is`(1.3))
    }
}
