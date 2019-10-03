package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import okio.BufferedSink
import org.apache.avro.Schema
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.BinaryDecoder
import org.apache.avro.io.Decoder
import org.apache.avro.io.DecoderFactory
import org.radarbase.auth.jersey.Auth
import org.radarbase.producer.rest.JsonRecordRequest
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.gateway.exception.InvalidContentException
import java.io.IOException
import java.io.InputStream
import java.lang.IllegalStateException
import java.nio.ByteBuffer
import javax.ws.rs.core.Context

/** Converts binary input from a RecordSet to Kafka JSON. */
class BinaryToAvroConverter(
        @Context private val schemaRetriever: SchemaRetriever,
        @Context private val auth: Auth) {

    private var binaryDecoder: BinaryDecoder? = null
    private val readContext = ReadContext()

    fun process(topic: String, input: InputStream): (BufferedSink) -> Unit {
        val decoder = DecoderFactory.get().binaryDecoder(input, binaryDecoder)

        binaryDecoder = decoder

        val recordData = DecodedRecordData(topic, decoder, schemaRetriever, auth, readContext)

        val recordRequest = JsonRecordRequest(recordData.topic)
        recordRequest.prepare(
                recordData.keySchemaMetadata,
                recordData.valueSchemaMetadata,
                recordData)

        return recordRequest::writeToSink
    }

    class ReadContext {
        private var buffer: ByteBuffer? = null
        private var record: GenericRecord? = null
        private var valueDecoder : BinaryDecoder? = null
        private var valueReader : GenericDatumReader<GenericRecord>? = null

        fun init(schema: Schema) {
            if (valueReader?.schema != schema) {
                valueReader = GenericDatumReader(schema)
            }
        }

        fun decodeValue(decoder: Decoder): GenericRecord {
            return try {
                buffer = decoder.readBytes(buffer)
                valueDecoder = DecoderFactory.get().binaryDecoder(ByteBufferBackedInputStream(buffer), valueDecoder)
                val reader = valueReader ?: throw IllegalStateException("Value reader is not yet set")
                reader.read(record, valueDecoder)
                        ?.also { record = it }
                        ?: throw InvalidContentException("No record in data")
            } catch (ex: IOException) {
                throw InvalidContentException("Malformed record contents: ${ex.message}")
            }
        }
    }
}
