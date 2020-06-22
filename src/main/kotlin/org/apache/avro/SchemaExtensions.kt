package org.apache.avro

import com.fasterxml.jackson.databind.JsonNode

// Circumvents class-local default value
val Schema.Field.jsonDefaultValue: JsonNode?
    get() = defaultValue()
