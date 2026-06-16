package dev.temporalflow.sample.flows

import dev.temporalflow.core.flow.RegisteredFlow
import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.sample.domain.order.ShippingAddress
import jakarta.inject.Singleton
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

@Singleton
class OrderFulfillmentPipeline(
    private val pricingFlow: OrderPricingFlow,
    private val inventoryFlow: InventoryManagementFlow,
    private val fulfillmentFlow: FulfillmentFlow
) {
    val registered: RegisteredFlow<OrderFulfillmentPipelineInput, OrderFulfillmentPipelineOutput> = registerFlow(
        name        = "order-fulfillment-pipeline",
        description = "End-to-end order fulfillment: pricing and inventory run in parallel, then payment and confirmation"
    ) {
        // No dependsOn on pricing or inventory → they run in parallel
        val pricing = addSubFlow(pricingFlow.registered) {
            input {
                OrderPricingInput(
                    orderId         = flowInput.orderId,
                    shippingAddress = flowInput.shippingAddress,
                    promoCode       = flowInput.promoCode
                )
            }
        }

        val inventory = addSubFlow(inventoryFlow.registered) {
            input {
                InventoryManagementInput(
                    orderId         = flowInput.orderId,
                    warehouseRegion = flowInput.warehouseRegion
                )
            }
        }

        val fulfillment = addSubFlow(fulfillmentFlow.registered) {
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
}
