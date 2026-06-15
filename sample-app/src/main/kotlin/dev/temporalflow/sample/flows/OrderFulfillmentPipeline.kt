package dev.temporalflow.sample.flows

import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.sample.domain.order.ShippingAddress
import java.math.BigDecimal

data class OrderFulfillmentPipelineInput(
    val orderId: String,
    val shippingAddress: ShippingAddress,
    val promoCode: String?,
    val warehouseRegion: String,
    val paymentMethod: String
)

data class OrderFulfillmentPipelineOutput(
    val confirmationId: String,
    val transactionId: String,
    val reservationId: String,
    val grandTotal: BigDecimal
)

val OrderFulfillmentPipeline = registerFlow<OrderFulfillmentPipelineInput, OrderFulfillmentPipelineOutput>(
    name        = "order-fulfillment-pipeline",
    description = "End-to-end order fulfillment: pricing and inventory run in parallel, then payment and confirmation"
) {
    // No dependsOn on pricing or inventory → they run in parallel
    val pricing = addSubFlow(OrderPricingFlow) {
        input {
            OrderPricingInput(
                orderId         = flowInput.orderId,
                shippingAddress = flowInput.shippingAddress,
                promoCode       = flowInput.promoCode
            )
        }
    }

    val inventory = addSubFlow(InventoryManagementFlow) {
        input {
            InventoryManagementInput(
                orderId         = flowInput.orderId,
                warehouseRegion = flowInput.warehouseRegion
            )
        }
    }

    val fulfillment = addSubFlow(FulfillmentFlow) {
        dependsOn = setOf(pricing)
        input {
            FulfillmentInput(
                grandTotal    = outputOf(pricing).grandTotal,
                paymentMethod = flowInput.paymentMethod
            )
        }
    }

    output {
        OrderFulfillmentPipelineOutput(
            confirmationId = outputOf(fulfillment).confirmationId,
            transactionId  = outputOf(fulfillment).transactionId,
            reservationId  = outputOf(inventory).reservationId,
            grandTotal     = outputOf(pricing).grandTotal
        )
    }
}
