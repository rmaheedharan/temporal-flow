package dev.temporalflow.sample.domain.order

data class ProcessPaymentOutput(val transactionId: String, val paymentStatus: String)
