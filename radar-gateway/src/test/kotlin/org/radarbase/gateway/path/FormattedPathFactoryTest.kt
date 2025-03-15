package org.radarbase.gateway.path

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.radarbase.gateway.path.config.PathConfig
import org.radarbase.gateway.path.config.PathFormatterConfig
import org.radarcns.kafka.ObservationKey
import java.time.Instant

internal class FormattedPathFactoryTest {

    @Test
    fun testFormat() = runBlocking {
        val factory = createFactory(
            format = "\${topic}/\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}/\${filename}",
        )

        val t = Instant.parse("2021-12-02T10:05:00Z")

        val path = factory.relativePath(
            PathFormatParameters(
                topic = "t",
                key = ObservationKey(
                    "p",
                    "u",
                    "s",
                ),
                time = t,
                extension = ".csv.gz",
            ),
        )
        val regex = Regex("""t/p/u/s/202112/02/20211202_1000(_[a-f0-9\-]+)?\.csv\.gz""")
        assertTrue(regex.matches(path), "Path format is incorrect: $path")
    }

    @Test
    fun unparameterized() = runBlocking {
        val factory = FormattedPathFactory().apply {
            init(
                config = PathConfig(),
            )
        }
        val t = Instant.parse("2021-01-02T13:05:00Z")
        val path = factory.relativePath(
            PathFormatParameters(
                topic = "t",
                key = ObservationKey(
                    "p",
                    "u",
                    "s",
                ),
                time = t,
                extension = ".csv.gz",
            ),
        )
        val regex = Regex("""p/u/t/20210102_1300(_[a-f0-9\-]+)?\.csv\.gz""")
        assertTrue(regex.matches(path), "Path format is incorrect: $path")
    }

    @Test
    fun testMissingTopic() {
        assertThrows<IllegalArgumentException> {
            createFactory("\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}/\${filename}")
        }
    }

    @Test
    fun testMissingFilename() {
        assertThrows<IllegalArgumentException> {
            createFactory("\${topic}/\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}")
        }
    }

    @Test
    fun testUnknownParameter() {
        assertThrows<IllegalArgumentException> {
            createFactory("\${topic}/\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}/\${filename}\${unknown}")
        }
    }

    @Test
    fun testAttemptAndExtensionPresent() {
        createFactory("\${topic}/\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}/\${filename}/\${extension}")
        assertThrows<IllegalArgumentException> {
            createFactory("\${topic}/\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}/\${attempt}")
        }
        assertThrows<IllegalArgumentException> {
            createFactory("\${topic}/\${projectId}/\${userId}/\${sourceId}/\${time:yyyyMM}/\${time:dd}/\${extension}")
        }
    }

    private fun createFactory(format: String): FormattedPathFactory = FormattedPathFactory().apply {
        init(
            config = PathConfig(
                path = PathFormatterConfig(
                    format = format,
                ),
            ),
        )
    }
}
