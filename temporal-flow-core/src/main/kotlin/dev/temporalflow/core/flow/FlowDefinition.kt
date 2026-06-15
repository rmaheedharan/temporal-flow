package dev.temporalflow.core.flow

import dev.temporalflow.core.registry.DefinitionRegistry
import dev.temporalflow.core.step.RegisteredStep
import dev.temporalflow.core.step.StepDefinition
import kotlin.reflect.KClass

// ─── Execution context ────────────────────────────────────────────────────────

/**
 * Runtime context threaded through graph execution: provides typed access to the
 * flow's input and to the outputs of already-completed nodes.
 */
class FlowNodeContext<FI : Any>(
    val flowInput: FI,
    private val outputs: MutableMap<String, Any> = mutableMapOf()
) {
    @Suppress("UNCHECKED_CAST")
    fun <O : Any> outputOf(node: FlowNode<FI, O>): O =
        outputs[node.id] as? O ?: error("Node '${node.id}' has not been executed yet")

    internal fun record(node: FlowNode<*, *>, value: Any) { outputs[node.id] = value }
}

// ─── Graph nodes ──────────────────────────────────────────────────────────────

/**
 * A declared node in a flow graph — either a [StepNode] or a [SubFlowNode].
 *
 * Nodes are data, not code: the graph is fully inspectable without executing the flow.
 * [dependsOn] declares predecessor nodes; absent edges mean nodes can run in parallel.
 */
sealed class FlowNode<FI : Any, O : Any> {
    abstract val id: String
    abstract val dependsOn: Set<FlowNode<FI, *>>
    abstract val displayName: String
    internal abstract val inputFn: FlowNodeContext<FI>.() -> Any
}

class StepNode<FI : Any, SI : Any, SO : Any>(
    override val id: String,
    val step: StepDefinition<SI, SO>,
    override val dependsOn: Set<FlowNode<FI, *>>,
    override val inputFn: FlowNodeContext<FI>.() -> SI
) : FlowNode<FI, SO>() {
    override val displayName get() = step.name
}

class SubFlowNode<FI : Any, SFI : Any, SFO : Any>(
    override val id: String,
    val subFlow: FlowDefinition<SFI, SFO>,
    override val dependsOn: Set<FlowNode<FI, *>>,
    override val inputFn: FlowNodeContext<FI>.() -> SFI
) : FlowNode<FI, SFO>() {
    override val displayName get() = subFlow.name
}

// ─── Node builders ────────────────────────────────────────────────────────────

class StepBuilder<FI : Any, SI : Any, SO : Any>(val step: StepDefinition<SI, SO>) {
    var dependsOn: Set<FlowNode<FI, *>> = emptySet()
    private var inputFn: (FlowNodeContext<FI>.() -> SI)? = null

    /** Declares the single typed input this step receives. One step, one input type. */
    fun input(fn: FlowNodeContext<FI>.() -> SI) { inputFn = fn }

    internal fun buildInputFn(): FlowNodeContext<FI>.() -> SI =
        inputFn ?: error("addStep(${step.name}) is missing an input { } block")
}

class SubFlowBuilder<FI : Any, SFI : Any, SFO : Any>(val flow: FlowDefinition<SFI, SFO>) {
    var dependsOn: Set<FlowNode<FI, *>> = emptySet()
    private var inputFn: (FlowNodeContext<FI>.() -> SFI)? = null

    fun input(fn: FlowNodeContext<FI>.() -> SFI) { inputFn = fn }

    internal fun buildInputFn(): FlowNodeContext<FI>.() -> SFI =
        inputFn ?: error("addSubFlow(${flow.name}) is missing an input { } block")
}

// ─── Flow graph builder ───────────────────────────────────────────────────────

/**
 * DSL builder for declaring a flow as a node graph.
 *
 * - [addStep] declares an atomic step node with its dependency edges and input construction.
 * - [addSubFlow] declares a sub-flow node (pipeline composition).
 * - [output] declares how completed node outputs are combined into the flow's final result.
 *
 * The same step or sub-flow can appear multiple times — each call creates a distinct node
 * with a unique id (suffixed `[1]`, `[2]`, … on repeat usage).
 */
class FlowGraphBuilder<FI : Any, O : Any> {
    private val _nodes = mutableListOf<FlowNode<FI, *>>()
    private var _outputFn: (FlowNodeContext<FI>.() -> O)? = null
    private val nodeCounter = mutableMapOf<String, Int>()

    fun <SI : Any, SO : Any> addStep(
        type: RegisteredStep<SI, SO>,
        block: StepBuilder<FI, SI, SO>.() -> Unit
    ): StepNode<FI, SI, SO> {
        val b = StepBuilder<FI, SI, SO>(type.definition).also { it.block() }
        return StepNode(nodeId(type.definition.name), type.definition, b.dependsOn, b.buildInputFn())
            .also { _nodes += it }
    }

    fun <SFI : Any, SFO : Any> addSubFlow(
        type: RegisteredFlow<SFI, SFO>,
        block: SubFlowBuilder<FI, SFI, SFO>.() -> Unit
    ): SubFlowNode<FI, SFI, SFO> {
        val b = SubFlowBuilder<FI, SFI, SFO>(type.definition).also { it.block() }
        return SubFlowNode(nodeId(type.definition.name), type.definition, b.dependsOn, b.buildInputFn())
            .also { _nodes += it }
    }

    fun output(fn: FlowNodeContext<FI>.() -> O) { _outputFn = fn }

    private fun nodeId(baseName: String): String {
        val count = nodeCounter.getOrDefault(baseName, 0)
        nodeCounter[baseName] = count + 1
        return if (count == 0) baseName else "$baseName[$count]"
    }

    @PublishedApi internal fun buildNodes(): List<FlowNode<FI, *>> = _nodes.toList()

    @PublishedApi internal fun buildOutputFn(): FlowNodeContext<FI>.() -> O =
        _outputFn ?: error("registerFlow { } block is missing an output { } declaration")
}

// ─── FlowDefinition ───────────────────────────────────────────────────────────

/**
 * Declares a flow as an immutable node graph.
 *
 * [nodes] is the declared graph — inspectable without executing the flow.
 * Derived views [steps], [subFlows], and [edges] are computed from [nodes] on demand.
 */
class FlowDefinition<I : Any, O : Any>(
    val name: String,
    val description: String,
    val inputClass: KClass<I>,
    val outputClass: KClass<O>,
    val nodes: List<FlowNode<I, *>>,
    internal val outputFn: FlowNodeContext<I>.() -> O
) {
    val steps: List<StepDefinition<*, *>>
        get() = nodes.filterIsInstance<StepNode<*, *, *>>().map { it.step }
    val subFlows: List<FlowDefinition<*, *>>
        get() = nodes.filterIsInstance<SubFlowNode<*, *, *>>().map { it.subFlow }
    val edges: List<Pair<String, String>>
        get() = nodes.flatMap { n -> n.dependsOn.map { dep -> dep.displayName to n.displayName } }
}

// ─── Registration wrapper ─────────────────────────────────────────────────────

/**
 * Opaque handle to a registered flow. Only obtainable via [registerFlow].
 * [FlowGraphBuilder.addSubFlow] accepts only [RegisteredFlow] — enforcing at compile time
 * that only registered flows can be composed as sub-flows.
 */
class RegisteredFlow<I : Any, O : Any> @PublishedApi internal constructor(
    internal val definition: FlowDefinition<I, O>
)

// ─── Factory function ─────────────────────────────────────────────────────────

/**
 * Builds the flow graph, registers the resulting [FlowDefinition] in [FlowRegistry],
 * and returns a [RegisteredFlow] handle.
 *
 * ```kotlin
 * val OrderPricingFlow = registerFlow<OrderPricingInput, OrderPricingOutput>(
 *     name = "order-pricing", description = "..."
 * ) {
 *     val validated = addStep(ValidateOrderStep) {
 *         input { ValidateOrderInput(flowInput.orderId) }
 *     }
 *     val discounted = addStep(ApplyDiscountsStep) {
 *         dependsOn = setOf(validated)
 *         input { ApplyDiscountsInput(outputOf(validated).items, flowInput.promoCode) }
 *     }
 *     output { OrderPricingOutput(outputOf(discounted).discountedTotal) }
 * }
 * ```
 */
inline fun <reified I : Any, reified O : Any> registerFlow(
    name: String,
    description: String = "",
    noinline graph: FlowGraphBuilder<I, O>.() -> Unit
): RegisteredFlow<I, O> {
    val b = FlowGraphBuilder<I, O>().also { it.graph() }
    val definition = FlowDefinition(
        name        = name,
        description = description,
        inputClass  = I::class,
        outputClass = O::class,
        nodes       = b.buildNodes(),
        outputFn    = b.buildOutputFn()
    )
    FlowRegistry.register(definition)
    return RegisteredFlow(definition)
}

// ─── Registry ─────────────────────────────────────────────────────────────────

val FlowRegistry = DefinitionRegistry<FlowDefinition<*, *>>()

fun DefinitionRegistry<FlowDefinition<*, *>>.register(definition: FlowDefinition<*, *>) =
    register(definition.name, definition)
