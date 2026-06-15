package dev.temporalflow.sample.steps

import dev.temporalflow.core.step.FlowStep
import dev.temporalflow.core.step.registerStep
import dev.temporalflow.sample.domain.inventory.CheckInventoryInput
import dev.temporalflow.sample.domain.inventory.CheckInventoryOutput
import dev.temporalflow.sample.domain.inventory.InventoryItem
import dev.temporalflow.sample.domain.inventory.ReserveStockInput
import dev.temporalflow.sample.domain.inventory.ReserveStockOutput
import dev.temporalflow.sample.domain.inventory.UpdateWarehouseInput
import dev.temporalflow.sample.domain.inventory.UpdateWarehouseOutput
import java.time.Instant
import java.util.UUID

@FlowStep
val CheckInventoryStep = registerStep<CheckInventoryInput, CheckInventoryOutput>(
    name        = "check-inventory",
    description = "Checks available stock in the given warehouse region for the order items"
) { input ->
    CheckInventoryOutput(
        availableItems = listOf(
            InventoryItem("SKU-001", 10, "${input.warehouseRegion}-WH-01"),
            InventoryItem("SKU-002", 5, "${input.warehouseRegion}-WH-01")
        ),
        inventoryId = "INV-${input.orderId}-${input.warehouseRegion}"
    )
}

@FlowStep
val ReserveStockStep = registerStep<ReserveStockInput, ReserveStockOutput>(
    name        = "reserve-stock",
    description = "Reserves inventory in the warehouse to prevent overselling"
) { input ->
    ReserveStockOutput(
        reservationId = "RES-${UUID.randomUUID()}",
        reservedItems = listOf(
            InventoryItem("SKU-001", 2, "${input.warehouseRegion}-WH-01"),
            InventoryItem("SKU-002", 1, "${input.warehouseRegion}-WH-01")
        )
    )
}

@FlowStep
val UpdateWarehouseStep = registerStep<UpdateWarehouseInput, UpdateWarehouseOutput>(
    name        = "update-warehouse",
    description = "Deducts reserved quantities from warehouse inventory records"
) { _ ->
    UpdateWarehouseOutput(
        warehouseUpdateId = "WU-${UUID.randomUUID()}",
        updatedAt         = Instant.now().toString()
    )
}
