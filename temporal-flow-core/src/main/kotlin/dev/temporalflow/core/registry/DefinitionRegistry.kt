package dev.temporalflow.core.registry

import java.util.concurrent.ConcurrentHashMap

/**
 * Registry pattern — thread-safe catalog of named definitions.
 * Consumers can register, look up, and enumerate definitions by name.
 */
class DefinitionRegistry<D : Any> {

    private val store = ConcurrentHashMap<String, D>()

    fun register(name: String, definition: D) {
        store[name] = definition
    }

    fun find(name: String): D? = store[name]

    fun require(name: String): D =
        find(name) ?: error("No definition registered for name '$name'")

    fun all(): List<D> = store.values.toList()

    fun isEmpty(): Boolean = store.isEmpty()
}
