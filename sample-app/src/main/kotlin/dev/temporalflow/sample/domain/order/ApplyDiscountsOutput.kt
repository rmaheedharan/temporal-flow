package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class ApplyDiscountsOutput(val discountedTotal: BigDecimal, val discountAmount: BigDecimal)
