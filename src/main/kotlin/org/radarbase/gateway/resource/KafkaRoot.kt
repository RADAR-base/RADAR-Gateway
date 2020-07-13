package org.radarbase.gateway.resource

import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_AVRO_V1_JSON
import org.radarbase.gateway.resource.KafkaTopics.Companion.PRODUCE_JSON
import javax.inject.Singleton
import javax.ws.rs.GET
import javax.ws.rs.Path
import javax.ws.rs.Produces

/** Root path, just forward requests without authentication. */
@Path("/")
@Singleton
class KafkaRoot {
    @GET
    @Produces(PRODUCE_AVRO_V1_JSON, PRODUCE_JSON)
    fun root() = mapOf<String, String>()
}
