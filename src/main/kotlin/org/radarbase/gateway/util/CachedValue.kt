package org.radarbase.gateway.util

import java.time.Duration
import java.time.Instant

class CachedValue<T: Any>(
        private val refreshDuration: Duration,
        private val retryDuration: Duration,
        private val supplier: () -> T) {

    private var cached: T? = null
    private var exception: Exception? = null
    private var nextRefresh: Instant = Instant.MIN
    private var nextRetry: Instant = Instant.MIN

    /** Age of cache has twice exceeded the refresh duration. */
    val isStale: Boolean
        get() = synchronized(this) { nextRefresh }.let { threshold ->
            val now = Instant.now()
            threshold < now && Duration.between(threshold, now) > refreshDuration
        }

    fun <V> compute(computation: (T) -> V, successPredicate: (V) -> Boolean): V {
        val now = Instant.now()
        val (localValue, mustRefresh, mayRetry, localException) = synchronized(this) {
            CurrentStatus(cached, now > nextRefresh, now > nextRetry, exception)
        }

        if (localException != null) {
            if (mayRetry) return computation(update(now))
            else throw localException
        }

        check(localValue != null || mayRetry) { "No state or exception is available" }

        return if (mustRefresh || localValue == null) {
            computation(update(now))
        } else {
            val result = computation(localValue)
            if (!successPredicate(result) && mayRetry) {
                computation(update(now))
            } else result
        }
    }

    private fun update(now: Instant): T {
        try {
            val updatedValue = supplier()
            synchronized(this) {
                cached = updatedValue
                exception = null
                nextRefresh = now.plus(refreshDuration)
                nextRetry = now.plus(retryDuration)
            }
            return updatedValue
        } catch (ex: Exception) {
            synchronized(this) {
                cached = null
                exception = ex
                nextRefresh = now.plus(refreshDuration)
                nextRetry = now.plus(retryDuration)
            }
            throw ex
        }
    }

    fun retrieve(): T = compute({ it }, { true })

    data class CurrentStatus<T>(
            val result: T?,
            val mustRefresh: Boolean,
            val mustRetry: Boolean,
            val exception: Exception?)
}
