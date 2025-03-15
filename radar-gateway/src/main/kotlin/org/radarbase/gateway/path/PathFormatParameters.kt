package org.radarbase.gateway.path

import org.apache.avro.generic.GenericRecord
import java.time.Instant

data class PathFormatParameters(
    val topic: String,
    val key: GenericRecord,
    val time: Instant?,
    val extension: String,
)
