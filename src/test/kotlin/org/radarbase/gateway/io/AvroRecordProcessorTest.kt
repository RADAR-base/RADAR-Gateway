package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.avro.Schema
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.nullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test
import org.radarbase.jersey.auth.disabled.DisabledAuth
import org.radarbase.producer.rest.AvroDataMapperFactory.IDENTITY_MAPPER
import org.radarcns.passive.phone.PhoneBluetoothDevices

internal class AvroRecordProcessorTest {
    @Test
    fun process() {
        val objectMapper = ObjectMapper()
        val processor = AvroRecordProcessor(
            false,
            DisabledAuth("test"),
            objectMapper,
        )

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
                mapper = IDENTITY_MAPPER
            ),
            AvroParsingContext(Schema.Type.MAP, "value", AvroParsingContext(Schema.Type.ARRAY, "records[0]")),
        )

        assertThat(result.get("time"), `is`(1.0))
        assertThat(result.get("timeReceived"), `is`(1.1))
        assertThat(result.get("pairedDevices"), `is`(2))
        assertThat(result.get("nearbyDevices"), nullValue())
        assertThat(result.get("bluetoothEnabled"), `is`(true))
    }
}
