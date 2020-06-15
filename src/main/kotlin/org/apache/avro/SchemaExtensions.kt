package org.apache.avro

import com.fasterxml.jackson.databind.JsonNode

val Schema.Field.jsonDefaultValue: JsonNode?
    get() = defaultValue()
