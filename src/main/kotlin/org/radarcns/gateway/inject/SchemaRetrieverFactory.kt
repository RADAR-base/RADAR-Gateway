package org.radarcns.gateway.inject

import org.radarbase.config.ServerConfig
import org.radarbase.producer.rest.SchemaRetriever
import org.radarcns.gateway.Config
import java.util.function.Supplier
import javax.ws.rs.core.Context

/** Creates a Schema Retriever based on the current schema registry configuration. */
class SchemaRetrieverFactory: Supplier<SchemaRetriever> {
    @Context
    private lateinit var config: Config

    override fun get(): SchemaRetriever {
        return SchemaRetriever(ServerConfig(config.schemaRegistryUrl), 30)
    }
}
