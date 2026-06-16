package dev.temporalflow.sample

import dev.temporalflow.core.engine.SynchronousWorkflowEngine
import dev.temporalflow.core.engine.runFlow
import dev.temporalflow.sample.domain.order.ShippingAddress
import dev.temporalflow.sample.flows.FulfillmentFlow
import dev.temporalflow.sample.flows.FulfillmentInput
import dev.temporalflow.sample.flows.InventoryManagementFlow
import dev.temporalflow.sample.flows.InventoryManagementInput
import dev.temporalflow.sample.flows.OrderFulfillmentPipeline
import dev.temporalflow.sample.flows.OrderFulfillmentPipelineInput
import dev.temporalflow.sample.flows.OrderPricingFlow
import dev.temporalflow.sample.flows.OrderPricingInput
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import java.math.BigDecimal

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OrderFulfillmentPipelineTest {

    private val engine = SynchronousWorkflowEngine()

    private val usAddress = ShippingAddress("123 Main St", "Austin", "US")
    private val intlAddress = ShippingAddress("10 Rue de Rivoli", "Paris", "FR")

    @Nested
    @DisplayName("OrderPricingFlow")
    inner class OrderPricingFlowTests {
        @Test
        fun `produces grand total with US shipping`() {
            val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-001", usAddress, null))
            assertEquals("FedEx", result.carrier)
            assertEquals(BigDecimal("5.99"), result.shippingCost)
            assertEquals(BigDecimal("125.96"), result.grandTotal)
        }

        @Test
        fun `applies promo code SAVE10 discount`() {
            val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-002", usAddress, "SAVE10"))
            // 119.97 - 11.997 = 107.973 + 5.99 = 113.963
            assertEquals(BigDecimal("113.963"), result.grandTotal)
        }

        @Test
        fun `uses DHL for international addresses`() {
            val result = engine.runFlow(OrderPricingFlow, OrderPricingInput("ORD-003", intlAddress, null))
            assertEquals("DHL", result.carrier)
            assertEquals(BigDecimal("14.99"), result.shippingCost)
        }
    }

    @Nested
    @DisplayName("InventoryManagementFlow")
    inner class InventoryManagementFlowTests {
        @Test
        fun `returns reservation and warehouse update IDs`() {
            val result = engine.runFlow(InventoryManagementFlow, InventoryManagementInput("ORD-001", "US-WEST"))
            assertTrue(result.reservationId.startsWith("RES-"))
            assertTrue(result.warehouseUpdateId.startsWith("WU-"))
        }

        @Test
        fun `scopes inventory to the given warehouse region`() {
            val result = engine.runFlow(InventoryManagementFlow, InventoryManagementInput("ORD-001", "EU-CENTRAL"))
            assertNotNull(result.reservationId)
        }
    }

    @Nested
    @DisplayName("FulfillmentFlow")
    inner class FulfillmentFlowTests {
        @Test
        fun `approves payment and sends confirmation`() {
            val result = engine.runFlow(FulfillmentFlow, FulfillmentInput(BigDecimal("99.99"), "CREDIT_CARD"))
            assertTrue(result.confirmationId.startsWith("CONF-"))
            assertTrue(result.transactionId.startsWith("TXN-"))
        }
    }

    @Nested
    @DisplayName("OrderFulfillmentPipeline — end to end")
    inner class OrderFulfillmentPipelineTests {
        @Test
        fun `completes full pipeline and returns all IDs`() {
            val input = OrderFulfillmentPipelineInput(
                orderId = "ORD-E2E-001",
                shippingAddress = usAddress,
                promoCode = null,
                warehouseRegion = "US-EAST",
                paymentMethod = "CREDIT_CARD"
            )
            val result = engine.runFlow(OrderFulfillmentPipeline, input)
            assertTrue(result.confirmationId.startsWith("CONF-"))
            assertTrue(result.transactionId.startsWith("TXN-"))
            assertTrue(result.reservationId.startsWith("RES-"))
            assertEquals(BigDecimal("125.96"), result.grandTotal)
        }

        @Test
        fun `passes grandTotal from pricing to fulfillment (not from pipeline input)`() {
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

            assertEquals(BigDecimal("113.963"), promoResult.grandTotal)
            assertEquals(BigDecimal("125.96"), regularResult.grandTotal)
        }
    }
}
