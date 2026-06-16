package dev.temporalflow.sample.startup

import dev.temporalflow.sample.flows.FulfillmentFlow
import dev.temporalflow.sample.flows.InventoryManagementFlow
import dev.temporalflow.sample.flows.OrderFulfillmentPipeline
import dev.temporalflow.sample.flows.OrderPricingFlow
import io.micronaut.context.event.ApplicationEventListener
import io.micronaut.context.event.StartupEvent
import jakarta.inject.Singleton

/**
 * Forces eager initialization of all flow beans at startup.
 *
 * Micronaut @Singleton beans are lazy. Injecting the top-level flow beans here
 * causes Micronaut to create them (and their step dependencies) before any
 * HTTP or MCP request arrives. Steps register themselves via TemporalStep.init;
 * flows register via registerFlow() in their property initializers.
 */
@Singleton
class SampleFlowRegistrar(
    @Suppress("UNUSED_PARAMETER") orderPricingFlow: OrderPricingFlow,
    @Suppress("UNUSED_PARAMETER") inventoryManagementFlow: InventoryManagementFlow,
    @Suppress("UNUSED_PARAMETER") fulfillmentFlow: FulfillmentFlow,
    @Suppress("UNUSED_PARAMETER") orderFulfillmentPipeline: OrderFulfillmentPipeline
) : ApplicationEventListener<StartupEvent> {
    override fun onApplicationEvent(event: StartupEvent) { }
}
