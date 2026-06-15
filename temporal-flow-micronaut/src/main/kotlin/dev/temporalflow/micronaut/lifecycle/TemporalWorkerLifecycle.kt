package dev.temporalflow.micronaut.lifecycle

import dev.temporalflow.core.temporal.TemporalWorker
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.runtime.server.event.ServerStartupEvent
import jakarta.inject.Singleton

/**
 * Bridges Micronaut's server lifecycle to [TemporalWorker].
 * Starts the Temporal worker after the HTTP server is ready.
 */
@Singleton
class TemporalWorkerLifecycle(
    private val worker: TemporalWorker
) : ApplicationEventListener<ServerStartupEvent> {

    override fun onApplicationEvent(event: ServerStartupEvent) {
        worker.start()
    }
}
