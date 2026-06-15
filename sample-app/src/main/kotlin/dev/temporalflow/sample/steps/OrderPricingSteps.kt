package dev.temporalflow.sample.steps

import dev.temporalflow.core.step.FlowStep
import dev.temporalflow.core.step.registerStep
import dev.temporalflow.sample.domain.order.ApplyDiscountsInput
import dev.temporalflow.sample.domain.order.ApplyDiscountsOutput
import dev.temporalflow.sample.domain.order.CalculateShippingInput
import dev.temporalflow.sample.domain.order.CalculateShippingOutput
import dev.temporalflow.sample.domain.order.OrderItem
import dev.temporalflow.sample.domain.order.ValidateOrderInput
import dev.temporalflow.sample.domain.order.ValidateOrderOutput
import java.math.BigDecimal

@FlowStep
val ValidateOrderStep = registerStep<ValidateOrderInput, ValidateOrderOutput>(
    name        = "validate-order",
    description = "Validates the order exists and returns its items, customer, and base total"
) { input ->
    ValidateOrderOutput(
        items = listOf(
            OrderItem("SKU-001", 2, BigDecimal("49.99")),
            OrderItem("SKU-002", 1, BigDecimal("19.99"))
        ),
        customerId = "CUST-${input.orderId}",
        baseTotal  = BigDecimal("119.97")
    )
}

@FlowStep
val ApplyDiscountsStep = registerStep<ApplyDiscountsInput, ApplyDiscountsOutput>(
    name        = "apply-discounts",
    description = "Applies promo codes and item-level discounts to produce the discounted total"
) { input ->
    val discount = if (input.promoCode == "SAVE10") BigDecimal("11.997") else BigDecimal.ZERO
    ApplyDiscountsOutput(
        discountedTotal = input.baseTotal.subtract(discount),
        discountAmount  = discount
    )
}

@FlowStep
val CalculateShippingStep = registerStep<CalculateShippingInput, CalculateShippingOutput>(
    name        = "calculate-shipping",
    description = "Calculates shipping cost and carrier based on destination and order total"
) { input ->
    val shippingCost = if (input.shippingAddress.country == "US") BigDecimal("5.99") else BigDecimal("14.99")
    val carrier      = if (input.shippingAddress.country == "US") "FedEx" else "DHL"
    CalculateShippingOutput(
        shippingCost = shippingCost,
        carrier      = carrier,
        grandTotal   = input.discountedTotal.add(shippingCost)
    )
}
