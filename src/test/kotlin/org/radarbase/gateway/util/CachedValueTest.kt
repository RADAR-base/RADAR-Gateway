package org.radarbase.gateway.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test
import java.time.Duration

internal class CachedValueTest {
    @Test
    fun exceptionTest() {
        val value = CachedValue<String>(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            throw IllegalStateException("Test")
        }
        assertThrows(IllegalStateException::class.java) { value.retrieve() }
    }

    @Test
    fun nextExceptionTest() {
        var first = true
        val value = CachedValue(Duration.ofSeconds(1), Duration.ofSeconds(1)) {
            if (first) {
                first = false
                throw IllegalStateException("Test")
            }
            else "String"
        }
        assertThrows(IllegalStateException::class.java) { value.retrieve() }
        assertThrows(IllegalStateException::class.java) { value.retrieve() }
    }


    @Test
    fun nextRetryExceptionTest() {
        var first = true
        val value = CachedValue(Duration.ofSeconds(1), Duration.ZERO) {
            if (first) {
                first = false
                throw IllegalStateException("Test")
            }
            else "String"
        }
        assertThrows(IllegalStateException::class.java) { value.retrieve() }
        assertEquals("String", value.retrieve())
    }
}
