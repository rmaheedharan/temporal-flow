package dev.temporalflow.core.temporal

import dev.temporalflow.core.step.StepRegistry
import io.temporal.activity.Activity
import io.temporal.activity.DynamicActivity
import io.temporal.common.converter.EncodedValues

/**
 * Dynamic Temporal activity dispatcher — the activity type name IS the step name.
 *
 * Using [DynamicActivity] means each step (e.g. "validate-order", "apply-discounts")
 * appears as its own named activity type in the Temporal UI, rather than all steps
 * showing up as a generic "StepExecutorActivity".
 */
internal class DynamicStepExecutorActivity : DynamicActivity {
    override fun execute(args: EncodedValues): Any {
        val stepName = Activity.getExecutionContext().info.activityType
        val inputJson = args.get(0, String::class.java)
        val definition = StepRegistry.require(stepName)
        @Suppress("UNCHECKED_CAST")
        val typed = definition as dev.temporalflow.core.step.StepDefinition<Any, Any>
        val input = FlowSerialization.decode(inputJson, typed.inputClass)
        val output = typed.handler(input)
        return FlowSerialization.encode(output)
    }
}
