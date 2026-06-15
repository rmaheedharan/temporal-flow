package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class CalculateShippingOutput(
    val shippingCost: BigDecimal,
    val carrier: String,
    val grandTotal: BigDecimal
)
