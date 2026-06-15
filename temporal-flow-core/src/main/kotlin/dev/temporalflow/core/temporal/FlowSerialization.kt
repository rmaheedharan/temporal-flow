package dev.temporalflow.core.temporal

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.reflect.KClass

internal object FlowSerialization {

    private val mapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun encode(value: Any): String = mapper.writeValueAsString(value)

    fun <T : Any> decode(json: String, type: KClass<T>): T =
        mapper.readValue(json, type.java)
}
