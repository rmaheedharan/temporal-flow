package dev.temporalflow.sample.steps

import dev.temporalflow.core.step.TemporalStep
import dev.temporalflow.sample.domain.order.ApplyDiscountsInput
import dev.temporalflow.sample.domain.order.ApplyDiscountsOutput
import dev.temporalflow.sample.domain.order.CalculateShippingInput
import dev.temporalflow.sample.domain.order.CalculateShippingOutput
import dev.temporalflow.sample.domain.order.OrderItem
import dev.temporalflow.sample.domain.order.ValidateOrderInput
import dev.temporalflow.sample.domain.order.ValidateOrderOutput
import jakarta.inject.Singleton
import java.math.BigDecimal

@Singleton
class ValidateOrderStep : TemporalStep<ValidateOrderInput, ValidateOrderOutput>(
    name        = "validate-order",
    description = "Validates the order exists and returns its items, customer, and base total",
    inputClass  = ValidateOrderInput::class,
    outputClass = ValidateOrderOutput::class
) {
    override fun handle(input: ValidateOrderInput): ValidateOrderOutput =
        ValidateOrderOutput(
            items = listOf(
                OrderItem("SKU-001", 2, BigDecimal("49.99")),
                OrderItem("SKU-002", 1, BigDecimal("19.99"))
            ),
            customerId = "CUST-${input.orderId}",
            baseTotal  = BigDecimal("119.97")
        )
}

@Singleton
class ApplyDiscountsStep : TemporalStep<ApplyDiscountsInput, ApplyDiscountsOutput>(
    name        = "apply-discounts",
    description = "Applies promo codes and item-level discounts to produce the discounted total",
    inputClass  = ApplyDiscountsInput::class,
    outputClass = ApplyDiscountsOutput::class
) {
    override fun handle(input: ApplyDiscountsInput): ApplyDiscountsOutput {
        val discount = if (input.promoCode == "SAVE10") BigDecimal("11.997") else BigDecimal.ZERO
        return ApplyDiscountsOutput(
            discountedTotal = input.baseTotal.subtract(discount),
            discountAmount  = discount
        )
    }
}

@Singleton
class CalculateShippingStep : TemporalStep<CalculateShippingInput, CalculateShippingOutput>(
    name        = "calculate-shipping",
    description = "Calculates shipping cost and carrier based on destination and order total",
    inputClass  = CalculateShippingInput::class,
    outputClass = CalculateShippingOutput::class
) {
    override fun handle(input: CalculateShippingInput): CalculateShippingOutput {
        val shippingCost = if (input.shippingAddress.country == "US") BigDecimal("5.99") else BigDecimal("14.99")
        val carrier      = if (input.shippingAddress.country == "US") "FedEx" else "DHL"
        return CalculateShippingOutput(
            shippingCost = shippingCost,
            carrier      = carrier,
            grandTotal   = input.discountedTotal.add(shippingCost)
        )
    }
}
