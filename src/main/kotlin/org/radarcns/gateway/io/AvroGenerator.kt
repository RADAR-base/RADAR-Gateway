package org.radarcns.gateway.io;

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import okio.BufferedSink
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericDatumWriter
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.io.BinaryDecoder
import org.apache.avro.io.Decoder
import org.apache.avro.io.DecoderFactory
import org.apache.avro.io.EncoderFactory
import org.glassfish.jersey.internal.inject.PerThread
import org.radarcns.auth.token.RadarToken
import org.radarcns.gateway.auth.AvroAuth
import org.radarcns.gateway.util.Json
import org.radarcns.producer.rest.SchemaRetriever
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer
import javax.ws.rs.NotAuthorizedException
import javax.ws.rs.core.Context
import javax.ws.rs.ext.Provider

@Provider
@PerThread
class AvroGenerator {
    @Context
    private lateinit var schemaRetriever : SchemaRetriever

    @Context
    private lateinit var token: RadarToken

    private var binaryDecoder: BinaryDecoder? = null

    fun process(topic: String, input: InputStream): (BufferedSink) -> Unit {
        val auth = AvroAuth(token)

        val decoder = DecoderFactory.get().binaryDecoder(input, binaryDecoder) ?: throw IOException("Cannot create binary decoder")
        binaryDecoder = decoder

        val keySchemaMetadata = schemaRetriever.getSchemaMetadata(topic, false, decoder.readInt())
        val valueSchemaMetadata = schemaRetriever.getSchemaMetadata(topic, true, decoder.readInt())

        val keyWriter = GenericDatumWriter<GenericData.Record>(keySchemaMetadata.schema)
        val valueWriter = GenericDatumWriter<GenericData.Record>(valueSchemaMetadata.schema)

        val sourceId = decoder.readString()

        val keyRecord = createKey(auth, keySchemaMetadata.schema, sourceId)
        val keyString = recordToString(keyWriter, keyRecord)

        val readContext = ReadContext(valueSchemaMetadata.schema)

        return { sink: BufferedSink ->
            Json.factory.createGenerator(sink.outputStream()).use { gen ->
                gen.writeStartObject()
                gen.writeNumberField("key_schema_id", keySchemaMetadata.id)
                gen.writeNumberField("value_schema_id", valueSchemaMetadata.id)
                gen.writeArrayFieldStart("records")

                for (i in 0..decoder.readArrayStart()) {
                    gen.writeStartObject()
                    gen.writeFieldName("key")
                    gen.writeRaw(keyString)
                    gen.writeFieldName("value")
                    val record = readContext.decodeValue(decoder)
                    gen.writeRaw(recordToString(valueWriter, record))
                    gen.writeEndObject()
                }

                gen.writeEndArray()
                gen.writeEndObject()
            }
        }
    }

    private fun createKey(auth: AvroAuth, schema: Schema, sourceId: String): GenericData.Record {
        val keyBuilder = GenericRecordBuilder(schema)
        if (schema.getField("projectId") != null) {
            keyBuilder.set("projectId", auth.defaultProject)
        }
        if (schema.getField("userId") != null) {
            keyBuilder.set("userId", auth.userId)
        }
        if (schema.getField("sourceId") != null) {
            if (!auth.sourceIds.contains(sourceId)) {
                throw NotAuthorizedException(
                        "record sourceId '$sourceId' has not been added to JWT allowed "
                                + "IDs ${auth.sourceIds}.")
            }

            keyBuilder.set("sourceId", sourceId)
        }

        return keyBuilder.build()
    }

    private class ReadContext(schema: Schema) {
        var buffer: ByteBuffer? = null
        var record: GenericData.Record? = null
        var valueDecoder : BinaryDecoder? = null
        val valueReader = GenericDatumReader<GenericData.Record>(schema)

        fun decodeValue(decoder: Decoder): GenericData.Record {
            buffer = decoder.readBytes(buffer)
            valueDecoder = DecoderFactory.get().binaryDecoder(ByteBufferBackedInputStream(buffer), valueDecoder) ?: throw IOException("Cannot create binary decoder")
            record = valueReader.read(record, valueDecoder)
            return record ?: throw IOException("Failed to read record")
        }
    }

    companion object {
        fun recordToString(writer: GenericDatumWriter<GenericData.Record>, record: GenericData.Record): String {
            return ByteArrayOutputStream().use {
                val encoder = EncoderFactory.get().jsonEncoder(record.schema, it)
                writer.write(record, encoder)
                encoder.flush()
                String(it.toByteArray())
            }
        }
    }
}
