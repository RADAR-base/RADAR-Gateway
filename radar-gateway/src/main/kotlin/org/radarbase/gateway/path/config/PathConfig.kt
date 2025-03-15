package org.radarbase.gateway.path.config

import org.radarbase.gateway.path.RecordPathFactory
import org.radarbase.gateway.utils.constructClass

data class PathConfig(
    override val factory: String = "org.radarbase.gateway.path.FormattedPathFactory",
    /** Path formatting rules. */
    val path: PathFormatterConfig = PathFormatterConfig(),
) : PluginConfig {
    fun createFactory(
        extension: String,
    ): RecordPathFactory {
        val pathFactory = factory.constructClass<RecordPathFactory>()

        // Pass any properties from the given PathConfig to the PathFormatterConfig for the factory.
        // Properties passed in the PathConfig.path.properties take precedent
        val pathProperties = buildMap {
            putAll(path.properties)
        }

        val pathFormatterConfig = path.copy(properties = pathProperties)
        val pathConfig = copy(path = pathFormatterConfig)

        pathFactory.init(
            config = pathConfig,
        )

        return pathFactory
    }
}
