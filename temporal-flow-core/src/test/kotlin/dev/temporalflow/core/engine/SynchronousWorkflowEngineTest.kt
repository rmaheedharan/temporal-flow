package dev.temporalflow.core.engine

import dev.temporalflow.core.flow.SubFlowNode
import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.core.step.registerStep
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class SynchronousWorkflowEngineTest : DescribeSpec({

    val engine = SynchronousWorkflowEngine()

    data class MultiplyInput(val value: Int, val factor: Int)
    data class MultiplyOutput(val result: Int)

    data class AddInput(val a: Int, val b: Int)
    data class AddOutput(val sum: Int)

    val MultiplyStep = registerStep<MultiplyInput, MultiplyOutput>(
        name = "multiply", description = "Multiplies value by factor"
    ) { input -> MultiplyOutput(input.value * input.factor) }

    val AddStep = registerStep<AddInput, AddOutput>(
        name = "add", description = "Adds two integers"
    ) { input -> AddOutput(input.a + input.b) }

    describe("runStep") {
        it("executes the step handler and returns typed output") {
            engine.runStep(MultiplyStep, MultiplyInput(6, 7)).result shouldBe 42
        }

        it("passes input through to handler unchanged") {
            engine.runStep(AddStep, AddInput(10, 32)).sum shouldBe 42
        }
    }

    describe("runFlow — sequential steps with output chaining") {
        data class PipelineInput(val value: Int)
        data class PipelineOutput(val result: Int)

        val SequentialFlow = registerFlow<PipelineInput, PipelineOutput>(
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

        it("chains steps — output of one feeds input of the next") {
            // 4 * 3 = 12, 12 + 2 = 14
            engine.runFlow(SequentialFlow, PipelineInput(4)).result shouldBe 14
        }
    }

    describe("runFlow — parallel execution via absent dependsOn") {
        data class ParallelInput(val value: Int)
        data class ParallelOutput(val left: Int, val right: Int)

        val ParallelFlow = registerFlow<ParallelInput, ParallelOutput>(
            name = "parallel-test", description = ""
        ) {
            // Both nodes have no dependsOn → declared as independent (parallel in Temporal)
            val left = addStep(MultiplyStep) {
                input { MultiplyInput(flowInput.value, 2) }
            }
            val right = addStep(MultiplyStep) {
                input { MultiplyInput(flowInput.value, 3) }
            }
            output { ParallelOutput(outputOf(left).result, outputOf(right).result) }
        }

        it("executes both branches and returns both results") {
            val result = engine.runFlow(ParallelFlow, ParallelInput(5))
            result.left  shouldBe 10
            result.right shouldBe 15
        }
    }

    describe("runFlow — subFlow composition") {
        data class InnerInput(val x: Int)
        data class InnerOutput(val doubled: Int)
        data class OuterInput(val x: Int)
        data class OuterOutput(val quadrupled: Int)

        val InnerFlow = registerFlow<InnerInput, InnerOutput>(
            name = "inner-flow-test", description = ""
        ) {
            val multiplied = addStep(MultiplyStep) {
                input { MultiplyInput(flowInput.x, 2) }
            }
            output { InnerOutput(outputOf(multiplied).result) }
        }

        val OuterFlow = registerFlow<OuterInput, OuterOutput>(
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

        it("composes sub-flows and threads data correctly") {
            engine.runFlow(OuterFlow, OuterInput(3)).quadrupled shouldBe 12
        }

        it("repeated sub-flow gets a unique node id to distinguish the two uses") {
            OuterFlow.definition.nodes.filterIsInstance<SubFlowNode<*, *, *>>()
                .map { it.id } shouldBe listOf("inner-flow-test", "inner-flow-test[1]")
        }
    }

    describe("runFlow — flow input accessible throughout all steps") {
        data class ContextInput(val prefix: String, val value: Int)
        data class ContextOutput(val label: String)

        val ContextFlow = registerFlow<ContextInput, ContextOutput>(
            name = "context-test", description = ""
        ) {
            val multiplied = addStep(MultiplyStep) {
                input { MultiplyInput(flowInput.value, 2) }
            }
            output { ContextOutput("${flowInput.prefix}-${outputOf(multiplied).result}") }
        }

        it("flow input remains in scope after steps run") {
            engine.runFlow(ContextFlow, ContextInput("ORDER", 5)).label shouldBe "ORDER-10"
        }
    }

    describe("graph introspection — nodes and edges") {
        data class GI(val x: Int)
        data class GO(val y: Int)

        val GraphFlow = registerFlow<GI, GO>(name = "graph-inspect-test", description = "") {
            val first = addStep(MultiplyStep) {
                input { MultiplyInput(flowInput.x, 2) }
            }
            val second = addStep(AddStep) {
                dependsOn = setOf(first)
                input { AddInput(outputOf(first).result, 1) }
            }
            output { GO(outputOf(second).sum) }
        }

        it("nodes list reflects declaration order") {
            GraphFlow.definition.nodes.map { it.displayName } shouldBe listOf("multiply", "add")
        }

        it("edges reflect dependsOn declarations") {
            GraphFlow.definition.edges shouldBe listOf("multiply" to "add")
        }

        it("steps derived view lists atomic steps") {
            GraphFlow.definition.steps.map { it.name } shouldBe listOf("multiply", "add")
        }
    }

    describe("step with zero values") {
        it("never returns null — zero is a valid output") {
            val result = engine.runStep(MultiplyStep, MultiplyInput(0, 0))
            result shouldNotBe null
            result.result shouldBe 0
        }
    }
})
