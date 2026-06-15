package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class ApplyDiscountsInput(
    val items: List<OrderItem>,
    val baseTotal: BigDecimal,
    val promoCode: String?
)
