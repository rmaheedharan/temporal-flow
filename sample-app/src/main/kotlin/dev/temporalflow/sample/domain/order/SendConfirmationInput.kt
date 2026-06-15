package dev.temporalflow.sample.domain.order

data class SendConfirmationInput(val transactionId: String, val paymentStatus: String)
