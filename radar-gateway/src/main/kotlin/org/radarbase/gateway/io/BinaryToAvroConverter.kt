package org.radarbase.gateway.io

import jakarta.ws.rs.core.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.apache.avro.Schema
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericDatumReader
import org.apache.avro.generic.GenericRecord
import org.apache.avro.generic.GenericRecordBuilder
import org.apache.avro.io.BinaryDecoder
import org.apache.avro.io.Decoder
import org.apache.avro.io.DecoderFactory
import org.radarbase.auth.authorization.EntityDetails
import org.radarbase.auth.authorization.Permission
import org.radarbase.auth.token.RadarToken
import org.radarbase.gateway.config.GatewayConfig
import org.radarbase.jersey.auth.AuthService
import org.radarbase.producer.schema.ParsedSchemaMetadata
import org.radarbase.producer.schema.SchemaRetriever
import org.radarbase.topic.AvroTopic
import java.io.InputStream

/** Converts binary input from a RecordSet to Kafka JSON. */
class BinaryToAvroConverter(
    @Context private val schemaRetriever: SchemaRetriever,
    @Context private val authService: AuthService,
    @Context private val config: GatewayConfig,
) {
    private var binaryDecoder: BinaryDecoder? = null

    private val genericData = GenericData().apply {
        isFastReaderEnabled = true
    }
    private var valueReader: GenericDatumReader<GenericRecord>? = null

    suspend fun process(topic: String, input: InputStream): AvroProcessingResult = coroutineScope {
        val decoder = DecoderFactory.get().binaryDecoder(input, binaryDecoder)
            .also { binaryDecoder = it }

        val token = authService.requestScopedToken()

        val metadata = decodeMetadata(decoder, topic, token)

        authService.checkPermission(
            Permission.MEASUREMENT_CREATE,
            if (config.auth.checkSourceId) metadata.authId else metadata.authId.copy(source = null),
            token,
            location = "POST $topic",
        )

        val recordData = DecodedRecordData(
            decoder,
            metadata.size,
            topic = AvroTopic(
                topic,
                metadata.keySchemaMetadata.schema,
                metadata.valueSchemaMetadata.schema,
                GenericRecord::class.java,
                GenericRecord::class.java,
            ),
            key = createKey(metadata.keySchemaMetadata.schema, metadata.authId),
            valueReader = valueReader?.takeIf { it.schema == metadata.valueSchemaMetadata.schema }
                ?: createDatumReader(metadata.valueSchemaMetadata.schema),
        )

        AvroProcessingResult(
            metadata.keySchemaMetadata.id,
            metadata.valueSchemaMetadata.id,
            records = withContext(Dispatchers.IO) {
                recordData
                    .takeWhile { isActive }
                    .map { Pair(recordData.key, it) }
            },
        )
    }

    private suspend fun decodeMetadata(decoder: Decoder, topic: String, token: RadarToken) = withContext(Dispatchers.IO) {
        val keyVersion = decoder.readInt()
        val valueVersion = decoder.readInt()

        val keySchemaMetadataJob = async {
            schemaRetriever.getByVersion(topic, false, keyVersion)
        }
        val valueSchemaMetadataJob = async {
            schemaRetriever.getByVersion(topic, true, valueVersion)
        }

        val authId = EntityDetails(
            project = if (decoder.readIndex() == 1) decoder.readString() else authService.activeParticipantProject(token),
            subject = if (decoder.readIndex() == 1) decoder.readString() else token.subject,
            source = decoder.readString(),
        )
        val size = decoder.readArrayStart().toInt()

        DecodedRecordDataMetadata(
            authId = authId,
            keySchemaMetadata = keySchemaMetadataJob.await(),
            valueSchemaMetadata = valueSchemaMetadataJob.await(),
            size = size,
        )
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDatumReader(schema: Schema) =
        (genericData.createDatumReader(schema) as GenericDatumReader<GenericRecord>)
            .also { valueReader = it }

    private data class DecodedRecordDataMetadata(
        val authId: EntityDetails,
        val keySchemaMetadata: ParsedSchemaMetadata,
        val valueSchemaMetadata: ParsedSchemaMetadata,
        val size: Int,
    )

    companion object {
        private fun createKey(
            schema: Schema,
            authId: EntityDetails,
        ): GenericRecord {
            return GenericRecordBuilder(schema).apply {
                schema.getField("projectId")?.let { set(it, authId.project) }
                schema.getField("userId")?.let { set(it, authId.subject) }
                schema.getField("sourceId")?.let { set(it, authId.source) }
            }.build()
        }
    }
}
