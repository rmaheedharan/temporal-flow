package dev.temporalflow.core.step

import dev.temporalflow.core.registry.DefinitionRegistry
import kotlin.reflect.KClass

/**
 * Source-only annotation marking a top-level property as a registered flow step.
 * Not required for registration (that happens inside [registerStep]) — serves as a
 * tooling hint for KSP processors and diagram generators.
 */
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.LOCAL_VARIABLE)
@Retention(AnnotationRetention.SOURCE)
annotation class FlowStep

/**
 * Describes a single unit of work — its identity, contract, and execution logic.
 */
class StepDefinition<I : Any, O : Any>(
    val name: String,
    val description: String,
    val inputClass: KClass<I>,
    val outputClass: KClass<O>,
    val handler: (I) -> O
)

/**
 * Opaque handle to a registered step. The only way to obtain one is through [registerStep],
 * which simultaneously creates the step and records it in [StepRegistry].
 *
 * [dev.temporalflow.core.flow.FlowGraphBuilder.addStep] accepts only [RegisteredStep], not
 * a raw [StepDefinition] — this enforces at compile time that only registered steps can be
 * composed into flows.
 */
class RegisteredStep<SI : Any, SO : Any> @PublishedApi internal constructor(
    internal val definition: StepDefinition<SI, SO>
)

/**
 * Creates a [StepDefinition], registers it in [StepRegistry], and returns a [RegisteredStep] handle.
 *
 * ```kotlin
 * @FlowStep
 * val ValidateOrderStep = registerStep<ValidateOrderInput, ValidateOrderOutput>(
 *     name        = "validate-order",
 *     description = "Validates the order and resolves item pricing"
 * ) { input -> ValidateOrderOutput(...) }
 * ```
 */
inline fun <reified I : Any, reified O : Any> registerStep(
    name: String,
    description: String = "",
    noinline handler: (I) -> O
): RegisteredStep<I, O> {
    val definition = StepDefinition(name, description, I::class, O::class, handler)
    StepRegistry.register(definition)
    return RegisteredStep(definition)
}

val StepRegistry = DefinitionRegistry<StepDefinition<*, *>>()

fun DefinitionRegistry<StepDefinition<*, *>>.register(definition: StepDefinition<*, *>) =
    register(definition.name, definition)
