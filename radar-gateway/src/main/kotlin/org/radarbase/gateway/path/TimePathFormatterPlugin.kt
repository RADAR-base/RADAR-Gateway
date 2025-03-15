package org.radarbase.gateway.path

import org.radarbase.gateway.path.RecordPathFactory.Companion.sanitizeId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class TimePathFormatterPlugin : PathFormatterPlugin() {
    override val prefix = "time"

    override val allowedFormats: String = "time:YYYY-mm-dd"

    override fun lookup(parameterContents: String): suspend PathFormatParameters.() -> String {
        val dateFormatter = DateTimeFormatter
            .ofPattern(parameterContents)
            .withZone(ZoneOffset.UTC)

        return {
            sanitizeId(
                time?.let { dateFormatter.format(it) },
                "unknown-time",
            )
        }
    }
}
