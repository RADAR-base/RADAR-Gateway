package org.radarbase.gateway.path

import org.slf4j.LoggerFactory

class PathFormatter(
    private val format: String,
    private val plugins: List<PathFormatterPlugin>,
    checkMinimalDistinction: Boolean = true,
) {
    private val parameterLookups: Map<String, suspend PathFormatParameters.() -> String>

    init {
        require(format.isNotBlank()) { "Path format may not be an empty string" }
        val foundParameters = "\\$\\{([^}]*)}".toRegex().findAll(format).mapTo(HashSet()) { it.groupValues[1] }

        parameterLookups = buildMap {
            plugins.forEach { plugin ->
                putAll(
                    try {
                        plugin.createLookupTable(foundParameters)
                    } catch (ex: IllegalArgumentException) {
                        logger.error(
                            "Cannot parse path format {}, illegal format parameter found by plugin {}",
                            format,
                            plugin.name,
                            ex,
                        )
                        throw ex
                    },
                )
            }
        }

        val unsupportedParameters = foundParameters - parameterLookups.keys
        require(unsupportedParameters.isEmpty()) {
            val allowedFormats = plugins.map { it.allowedFormats }
            "Cannot use path format $format: unknown parameters $unsupportedParameters. Legal parameter names are parameters $allowedFormats"
        }

        if (checkMinimalDistinction) {
            require("topic" in parameterLookups) { "Path must include topic parameter." }
            require(
                "filename" in parameterLookups || ("extension" in parameterLookups && "attempt" in parameterLookups),
            ) {
                "Path must include filename parameter or extension and attempt parameters."
            }
        }
    }

    suspend fun format(
        parameters: PathFormatParameters,
    ): String = parameterLookups.asSequence().fold(format) { p, (name, lookup) ->
        p.replace("\${$name}", parameters.lookup())
    }

    override fun toString(): String = "PathFormatter{" + "format=$format," + "plugins=${plugins.map { it.name }}}"

    companion object {
        private val logger = LoggerFactory.getLogger(PathFormatter::class.java)
    }
}
