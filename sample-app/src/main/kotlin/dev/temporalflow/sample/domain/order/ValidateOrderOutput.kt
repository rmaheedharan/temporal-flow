package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class ValidateOrderOutput(
    val items: List<OrderItem>,
    val customerId: String,
    val baseTotal: BigDecimal
)
