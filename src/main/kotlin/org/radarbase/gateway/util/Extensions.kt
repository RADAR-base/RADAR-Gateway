package org.radarbase.gateway.util

import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.Future
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Suppress("BlockingMethodInNonBlockingContext")
suspend inline fun <T> Future<T>.toCoroutine(
    crossinline retrieve: Future<T>.() -> T = { get() },
): T = suspendCancellableCoroutine { continuation ->
    continuation.invokeOnCancellation { cancel(true) }
    try {
        continuation.resume(retrieve())
    } catch (ex: InterruptedException) {
        continuation.cancel()
    } catch (ex: Throwable) {
        continuation.resumeWithException(ex)
    }
}
