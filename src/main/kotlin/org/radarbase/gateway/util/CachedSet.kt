package org.radarbase.gateway.util

import java.time.Duration
import java.time.Instant

class CachedSet<in T>(
        private val refreshDuration: Duration,
        private val retryDuration: Duration,
        private val supplier: () -> Iterable<T>) {

    private var cached: Set<T> = emptySet()
    private var lastFetch: Instant = Instant.MIN

    fun contains(value: T): Boolean {
        val now = Instant.now()
        val (localSet, mustRefresh, mayRetry) = synchronized(this) {
            Triple(cached,
                    now.isAfter(lastFetch.plus(refreshDuration)),
                    now.isAfter(lastFetch.plus(retryDuration)))
        }

        val containsValue = localSet.contains(value)

        return if (mustRefresh || (!containsValue && mayRetry)) {
            val updatedSet = supplier.invoke().toSet()
            synchronized(this) {
                cached = updatedSet
                lastFetch = Instant.now()
            }
            updatedSet.contains(value)
        } else {
            containsValue
        }
    }
}
