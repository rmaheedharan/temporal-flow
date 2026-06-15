package dev.temporalflow.core.temporal

import io.temporal.client.WorkflowClient
import io.temporal.worker.WorkerFactory

/**
 * Manages the lifecycle of the Temporal worker process.
 *
 * Registers the generic [FlowExecutorWorkflow] and [StepExecutorWorkflow] implementations
 * along with the [TemporalStepExecutorActivity], then starts polling the task queue.
 *
 * Framework integrations (e.g. temporal-flow-micronaut) call [start] on server startup
 * and [stop] on shutdown — this class has no framework dependency itself.
 */
class TemporalWorker(
    private val client: WorkflowClient,
    private val taskQueue: String
) {
    private lateinit var factory: WorkerFactory

    fun start() {
        factory = WorkerFactory.newInstance(client)
        val worker = factory.newWorker(taskQueue)

        worker.registerWorkflowImplementationTypes(
            TemporalFlowExecutorWorkflow::class.java,
            TemporalStepExecutorWorkflow::class.java
        )
        worker.registerActivitiesImplementations(DynamicStepExecutorActivity())

        factory.start()
    }

    fun stop() {
        if (::factory.isInitialized) {
            factory.shutdown()
        }
    }
}
