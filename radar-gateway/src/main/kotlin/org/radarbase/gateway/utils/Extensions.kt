package org.radarbase.gateway.utils

import org.radarbase.gateway.exception.InvalidFileDetailsException
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

/**
 * Ensures that all elements in the given list are non-null and not blank.
 *
 * @param names The list of strings to validate.
 * @param lazyMessage A lambda function that provides the error message if validation fails.
 * @throws InvalidFileDetailsException If any element in the list is null or blank.
 */
@OptIn(ExperimentalContracts::class)
fun requiresListNonNullOrBlank(names: List<String?>, lazyMessage: () -> String) {

    if (names.any { it.isNullOrBlank() }) {
        throw InvalidFileDetailsException(lazyMessage())
    }
}
