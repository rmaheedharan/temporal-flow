package dev.temporalflow.sample.flows

import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.sample.domain.order.ApplyDiscountsInput
import dev.temporalflow.sample.domain.order.CalculateShippingInput
import dev.temporalflow.sample.domain.order.ShippingAddress
import dev.temporalflow.sample.domain.order.ValidateOrderInput
import dev.temporalflow.sample.steps.ApplyDiscountsStep
import dev.temporalflow.sample.steps.CalculateShippingStep
import dev.temporalflow.sample.steps.ValidateOrderStep
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

val OrderPricingFlow = registerFlow<OrderPricingInput, OrderPricingOutput>(
    name        = "order-pricing",
    description = "Validates order, applies discounts, and calculates shipping cost"
) {
    val validated = addStep(ValidateOrderStep) {
        input { ValidateOrderInput(flowInput.orderId) }
    }

    val discounted = addStep(ApplyDiscountsStep) {
        dependsOn = setOf(validated)
        input {
            ApplyDiscountsInput(
                items     = outputOf(validated).items,
                baseTotal = outputOf(validated).baseTotal,
                promoCode = flowInput.promoCode
            )
        }
    }

    val shipping = addStep(CalculateShippingStep) {
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
