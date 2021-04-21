package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import jakarta.ws.rs.core.Context
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.BinaryDecoder
import org.apache.avro.io.Decoder
import org.apache.avro.io.DecoderFactory
import org.radarbase.gateway.Config
import org.radarbase.jersey.auth.Auth
import org.radarbase.jersey.exception.HttpInvalidContentException
import org.radarbase.producer.rest.SchemaRetriever
import java.io.IOException
import java.io.InputStream
import java.nio.ByteBuffer

/** Converts binary input from a RecordSet to Kafka JSON. */
class BinaryToAvroConverter(
    @Context private val schemaRetriever: SchemaRetriever,
    @Context private val auth: Auth,
    @Context private val config: Config,
) {

    private var binaryDecoder: BinaryDecoder? = null
    private val readContext = ReadContext()

    fun process(topic: String, input: InputStream): AvroProcessingResult {
        val decoder = DecoderFactory.get().binaryDecoder(input, binaryDecoder)

        binaryDecoder = decoder

        val recordData = DecodedRecordData(
            topic,
            decoder,
            schemaRetriever,
            auth,
            readContext,
            config.auth.checkSourceId,
        )

        return AvroProcessingResult(
            recordData.keySchemaMetadata.id,
            recordData.valueSchemaMetadata.id,
            recordData.map { value ->
                Pair(recordData.key, value)
            },
        )
    }

    class ReadContext {
        private val genericData = GenericData().apply {
            isFastReaderEnabled = true
        }
        private var buffer: ByteBuffer? = null
        private var valueDecoder: BinaryDecoder? = null
        private var valueReader: GenericDatumReader<GenericRecord>? = null

        fun init(schema: Schema) {
            if (valueReader?.schema != schema) {
                @Suppress("UNCHECKED_CAST")
                valueReader = genericData.createDatumReader(schema) as GenericDatumReader<GenericRecord>
            }
        }

        fun decodeValue(decoder: Decoder): GenericRecord {
            return try {
                buffer = decoder.readBytes(buffer)
                valueDecoder = DecoderFactory.get().binaryDecoder(ByteBufferBackedInputStream(buffer), valueDecoder)
                val reader = valueReader
                    ?: throw IllegalStateException("Value reader is not yet set")
                reader.read(null, valueDecoder)
                    ?: throw HttpInvalidContentException("No record in data")
            } catch (ex: IOException) {
                throw HttpInvalidContentException("Malformed record contents: ${ex.message}")
            }
        }
    }
}
