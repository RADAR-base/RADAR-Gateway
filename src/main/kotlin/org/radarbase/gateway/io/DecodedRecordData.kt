package org.radarbase.gateway.io

import com.fasterxml.jackson.databind.util.ByteBufferBackedInputStream
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.io.BinaryDecoder
import org.apache.avro.io.Decoder
import org.apache.avro.io.DecoderFactory
import org.radarbase.data.RecordData
import org.radarbase.jersey.exception.HttpInvalidContentException
import org.radarbase.topic.AvroTopic
import java.io.IOException
import java.nio.ByteBuffer

class DecodedRecordData(
    private val decoder: BinaryDecoder,
    @Volatile
    private var size: Int,
    private val topic: AvroTopic<GenericRecord, GenericRecord>,
    private val key: GenericRecord,
    private val valueReader: GenericDatumReader<GenericRecord>,
) : RecordData<GenericRecord, GenericRecord> {
    private var remaining: Int = size
    private var buffer: ByteBuffer? = null
    private var valueDecoder: BinaryDecoder? = null

    override fun getKey() = key

    override fun isEmpty() = size == 0

    override fun iterator(): MutableIterator<GenericRecord> {
        check(remaining != 0) { "Cannot read decoded record data twice." }

        return object : MutableIterator<GenericRecord> {
            override fun hasNext() = remaining > 0

            override fun next(): GenericRecord {
                if (!hasNext()) throw NoSuchElementException()

                val result = decodeValue(decoder)
                remaining--
                if (remaining == 0) {
                    remaining = decoder.arrayNext().toInt()
                    size += remaining
                }
                return result
            }

            override fun remove() {
                throw NotImplementedError()
            }
        }
    }

    private fun decodeValue(decoder: Decoder): GenericRecord {
        try {
            buffer = decoder.readBytes(buffer)
            return ByteBufferBackedInputStream(buffer).use { input ->
                valueDecoder = DecoderFactory.get().binaryDecoder(input, valueDecoder)
                valueReader.read(null, valueDecoder)
                    ?: throw HttpInvalidContentException("No record in data")
            }
        } catch (ex: IOException) {
            throw HttpInvalidContentException("Malformed record contents: ${ex.message}")
        }
    }

    override fun size() = size

    override fun getTopic() = topic
}
