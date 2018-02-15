package org.radarcns.gateway.util

import org.glassfish.jersey.internal.inject.DisposableSupplier
import java.util.*

class ByteArrayPool(private val byteArraySize: Int = 64 * 1024, private val maxPoolSize: Int = 10):
        DisposableSupplier<ByteArray> {
    private val pool: ArrayDeque<ByteArray> = ArrayDeque(maxPoolSize)

    override fun dispose(instance: ByteArray?) {
        if (instance == null || instance.size != byteArraySize) {
            return
        }

        synchronized(pool) {
            if (pool.size < maxPoolSize) {
                pool.addLast(instance)
            }
        }
    }

    override fun get(): ByteArray {
        synchronized(pool) {
            if (pool.isNotEmpty()) {
                return pool.removeLast()
            }
        }
        return ByteArray(byteArraySize)
    }
}