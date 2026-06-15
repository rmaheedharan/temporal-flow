package dev.temporalflow.core.engine

import dev.temporalflow.core.flow.FlowDefinition
import dev.temporalflow.core.flow.FlowNode
import dev.temporalflow.core.flow.FlowNodeContext
import dev.temporalflow.core.flow.StepNode
import dev.temporalflow.core.flow.SubFlowNode
import dev.temporalflow.core.step.StepDefinition

/**
 * Synchronous, in-process [WorkflowEngine] — executes everything inline on the calling thread.
 *
 * Intended for unit tests and local development: no Temporal server, no network, instant execution.
 * The graph is traversed topologically; nodes with no mutual dependency are executed in declaration
 * order (the sync engine does not exploit parallelism, but the composition structure is preserved).
 */
class SynchronousWorkflowEngine : WorkflowEngine {

    override fun <I : Any, O : Any> runStep(definition: StepDefinition<I, O>, input: I): O =
        definition.handler(input)

    override fun <I : Any, O : Any> runFlow(definition: FlowDefinition<I, O>, input: I): O {
        val ctx = FlowNodeContext(input)
        val remaining = definition.nodes.toMutableList()

        while (remaining.isNotEmpty()) {
            val ready = remaining.filter { node -> node.dependsOn.none { it in remaining } }
            check(ready.isNotEmpty()) { "Cycle detected in flow '${definition.name}'" }
            for (node in ready) {
                @Suppress("UNCHECKED_CAST")
                ctx.record(node, executeNode(node as FlowNode<I, Any>, ctx))
                remaining.remove(node)
            }
        }

        return definition.outputFn(ctx)
    }

    @Suppress("UNCHECKED_CAST")
    private fun <I : Any> executeNode(node: FlowNode<I, Any>, ctx: FlowNodeContext<I>): Any =
        when (node) {
            is StepNode<*, *, *> -> {
                val typed = node as StepNode<I, Any, Any>
                runStep(typed.step, typed.inputFn(ctx))
            }
            is SubFlowNode<*, *, *> -> {
                val typed = node as SubFlowNode<I, Any, Any>
                runFlow(typed.subFlow, typed.inputFn(ctx))
            }
        }
}
