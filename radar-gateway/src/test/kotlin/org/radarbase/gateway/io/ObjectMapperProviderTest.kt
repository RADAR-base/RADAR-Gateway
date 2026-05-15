package org.radarbase.gateway.io

import com.fasterxml.jackson.core.JsonParseException
import com.fasterxml.jackson.core.StreamReadFeature
import com.fasterxml.jackson.databind.ObjectMapper
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.hamcrest.MatcherAssert.assertThat
import org.junit.jupiter.api.Test

class ObjectMapperProviderTest {
    @Test
    fun testIncludeSourceInLocation() {
        val provider = ObjectMapperProvider()
        val mapper = provider.getContext(ObjectMapper::class.java)

        val invalidJson = "{\"a\": 1"
        try {
            mapper.readTree(invalidJson)
        } catch (ex: JsonParseException) {
            val location = ex.location
            assertThat(location, notNullValue())
            // If INCLUDE_SOURCE_IN_LOCATION is enabled, the source reference should be present.
            // In Jackson 2.13+, it's often a ContentReference.
            assertThat(mapper.tokenStreamFactory().isEnabled(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION.mappedFeature()), `is`(true))
            assertThat(location.contentReference().rawContent, notNullValue())
        }
    }
}
