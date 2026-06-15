package dev.temporalflow.core.temporal

import dev.temporalflow.core.engine.WorkflowEngine
import dev.temporalflow.core.flow.FlowDefinition
import dev.temporalflow.core.step.StepDefinition
import io.temporal.client.WorkflowClient
import io.temporal.client.WorkflowOptions
import java.util.UUID

/**
 * Adapter (GoF) — bridges [WorkflowEngine] to Temporal's [WorkflowClient].
 *
 * Both [runStep] and [runFlow] create isolated Temporal workflow executions,
 * giving every invocation its own durable execution history, retry behaviour,
 * and visibility in the Temporal UI.
 *
 * Consumers depend on [WorkflowEngine], not on this class — Temporal is an implementation detail.
 */
class TemporalWorkflowEngine(
    private val client: WorkflowClient,
    private val taskQueue: String
) : WorkflowEngine {

    override fun <I : Any, O : Any> runStep(definition: StepDefinition<I, O>, input: I): O {
        val stub = client.newWorkflowStub(
            StepExecutorWorkflow::class.java,
            workflowOptions("step:${definition.name}:${UUID.randomUUID()}")
        )
        val result = stub.execute(definition.name, FlowSerialization.encode(input))
        return FlowSerialization.decode(result, definition.outputClass)
    }

    override fun <I : Any, O : Any> runFlow(definition: FlowDefinition<I, O>, input: I): O {
        val stub = client.newWorkflowStub(
            FlowExecutorWorkflow::class.java,
            workflowOptions("flow:${definition.name}:${UUID.randomUUID()}")
        )
        val result = stub.execute(definition.name, FlowSerialization.encode(input))
        return FlowSerialization.decode(result, definition.outputClass)
    }

    private fun workflowOptions(id: String) = WorkflowOptions.newBuilder()
        .setWorkflowId(id)
        .setTaskQueue(taskQueue)
        .build()
}
