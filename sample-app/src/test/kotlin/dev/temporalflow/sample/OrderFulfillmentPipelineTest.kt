package dev.temporalflow.sample

import dev.temporalflow.core.engine.SynchronousWorkflowEngine
import dev.temporalflow.core.engine.runFlow
import dev.temporalflow.sample.domain.order.ShippingAddress
import dev.temporalflow.sample.flows.FulfillmentFlow
import dev.temporalflow.sample.flows.InventoryManagementFlow
import dev.temporalflow.sample.flows.InventoryManagementInput
import dev.temporalflow.sample.flows.OrderFulfillmentPipeline
import dev.temporalflow.sample.flows.OrderFulfillmentPipelineInput
import dev.temporalflow.sample.flows.OrderPricingFlow
import dev.temporalflow.sample.flows.OrderPricingInput
import dev.temporalflow.sample.flows.FulfillmentInput
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldStartWith
import java.math.BigDecimal

class OrderFulfillmentPipelineTest : DescribeSpec({

    val engine = SynchronousWorkflowEngine()

    val usAddress = ShippingAddress("123 Main St", "Austin", "US")
    val intlAddress = ShippingAddress("10 Rue de Rivoli", "Paris", "FR")

    describe("OrderPricingFlow") {
        it("produces grand total with US shipping") {
            val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-001", usAddress, null))
            result.carrier shouldBe "FedEx"
            result.shippingCost shouldBe BigDecimal("5.99")
            result.grandTotal shouldBe BigDecimal("125.96")   // 119.97 + 5.99
        }

        it("applies promo code SAVE10 discount") {
            val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-002", usAddress, "SAVE10"))
            // 119.97 - 11.997 = 107.973 + 5.99 = 113.963
            result.grandTotal shouldBe BigDecimal("113.963")
        }

        it("uses DHL for international addresses") {
            val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-003", intlAddress, null))
            result.carrier shouldBe "DHL"
            result.shippingCost shouldBe BigDecimal("14.99")
        }
    }

    describe("InventoryManagementFlow") {
        it("returns reservation and warehouse update IDs") {
            val result = engine.runFlow(InventoryManagementFlow, InventoryManagementInput("ORD-001", "US-WEST"))
            result.reservationId shouldStartWith "RES-"
            result.warehouseUpdateId shouldStartWith "WU-"
        }

        it("scopes inventory to the given warehouse region") {
            val result = engine.runFlow(InventoryManagementFlow, InventoryManagementInput("ORD-001", "EU-CENTRAL"))
            result.reservationId shouldNotBe null
        }
    }

    describe("FulfillmentFlow") {
        it("approves payment and sends confirmation") {
            val result = engine.runFlow(FulfillmentFlow, FulfillmentInput(BigDecimal("99.99"), "CREDIT_CARD"))
            result.confirmationId shouldStartWith "CONF-"
            result.transactionId shouldStartWith "TXN-"
        }
    }

    describe("OrderFulfillmentPipeline — end to end") {
        it("completes full pipeline and returns all IDs") {
            val input = OrderFulfillmentPipelineInput(
                orderId = "ORD-E2E-001",
                shippingAddress = usAddress,
                promoCode = null,
                warehouseRegion = "US-EAST",
                paymentMethod = "CREDIT_CARD"
            )
            val result = engine.runFlow(OrderFulfillmentPipeline, input)

            result.confirmationId shouldStartWith "CONF-"
            result.transactionId shouldStartWith "TXN-"
            result.reservationId shouldStartWith "RES-"
            result.grandTotal shouldBe BigDecimal("125.96")
        }

        it("passes grandTotal from pricing to fulfillment (not from pipeline input)") {
            val withPromo = OrderFulfillmentPipelineInput(
                orderId = "ORD-E2E-002",
                shippingAddress = usAddress,
                promoCode = "SAVE10",
                warehouseRegion = "US-WEST",
                paymentMethod = "PAYPAL"
            )
            val withoutPromo = OrderFulfillmentPipelineInput(
                orderId = "ORD-E2E-003",
                shippingAddress = usAddress,
                promoCode = null,
                warehouseRegion = "US-WEST",
                paymentMethod = "PAYPAL"
            )

            val promoResult = engine.runFlow(OrderFulfillmentPipeline, withPromo)
            val regularResult = engine.runFlow(OrderFulfillmentPipeline, withoutPromo)

            // Promo grand total must be lower than regular
            promoResult.grandTotal shouldBe BigDecimal("113.963")
            regularResult.grandTotal shouldBe BigDecimal("125.96")
        }
    }
})
