package dev.temporalflow.sample.flows

import dev.temporalflow.core.flow.RegisteredFlow
import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.sample.domain.order.ApplyDiscountsInput
import dev.temporalflow.sample.domain.order.CalculateShippingInput
import dev.temporalflow.sample.domain.order.ShippingAddress
import dev.temporalflow.sample.domain.order.ValidateOrderInput
import dev.temporalflow.sample.steps.ApplyDiscountsStep
import dev.temporalflow.sample.steps.CalculateShippingStep
import dev.temporalflow.sample.steps.ValidateOrderStep
import jakarta.inject.Singleton
import java.math.BigDecimal

data class OrderPricingInput(
    val orderId: String,
    val shippingAddress: ShippingAddress,
    val promoCode: String?
)

data class OrderPricingOutput(
    val grandTotal: BigDecimal,
    val carrier: String,
    val shippingCost: BigDecimal
)

@Singleton
class OrderPricingFlow(
    private val validateStep: ValidateOrderStep,
    private val discountStep: ApplyDiscountsStep,
    private val shippingStep: CalculateShippingStep
) {
    val registered: RegisteredFlow<OrderPricingInput, OrderPricingOutput> = registerFlow(
        name        = "order-pricing",
        description = "Validates order, applies discounts, and calculates shipping cost"
    ) {
        val validated = addStep(validateStep) {
            input { ValidateOrderInput(flowInput.orderId) }
        }

        val discounted = addStep(discountStep) {
            dependsOn = setOf(validated)
            input {
                ApplyDiscountsInput(
                    items     = outputOf(validated).items,
                    baseTotal = outputOf(validated).baseTotal,
                    promoCode = flowInput.promoCode
                )
            }
        }

        val shipping = addStep(shippingStep) {
            dependsOn = setOf(discounted)
            input {
                CalculateShippingInput(
                    discountedTotal = outputOf(discounted).discountedTotal,
                    shippingAddress = flowInput.shippingAddress
                )
            }
        }

        output {
            OrderPricingOutput(
                grandTotal   = outputOf(shipping).grandTotal,
                carrier      = outputOf(shipping).carrier,
                shippingCost = outputOf(shipping).shippingCost
            )
        }
    }
}
