package org.radarbase.gateway.utils

import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

/**
 * Ensures that the given [data] is not `null` or blank.
 *
 * If the [data] is `null` or blank, an [IllegalArgumentException] is thrown with the provided [message].
 *
 * @param T The type of the character sequence, which can be nullable.
 * @param data The character sequence to validate.
 * @param message A lambda providing the exception message if validation fails.
 * @return The same [data] if it is not `null` and not blank.
 * @throws IllegalArgumentException if [data] is `null` or blank.
 */
@OptIn(ExperimentalContracts::class)
fun <T: CharSequence?>requireNotNullAndBlank(data: T, message: () -> String): T {

    contract {
        returns() implies (data != null)
    }

    if (data != null && !data.isBlank()) {
        return data
    }
    throw IllegalArgumentException(message())
}
