package dev.temporalflow.core.engine

import dev.temporalflow.core.flow.SubFlowNode
import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.core.step.registerStep
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SynchronousWorkflowEngineTest {

    private val engine = SynchronousWorkflowEngine()

    data class MultiplyInput(val value: Int, val factor: Int)
    data class MultiplyOutput(val result: Int)
    data class AddInput(val a: Int, val b: Int)
    data class AddOutput(val sum: Int)
    data class PipelineInput(val value: Int)
    data class PipelineOutput(val result: Int)
    data class ParallelInput(val value: Int)
    data class ParallelOutput(val left: Int, val right: Int)
    data class InnerInput(val x: Int)
    data class InnerOutput(val doubled: Int)
    data class OuterInput(val x: Int)
    data class OuterOutput(val quadrupled: Int)
    data class ContextInput(val prefix: String, val value: Int)
    data class ContextOutput(val label: String)
    data class GI(val x: Int)
    data class GO(val y: Int)

    private val MultiplyStep = registerStep<MultiplyInput, MultiplyOutput>(
        name = "multiply", description = "Multiplies value by factor"
    ) { input -> MultiplyOutput(input.value * input.factor) }

    private val AddStep = registerStep<AddInput, AddOutput>(
        name = "add", description = "Adds two integers"
    ) { input -> AddOutput(input.a + input.b) }

    private val SequentialFlow = registerFlow<PipelineInput, PipelineOutput>(
        name = "sequential-test", description = ""
    ) {
        val multiplied = addStep(MultiplyStep) {
            input { MultiplyInput(flowInput.value, 3) }
        }
        val added = addStep(AddStep) {
            dependsOn = setOf(multiplied)
            input { AddInput(outputOf(multiplied).result, 2) }
        }
        output { PipelineOutput(outputOf(added).sum) }
    }

    private val ParallelFlow = registerFlow<ParallelInput, ParallelOutput>(
        name = "parallel-test", description = ""
    ) {
        val left = addStep(MultiplyStep) {
            input { MultiplyInput(flowInput.value, 2) }
        }
        val right = addStep(MultiplyStep) {
            input { MultiplyInput(flowInput.value, 3) }
        }
        output { ParallelOutput(outputOf(left).result, outputOf(right).result) }
    }

    private val InnerFlow = registerFlow<InnerInput, InnerOutput>(
        name = "inner-flow-test", description = ""
    ) {
        val multiplied = addStep(MultiplyStep) {
            input { MultiplyInput(flowInput.x, 2) }
        }
        output { InnerOutput(outputOf(multiplied).result) }
    }

    private val OuterFlow = registerFlow<OuterInput, OuterOutput>(
        name = "outer-flow-test", description = ""
    ) {
        val firstDouble = addSubFlow(InnerFlow) {
            input { InnerInput(flowInput.x) }
        }
        val secondDouble = addSubFlow(InnerFlow) {
            dependsOn = setOf(firstDouble)
            input { InnerInput(outputOf(firstDouble).doubled) }
        }
        output { OuterOutput(outputOf(secondDouble).doubled) }
    }

    private val ContextFlow = registerFlow<ContextInput, ContextOutput>(
        name = "context-test", description = ""
    ) {
        val multiplied = addStep(MultiplyStep) {
            input { MultiplyInput(flowInput.value, 2) }
        }
        output { ContextOutput("${flowInput.prefix}-${outputOf(multiplied).result}") }
    }

    private val GraphFlow = registerFlow<GI, GO>(
        name = "graph-inspect-test", description = ""
    ) {
        val first = addStep(MultiplyStep) {
            input { MultiplyInput(flowInput.x, 2) }
        }
        val second = addStep(AddStep) {
            dependsOn = setOf(first)
            input { AddInput(outputOf(first).result, 1) }
        }
        output { GO(outputOf(second).sum) }
    }

    @Nested
    @DisplayName("runStep")
    inner class RunStep {
        @Test
        fun `executes the step handler and returns typed output`() {
            assertEquals(42, engine.runStep(MultiplyStep, MultiplyInput(6, 7)).result)
        }

        @Test
        fun `passes input through to handler unchanged`() {
            assertEquals(42, engine.runStep(AddStep, AddInput(10, 32)).sum)
        }
    }

    @Nested
    @DisplayName("runFlow — sequential steps with output chaining")
    inner class RunFlowSequential {
        @Test
        fun `chains steps — output of one feeds input of the next`() {
            // 4 * 3 = 12, 12 + 2 = 14
            assertEquals(14, engine.runFlow(SequentialFlow, PipelineInput(4)).result)
        }
    }

    @Nested
    @DisplayName("runFlow — parallel execution via absent dependsOn")
    inner class RunFlowParallel {
        @Test
        fun `executes both branches and returns both results`() {
            val result = engine.runFlow(ParallelFlow, ParallelInput(5))
            assertEquals(10, result.left)
            assertEquals(15, result.right)
        }
    }

    @Nested
    @DisplayName("runFlow — subFlow composition")
    inner class RunFlowSubFlowComposition {
        @Test
        fun `composes sub-flows and threads data correctly`() {
            assertEquals(12, engine.runFlow(OuterFlow, OuterInput(3)).quadrupled)
        }

        @Test
        fun `repeated sub-flow gets a unique node id to distinguish the two uses`() {
            assertEquals(
                listOf("inner-flow-test", "inner-flow-test[1]"),
                OuterFlow.definition.nodes.filterIsInstance<SubFlowNode<*, *, *>>().map { it.id }
            )
        }
    }

    @Nested
    @DisplayName("runFlow — flow input accessible throughout all steps")
    inner class RunFlowContext {
        @Test
        fun `flow input remains in scope after steps run`() {
            assertEquals("ORDER-10", engine.runFlow(ContextFlow, ContextInput("ORDER", 5)).label)
        }
    }

    @Nested
    @DisplayName("graph introspection — nodes and edges")
    inner class GraphIntrospection {
        @Test
        fun `nodes list reflects declaration order`() {
            assertEquals(listOf("multiply", "add"), GraphFlow.definition.nodes.map { it.displayName })
        }

        @Test
        fun `edges reflect dependsOn declarations`() {
            assertEquals(listOf("multiply" to "add"), GraphFlow.definition.edges)
        }

        @Test
        fun `steps derived view lists atomic steps`() {
            assertEquals(listOf("multiply", "add"), GraphFlow.definition.steps.map { it.name })
        }
    }

    @Nested
    @DisplayName("step with zero values")
    inner class ZeroValues {
        @Test
        fun `never returns null — zero is a valid output`() {
            val result = engine.runStep(MultiplyStep, MultiplyInput(0, 0))
            assertNotNull(result)
            assertEquals(0, result.result)
        }
    }
}
