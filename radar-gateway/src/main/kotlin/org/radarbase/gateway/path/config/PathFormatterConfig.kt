package org.radarbase.gateway.path.config

data class PathFormatterConfig(
    /** Format string. May include any variables computed by the configured plugins. */
    val format: String = DEFAULT_FORMAT,
    /**
     * Space separated list of plugins to use for formatting the format string. May include custom
     * class names.
     */
    val plugins: String = "fixed time",
    /** Additional plugin properties. */
    val properties: Map<String, String> = emptyMap(),
) {
    /**
     * Combine this config with given config. If no changes are made, just return the current
     * object.
     */
    fun copy(values: PathFormatterConfig): PathFormatterConfig {
        val copy = PathFormatterConfig(
            format = values.format,
            plugins = values.plugins,
            properties = buildMap(properties.size + values.properties.size) {
                putAll(properties)
                putAll(values.properties)
            },
        )
        return if (this == copy) this else copy
    }

    companion object {
        /** Default path format. */
        const val DEFAULT_FORMAT = "\${projectId}/\${userId}/\${topic}/\${time:yyyyMMdd_HH'00'}/\${filename}"
    }
}
