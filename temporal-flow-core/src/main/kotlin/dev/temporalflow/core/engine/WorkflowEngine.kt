package dev.temporalflow.core.engine

import dev.temporalflow.core.flow.FlowDefinition
import dev.temporalflow.core.flow.RegisteredFlow
import dev.temporalflow.core.step.RegisteredStep
import dev.temporalflow.core.step.StepDefinition

/**
 * Strategy (GoF) — the engine contract for running steps and flows.
 *
 * Concrete strategies:
 *  - [SynchronousWorkflowEngine]  — runs everything inline; used in tests (no server required)
 *  - TemporalWorkflowEngine       — delegates to Temporal for durable, distributed execution
 *
 * Callers (MCP controller, HTTP controller, flow-of-flows) depend only on this interface
 * and never import engine-specific types.
 */
interface WorkflowEngine {

    /**
     * Executes a single step in isolation and returns its output.
     * Equivalent to a one-step flow — still durable in production engines.
     */
    fun <I : Any, O : Any> runStep(definition: StepDefinition<I, O>, input: I): O

    /**
     * Executes a complete flow and returns its output.
     * All step composition, parallelism, and context management defined in the flow body
     * are handled by the engine transparently.
     */
    fun <I : Any, O : Any> runFlow(definition: FlowDefinition<I, O>, input: I): O
}

fun <I : Any, O : Any> WorkflowEngine.runStep(step: RegisteredStep<I, O>, input: I): O =
    runStep(step.definition, input)

fun <I : Any, O : Any> WorkflowEngine.runFlow(flow: RegisteredFlow<I, O>, input: I): O =
    runFlow(flow.definition, input)
