package org.radarbase.gateway.path

import kotlinx.coroutines.runBlocking
import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.equalTo
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.radarcns.kafka.ObservationKey
import java.time.Instant

internal class PathFormatterTest {
    private lateinit var fixedPlugin: PathFormatterPlugin
    private lateinit var params: PathFormatParameters

    @BeforeEach
    fun setupRecord() {
        params = PathFormatParameters(
            topic = "my_topic",
            key = ObservationKey(
                "p",
                "u",
                "s",
            ),
            time = Instant.ofEpochMilli(1000),
            extension = ".csv",
        )
        fixedPlugin = FixedPathFormatterPlugin().create(mapOf())
    }

    @Test
    fun testDefaultPath() = runBlocking {
        val formatter = PathFormatter(
            format = DEFAULT_FORMAT,
            plugins = listOf(
                fixedPlugin,
                TimePathFormatterPlugin(),
            ),
        )

        val path = formatter.format(params)
        val regex = Regex("""p/u/my_topic/19700101_0000(_[a-f0-9\-]+)?\.csv""")
        assertTrue { regex.containsMatchIn(path) }
    }

    @Test
    fun testDefaultPathFewerPlugins() = runBlocking {
        val formatter = PathFormatter(
            format = DEFAULT_FORMAT,
            plugins = listOf(
                fixedPlugin,
            ),
        )
        val path = formatter.format(params)
        val regex = Regex("""p/u/my_topic/19700101_0000(_[a-f0-9\-]+)?\.csv""")
        assertTrue(regex.matches(path), "Path format is incorrect: $path")
    }

    @Test
    fun testDefaultPathNoTime() = runBlocking {
        val formatter = PathFormatter(
            format = DEFAULT_FORMAT,
            plugins = listOf(
                fixedPlugin,
            ),
        )
        assertThat(formatter.format(params.copy(time = null)), equalTo("p/u/my_topic/unknown-time.csv"))
    }

    @Test
    fun testDefaultPathWrongPlugins() {
        assertThrows(IllegalArgumentException::class.java) {
            PathFormatter(
                format = DEFAULT_FORMAT,
                plugins = listOf(
                    TimePathFormatterPlugin(),
                ),
            )
        }
    }

    @Test
    fun testCorrectTimeFormatPlugins() = runBlocking {
        val formatter = PathFormatter(
            format = "\${topic}/\${time:YYYY-MM-dd_HH:mm:ss}/\${filename}",
            plugins = listOf(
                fixedPlugin,
                TimePathFormatterPlugin(),
            ),
        )

        val regex = Regex("""my_topic/1970-01-01_000001/19700101_0000(_[a-f0-9\-]+)?\.csv""")
        val path = formatter.format(params)
        assertTrue(regex.matches(path), "Path format is incorrect: $path")
    }

    @Test
    fun testBadTimeFormatPlugins(): Unit = runBlocking {
        assertThrows(IllegalArgumentException::class.java) {
            PathFormatter(
                format = "\${topic}/\${time:VVV}\${attempt}\${extension}",
                plugins = listOf(
                    fixedPlugin,
                    TimePathFormatterPlugin(),
                ),
            )
        }
    }

    companion object {
        private const val DEFAULT_FORMAT = "\${projectId}/\${userId}/\${topic}/\${filename}"
    }
}
