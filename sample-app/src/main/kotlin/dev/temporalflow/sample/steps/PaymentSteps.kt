package dev.temporalflow.sample.steps

import dev.temporalflow.core.step.FlowStep
import dev.temporalflow.core.step.registerStep
import dev.temporalflow.sample.domain.order.ProcessPaymentInput
import dev.temporalflow.sample.domain.order.ProcessPaymentOutput
import dev.temporalflow.sample.domain.order.SendConfirmationInput
import dev.temporalflow.sample.domain.order.SendConfirmationOutput
import java.time.Instant
import java.util.UUID

@FlowStep
val ProcessPaymentStep = registerStep<ProcessPaymentInput, ProcessPaymentOutput>(
    name        = "process-payment",
    description = "Charges the customer via the given payment method and returns a transaction ID"
) { input ->
    ProcessPaymentOutput(
        transactionId = "TXN-${UUID.randomUUID()}",
        paymentStatus = if (input.grandTotal > java.math.BigDecimal.ZERO) "APPROVED" else "REJECTED"
    )
}

@FlowStep
val SendConfirmationStep = registerStep<SendConfirmationInput, SendConfirmationOutput>(
    name        = "send-confirmation",
    description = "Sends an order confirmation email and returns the confirmation ID"
) { _ ->
    SendConfirmationOutput(
        confirmationId = "CONF-${UUID.randomUUID()}",
        sentAt         = Instant.now().toString()
    )
}
