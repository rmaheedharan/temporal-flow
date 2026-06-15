package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class CalculateShippingInput(val discountedTotal: BigDecimal, val shippingAddress: ShippingAddress)
