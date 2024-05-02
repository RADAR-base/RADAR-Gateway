package org.radarbase.gateway.inject

import jakarta.ws.rs.NameBinding

/** Tag for additional processing required for incoming Avro data. */
@NameBinding
@Target(
    AnnotationTarget.CLASS,
    AnnotationTarget.FUNCTION,
    AnnotationTarget.PROPERTY_GETTER,
    AnnotationTarget.PROPERTY_SETTER,
)
@Retention(AnnotationRetention.RUNTIME)
annotation class ProcessAvro
