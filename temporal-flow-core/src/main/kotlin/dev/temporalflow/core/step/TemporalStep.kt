package dev.temporalflow.core.step

import kotlin.reflect.KClass

/**
 * Base class for DI-managed step beans.
 *
 * Extend this class and annotate with your DI framework's singleton annotation.
 * Registration into [StepRegistry] happens automatically in the [init] block,
 * so no separate registrar is needed.
 *
 * ```kotlin
 * @Singleton
 * class ValidateOrderStep(
 *     private val orderService: OrderService
 * ) : TemporalStep<ValidateOrderInput, ValidateOrderOutput>(
 *     name        = "validate-order",
 *     description = "Validates the order and returns items and base total",
 *     inputClass  = ValidateOrderInput::class,
 *     outputClass = ValidateOrderOutput::class
 * ) {
 *     override fun handle(input: ValidateOrderInput): ValidateOrderOutput =
 *         orderService.validate(input.orderId)
 * }
 * ```
 *
 * Constructor parameters (injected services) are assigned before [init] runs,
 * so [handle] can safely reference them when it is eventually called.
 */
abstract class TemporalStep<I : Any, O : Any>(
    val name: String,
    val description: String,
    val inputClass: KClass<I>,
    val outputClass: KClass<O>
) {
    abstract fun handle(input: I): O

    val registered: RegisteredStep<I, O>

    init {
        val definition = StepDefinition(name, description, inputClass, outputClass, ::handle)
        StepRegistry.register(definition)
        registered = RegisteredStep(definition)
    }
}
