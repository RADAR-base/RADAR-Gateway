package org.radarbase.gateway.resource

import jakarta.inject.Singleton
import jakarta.ws.rs.GET
import jakarta.ws.rs.Path
import jakarta.ws.rs.Produces
import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_AVRO_NON_SPECIFIC
import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_AVRO_V1_JSON
import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_AVRO_V2_JSON
import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_AVRO_V3_JSON
import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_JSON

/** Root path, just forward requests without authentication. */
@Path("/")
@Singleton
class KafkaRoot {
    @GET
    @Produces(PRODUCE_AVRO_V1_JSON, PRODUCE_AVRO_V2_JSON, PRODUCE_AVRO_V3_JSON,
        PRODUCE_AVRO_NON_SPECIFIC, PRODUCE_JSON)
    fun root() = mapOf<String, String>()
}
