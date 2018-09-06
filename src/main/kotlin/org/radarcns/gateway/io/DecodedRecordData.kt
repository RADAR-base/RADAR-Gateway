package org.radarcns.gateway.io

import org.apache.avro.Schema
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.io.BinaryDecoder
import org.radarcns.data.RecordData
import org.radarcns.gateway.auth.Auth
import org.radarcns.producer.rest.ParsedSchemaMetadata
import org.radarcns.producer.rest.SchemaRetriever
import org.radarcns.topic.AvroTopic
import java.lang.UnsupportedOperationException

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

        size = decoder.readArrayStart().toInt()
        remaining = size

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
        setIfPresent(keyBuilder, schema, "projectId", projectId)
        setIfPresent(keyBuilder, schema, "userId", userId)
        setIfPresent(keyBuilder, schema, "sourceId", sourceId)
        return keyBuilder.build()
    }

    override fun getKey() = key

    override fun isEmpty() = size > 0

    override fun iterator(): MutableIterator<GenericRecord> {
        return object : MutableIterator<GenericRecord> {
            override fun hasNext() = remaining > 0

            override fun next(): GenericRecord {
                val result = readContext.decodeValue(decoder)
                remaining--
                if (remaining == 0) {
                    size = decoder.arrayNext().toInt()
                    remaining = size
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

    companion object {
        fun setIfPresent(builder: GenericRecordBuilder, schema: Schema, fieldName: String,
                value: String) {
            val field = schema.getField(fieldName) ?: return

            builder.set(field, value)
        }
    }
}
