package dev.temporalflow.sample.domain.order

import java.math.BigDecimal

data class OrderItem(val sku: String, val quantity: Int, val unitPrice: BigDecimal)
