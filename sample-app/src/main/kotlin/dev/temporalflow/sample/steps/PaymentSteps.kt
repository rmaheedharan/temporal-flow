package dev.temporalflow.sample.steps

import dev.temporalflow.core.step.TemporalStep
import dev.temporalflow.sample.domain.order.ProcessPaymentInput
import dev.temporalflow.sample.domain.order.ProcessPaymentOutput
import dev.temporalflow.sample.domain.order.SendConfirmationInput
import dev.temporalflow.sample.domain.order.SendConfirmationOutput
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class ProcessPaymentStep : TemporalStep<ProcessPaymentInput, ProcessPaymentOutput>(
    name        = "process-payment",
    description = "Charges the customer via the given payment method and returns a transaction ID",
    inputClass  = ProcessPaymentInput::class,
    outputClass = ProcessPaymentOutput::class
) {
    override fun handle(input: ProcessPaymentInput): ProcessPaymentOutput =
        ProcessPaymentOutput(
            transactionId = "TXN-${UUID.randomUUID()}",
            paymentStatus = if (input.grandTotal > java.math.BigDecimal.ZERO) "APPROVED" else "REJECTED"
        )
}

@Singleton
class SendConfirmationStep : TemporalStep<SendConfirmationInput, SendConfirmationOutput>(
    name        = "send-confirmation",
    description = "Sends an order confirmation email and returns the confirmation ID",
    inputClass  = SendConfirmationInput::class,
    outputClass = SendConfirmationOutput::class
) {
    override fun handle(input: SendConfirmationInput): SendConfirmationOutput =
        SendConfirmationOutput(
            confirmationId = "CONF-${UUID.randomUUID()}",
            sentAt         = Instant.now().toString()
        )
}
