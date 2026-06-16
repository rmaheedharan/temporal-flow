package dev.temporalflow.sample.flows

import dev.temporalflow.core.flow.RegisteredFlow
import dev.temporalflow.core.flow.registerFlow
import dev.temporalflow.sample.domain.inventory.CheckInventoryInput
import dev.temporalflow.sample.domain.inventory.ReserveStockInput
import dev.temporalflow.sample.domain.inventory.UpdateWarehouseInput
import dev.temporalflow.sample.steps.CheckInventoryStep
import dev.temporalflow.sample.steps.ReserveStockStep
import dev.temporalflow.sample.steps.UpdateWarehouseStep
import jakarta.inject.Singleton

data class InventoryManagementInput(val orderId: String, val warehouseRegion: String)

data class InventoryManagementOutput(val reservationId: String, val warehouseUpdateId: String)

@Singleton
class InventoryManagementFlow(
    private val checkStep: CheckInventoryStep,
    private val reserveStep: ReserveStockStep,
    private val updateStep: UpdateWarehouseStep
) {
    val registered: RegisteredFlow<InventoryManagementInput, InventoryManagementOutput> = registerFlow(
        name        = "inventory-management",
        description = "Checks inventory availability, reserves stock, and updates warehouse records"
    ) {
        val checked = addStep(checkStep) {
            input { CheckInventoryInput(flowInput.orderId, flowInput.warehouseRegion) }
        }

        val reserved = addStep(reserveStep) {
            dependsOn = setOf(checked)
            input { ReserveStockInput(flowInput.orderId, flowInput.warehouseRegion) }
        }

        val updated = addStep(updateStep) {
            dependsOn = setOf(checked, reserved)
            input {
                UpdateWarehouseInput(
                    availableItems = outputOf(checked).availableItems,
                    inventoryId    = outputOf(checked).inventoryId
                )
            }
        }

        output {
            InventoryManagementOutput(
                reservationId     = outputOf(reserved).reservationId,
                warehouseUpdateId = outputOf(updated).warehouseUpdateId
            )
        }
    }
}
