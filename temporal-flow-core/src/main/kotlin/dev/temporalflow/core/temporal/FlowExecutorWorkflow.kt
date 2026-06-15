package dev.temporalflow.core.temporal

import dev.temporalflow.core.flow.FlowDefinition
import dev.temporalflow.core.flow.FlowNode
import dev.temporalflow.core.flow.FlowNodeContext
import dev.temporalflow.core.flow.FlowRegistry
import dev.temporalflow.core.flow.StepNode
import dev.temporalflow.core.flow.SubFlowNode
import io.temporal.activity.ActivityOptions
import io.temporal.common.RetryOptions
import io.temporal.workflow.ActivityStub
import io.temporal.workflow.Async
import io.temporal.workflow.ChildWorkflowOptions
import io.temporal.workflow.Promise
import io.temporal.workflow.Workflow
import io.temporal.workflow.WorkflowInterface
import io.temporal.workflow.WorkflowMethod
import java.time.Duration

/**
 * Generic Temporal workflow that executes any registered [FlowDefinition] by name.
 *
 * A single concrete workflow type handles ALL flows — the flow name routes execution
 * to the correct definition in [FlowRegistry] at runtime.
 */
@WorkflowInterface
internal interface FlowExecutorWorkflow {
    @WorkflowMethod
    fun execute(flowName: String, inputJson: String): String
}

internal class TemporalFlowExecutorWorkflow : FlowExecutorWorkflow {

    private val defaultActivityOptions: ActivityOptions = ActivityOptions.newBuilder()
        .setStartToCloseTimeout(Duration.ofSeconds(60))
        .setRetryOptions(
            RetryOptions.newBuilder()
                .setMaximumAttempts(3)
                .setInitialInterval(Duration.ofSeconds(1))
                .setBackoffCoefficient(2.0)
                .build()
        )
        .build()

    // Activity type name = step name → each step appears by name in the Temporal UI
    private fun stepStub(): ActivityStub = Workflow.newUntypedActivityStub(defaultActivityOptions)

    override fun execute(flowName: String, inputJson: String): String {
        val definition = FlowRegistry.require(flowName)
        @Suppress("UNCHECKED_CAST")
        val typed = definition as FlowDefinition<Any, Any>
        val input = FlowSerialization.decode(inputJson, typed.inputClass)
        val output = executeGraph(typed, input)
        return FlowSerialization.encode(output)
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeGraph(definition: FlowDefinition<Any, Any>, input: Any): Any {
        val ctx = FlowNodeContext(input)
        val remaining = definition.nodes.toMutableList<FlowNode<Any, *>>()

        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { node -> node.dependsOn.none { it in remaining } }
            check(ready.isNotEmpty()) { "Cycle detected in flow '${definition.name}'" }

            if (ready.size == 1) {
                val node = ready.first() as FlowNode<Any, Any>
                ctx.record(node, executeNodeTemporal(node, ctx))
                remaining.remove(node)
            } else {
                // Parallel wave: schedule all ready nodes concurrently via Async.function.
                // Input is computed eagerly (reads previous-wave outputs already in ctx)
                // before the async dispatch so each branch captures its own immutable input.
                val scheduled: List<Pair<FlowNode<Any, *>, Promise<String>>> = ready.map { node ->
                    node as FlowNode<Any, Any>
                    val encodedInput = FlowSerialization.encode(node.inputFn(ctx))
                    val promise: Promise<String> = when (node) {
                        is StepNode<*, *, *> -> {
                            val typedNode = node as StepNode<Any, Any, Any>
                            val stub = stepStub()
                            Async.function { stub.execute(typedNode.step.name, String::class.java, encodedInput) }
                        }
                        is SubFlowNode<*, *, *> -> {
                            val typedNode = node as SubFlowNode<Any, Any, Any>
                            val childStub = Workflow.newChildWorkflowStub(
                                FlowExecutorWorkflow::class.java,
                                ChildWorkflowOptions.newBuilder()
                                    .setWorkflowId("${typedNode.subFlow.name}:${Workflow.randomUUID()}")
                                    .build()
                            )
                            Async.function { childStub.execute(typedNode.subFlow.name, encodedInput) }
                        }
                    }
                    Pair(node, promise)
                }

                for ((node, promise) in scheduled) {
                    node as FlowNode<Any, Any>
                    val encodedResult = promise.get()
                    val output: Any = when (node) {
                        is StepNode<*, *, *> ->
                            FlowSerialization.decode(encodedResult, (node as StepNode<Any, Any, Any>).step.outputClass)
                        is SubFlowNode<*, *, *> ->
                            FlowSerialization.decode(encodedResult, (node as SubFlowNode<Any, Any, Any>).subFlow.outputClass)
                    }
                    ctx.record(node, output)
                    remaining.remove(node)
                }
            }
        }

        @Suppress("UNCHECKED_CAST")
        return (definition.outputFn as FlowNodeContext<Any>.() -> Any)(ctx)
    }

    @Suppress("UNCHECKED_CAST")
    private fun executeNodeTemporal(node: FlowNode<Any, Any>, ctx: FlowNodeContext<Any>): Any {
        val encodedInput = FlowSerialization.encode(node.inputFn(ctx))
        return when (node) {
            is StepNode<*, *, *> -> {
                val typedNode = node as StepNode<Any, Any, Any>
                val encodedResult = stepStub().execute(typedNode.step.name, String::class.java, encodedInput)
                FlowSerialization.decode(encodedResult, typedNode.step.outputClass)
            }
            is SubFlowNode<*, *, *> -> {
                val typedNode = node as SubFlowNode<Any, Any, Any>
                val childStub = Workflow.newChildWorkflowStub(
                    FlowExecutorWorkflow::class.java,
                    ChildWorkflowOptions.newBuilder()
                        .setWorkflowId("${typedNode.subFlow.name}:${Workflow.randomUUID()}")
                        .build()
                )
                val encodedResult = childStub.execute(typedNode.subFlow.name, encodedInput)
                FlowSerialization.decode(encodedResult, typedNode.subFlow.outputClass)
            }
        }
    }
}
