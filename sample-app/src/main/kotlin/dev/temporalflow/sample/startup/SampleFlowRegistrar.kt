package dev.temporalflow.sample.startup

import dev.temporalflow.sample.flows.FulfillmentFlow
import dev.temporalflow.sample.flows.InventoryManagementFlow
import dev.temporalflow.sample.flows.OrderFulfillmentPipeline
import dev.temporalflow.sample.flows.OrderPricingFlow
import dev.temporalflow.sample.steps.ApplyDiscountsStep
import dev.temporalflow.sample.steps.CalculateShippingStep
import dev.temporalflow.sample.steps.CheckInventoryStep
import dev.temporalflow.sample.steps.ProcessPaymentStep
import dev.temporalflow.sample.steps.ReserveStockStep
import dev.temporalflow.sample.steps.SendConfirmationStep
import dev.temporalflow.sample.steps.UpdateWarehouseStep
import dev.temporalflow.sample.steps.ValidateOrderStep
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton

/**
 * Forces initialization of all top-level step and flow declarations at startup.
 *
 * Kotlin top-level `val` objects are lazy — they initialize on first access. Touching each
 * declaration here ensures [registerStep] / [registerFlow] run (which auto-register into the
 * global registries) before any HTTP or MCP request arrives.
 *
 * Steps must be initialized before the flows that reference them to avoid ordering issues,
 * though Kotlin's class-loading order typically guarantees this via import resolution.
 */
@Singleton
class SampleFlowRegistrar : ApplicationEventListener<StartupEvent> {

    override fun onApplicationEvent(event: StartupEvent) {
        // Steps first — flows reference them; touching flows forces step init too,
        // but explicit ordering here makes the dependency chain obvious.
        ValidateOrderStep; ApplyDiscountsStep; CalculateShippingStep
        ProcessPaymentStep; SendConfirmationStep
        CheckInventoryStep; ReserveStockStep; UpdateWarehouseStep

        // Flows — registerFlow also adds to FlowRegistry
        OrderPricingFlow; InventoryManagementFlow; FulfillmentFlow; OrderFulfillmentPipeline
    }
}
