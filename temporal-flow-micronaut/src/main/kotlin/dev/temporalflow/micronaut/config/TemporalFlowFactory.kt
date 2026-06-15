package dev.temporalflow.micronaut.config

import dev.temporalflow.core.temporal.TemporalWorker
import dev.temporalflow.core.temporal.TemporalWorkflowEngine
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Value
import io.temporal.client.WorkflowClient
import io.temporal.serviceclient.WorkflowServiceStubs
import io.temporal.serviceclient.WorkflowServiceStubsOptions
import jakarta.inject.Singleton

/**
 * Factory (GoF + Micronaut @Factory) — wires Temporal infrastructure into the DI container.
 *
 * Consumers inject [TemporalWorkflowEngine] or [dev.temporalflow.core.engine.WorkflowEngine]
 * and remain unaware of connection details.
 */
@Factory
class TemporalFlowFactory {

    @Singleton
    fun workflowServiceStubs(
        @Value("\${temporal.server-url:127.0.0.1:7233}") serverUrl: String
    ): WorkflowServiceStubs =
        WorkflowServiceStubs.newServiceStubs(
            WorkflowServiceStubsOptions.newBuilder()
                .setTarget(serverUrl)
                .build()
        )

    @Singleton
    fun workflowClient(stubs: WorkflowServiceStubs): WorkflowClient =
        WorkflowClient.newInstance(stubs)

    @Singleton
    fun temporalWorkflowEngine(
        client: WorkflowClient,
        @Value("\${temporal.task-queue:default}") taskQueue: String
    ): TemporalWorkflowEngine =
        TemporalWorkflowEngine(client, taskQueue)

    @Singleton
    fun temporalWorker(
        client: WorkflowClient,
        @Value("\${temporal.task-queue:default}") taskQueue: String
    ): TemporalWorker =
        TemporalWorker(client, taskQueue)
}
