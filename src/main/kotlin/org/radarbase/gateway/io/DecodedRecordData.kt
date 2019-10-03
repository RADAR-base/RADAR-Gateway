package org.radarbase.gateway.io

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.io.BinaryDecoder
import org.radarbase.data.RecordData
import org.radarbase.producer.rest.ParsedSchemaMetadata
import org.radarbase.producer.rest.SchemaRetriever
import org.radarbase.topic.AvroTopic
import org.radarbase.gateway.auth.Auth

class DecodedRecordData(
        topicName: String,
        private val decoder: BinaryDecoder,
        schemaRetriever: SchemaRetriever,
        auth: Auth,
        private val readContext: BinaryToAvroConverter.ReadContext) : RecordData<GenericRecord, GenericRecord> {

    private val key: GenericRecord
    private var size: Int
    private var remaining: Int
    private val topic: AvroTopic<GenericRecord, GenericRecord>

    val keySchemaMetadata: ParsedSchemaMetadata
    val valueSchemaMetadata: ParsedSchemaMetadata

    init {
        val keyVersion = decoder.readInt()
        val valueVersion = decoder.readInt()
        val projectId = if (decoder.readIndex() == 1) decoder.readString() else auth.defaultProject
        val userId = if (decoder.readIndex() == 1) decoder.readString() else auth.userId
        val sourceId = decoder.readString()

        auth.checkPermission(projectId, userId, sourceId)

        remaining = decoder.readArrayStart().toInt()
        size = remaining

        keySchemaMetadata = schemaRetriever.getSchemaMetadata(topicName, false, keyVersion)
        valueSchemaMetadata = schemaRetriever.getSchemaMetadata(topicName, true, valueVersion)

        topic = AvroTopic(topicName, keySchemaMetadata.schema, valueSchemaMetadata.schema,
                GenericRecord::class.java, GenericRecord::class.java)

        key = createKey(keySchemaMetadata.schema, projectId!!, userId!!, sourceId)
        readContext.init(valueSchemaMetadata.schema)
    }

    private fun createKey(schema: Schema, projectId: String, userId: String, sourceId: String):
            GenericRecord {
        val keyBuilder = GenericRecordBuilder(schema)
        schema.getField("projectId")?.let { keyBuilder.set(it, projectId) }
        schema.getField("userId")?.let { keyBuilder.set(it, userId) }
        schema.getField("sourceId")?.let { keyBuilder.set(it, sourceId) }
        return keyBuilder.build()
    }

    override fun getKey() = key

    override fun isEmpty() = size == 0

    override fun iterator(): MutableIterator<GenericRecord> {
        if (remaining == 0)
            throw IllegalStateException("Cannot read decoded record data twice.")

        return object : MutableIterator<GenericRecord> {
            override fun hasNext() = remaining > 0

            override fun next(): GenericRecord {
                val result = readContext.decodeValue(decoder)
                remaining--
                if (remaining == 0) {
                    remaining = decoder.arrayNext().toInt()
                    size += remaining
                }
                return result
            }

            override fun remove() {
                throw UnsupportedOperationException()
            }
        }
    }

    override fun size() = size

    override fun getTopic() = topic
}
