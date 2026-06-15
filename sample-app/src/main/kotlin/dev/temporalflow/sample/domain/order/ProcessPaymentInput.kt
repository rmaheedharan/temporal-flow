package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class ProcessPaymentInput(val grandTotal: BigDecimal, val paymentMethod: String)
