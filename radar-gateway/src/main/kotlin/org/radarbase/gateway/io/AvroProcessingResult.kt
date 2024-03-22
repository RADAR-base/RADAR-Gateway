package org.radarbase.gateway.io

import org.apache.avro.generic.GenericRecord

data class AvroProcessingResult(
    val keySchemaId: Int,
    val valueSchemaId: Int,
    val records: List<Pair<GenericRecord, GenericRecord>>,
)
