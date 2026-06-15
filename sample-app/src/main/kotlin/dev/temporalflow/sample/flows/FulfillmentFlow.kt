package dev.temporalflow.sample.flows

import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.sample.domain.order.ProcessPaymentInput
import dev.temporalflow.sample.domain.order.SendConfirmationInput
import dev.temporalflow.sample.steps.ProcessPaymentStep
import dev.temporalflow.sample.steps.SendConfirmationStep
import java.math.BigDecimal

data class FulfillmentInput(val grandTotal: BigDecimal, val paymentMethod: String)

data class FulfillmentOutput(val confirmationId: String, val transactionId: String)

val FulfillmentFlow = registerFlow<FulfillmentInput, FulfillmentOutput>(
    name        = "fulfillment",
    description = "Processes payment and sends the order confirmation to the customer"
) {
    val payment = addStep(ProcessPaymentStep) {
        input { ProcessPaymentInput(flowInput.grandTotal, flowInput.paymentMethod) }
    }

    val confirmation = addStep(SendConfirmationStep) {
        dependsOn = setOf(payment)
        input {
            SendConfirmationInput(
                transactionId = outputOf(payment).transactionId,
                paymentStatus = outputOf(payment).paymentStatus
            )
        }
    }

    output {
        FulfillmentOutput(
            confirmationId = outputOf(confirmation).confirmationId,
            transactionId  = outputOf(payment).transactionId
        )
    }
}
