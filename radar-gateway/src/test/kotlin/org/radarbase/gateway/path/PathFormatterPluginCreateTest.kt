package org.radarbase.gateway.path

import org.hamcrest.MatcherAssert.assertThat
import org.hamcrest.Matchers.instanceOf
import org.hamcrest.Matchers.nullValue
import org.junit.jupiter.api.Test
import kotlin.reflect.jvm.jvmName

internal class PathFormatterPluginCreateTest {
    @Test
    fun testNamedPluginCreate() {
        assertThat("fixed".toPathFormatterPlugin(emptyMap()), instanceOf(PathFormatterPlugin::class.java))
        assertThat("time".toPathFormatterPlugin(emptyMap()), instanceOf(PathFormatterPlugin::class.java))
    }

    @Test
    fun testBadPluginCreate() {
        assertThat("unknown".toPathFormatterPlugin(emptyMap()), nullValue())
    }

    @Test
    fun testClassPathPluginCreate() {
        assertThat(
            FixedPathFormatterPlugin::class.jvmName.toPathFormatterPlugin(emptyMap()),
            instanceOf(PathFormatterPlugin::class.java),
        )
    }
}
