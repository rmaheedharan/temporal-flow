package dev.temporalflow.sample.steps

import dev.temporalflow.core.step.TemporalStep
import dev.temporalflow.sample.domain.inventory.CheckInventoryInput
import dev.temporalflow.sample.domain.inventory.CheckInventoryOutput
import dev.temporalflow.sample.domain.inventory.InventoryItem
import dev.temporalflow.sample.domain.inventory.ReserveStockInput
import dev.temporalflow.sample.domain.inventory.ReserveStockOutput
import dev.temporalflow.sample.domain.inventory.UpdateWarehouseInput
import dev.temporalflow.sample.domain.inventory.UpdateWarehouseOutput
import jakarta.inject.Singleton
import java.time.Instant
import java.util.UUID

@Singleton
class CheckInventoryStep : TemporalStep<CheckInventoryInput, CheckInventoryOutput>(
    name        = "check-inventory",
    description = "Checks available stock in the given warehouse region for the order items",
    inputClass  = CheckInventoryInput::class,
    outputClass = CheckInventoryOutput::class
) {
    override fun handle(input: CheckInventoryInput): CheckInventoryOutput =
        CheckInventoryOutput(
            availableItems = listOf(
                InventoryItem("SKU-001", 10, "${input.warehouseRegion}-WH-01"),
                InventoryItem("SKU-002", 5,  "${input.warehouseRegion}-WH-01")
            ),
            inventoryId = "INV-${input.orderId}-${input.warehouseRegion}"
        )
}

@Singleton
class ReserveStockStep : TemporalStep<ReserveStockInput, ReserveStockOutput>(
    name        = "reserve-stock",
    description = "Reserves inventory in the warehouse to prevent overselling",
    inputClass  = ReserveStockInput::class,
    outputClass = ReserveStockOutput::class
) {
    override fun handle(input: ReserveStockInput): ReserveStockOutput =
        ReserveStockOutput(
            reservationId = "RES-${UUID.randomUUID()}",
            reservedItems = listOf(
                InventoryItem("SKU-001", 2, "${input.warehouseRegion}-WH-01"),
                InventoryItem("SKU-002", 1, "${input.warehouseRegion}-WH-01")
            )
        )
}

@Singleton
class UpdateWarehouseStep : TemporalStep<UpdateWarehouseInput, UpdateWarehouseOutput>(
    name        = "update-warehouse",
    description = "Deducts reserved quantities from warehouse inventory records",
    inputClass  = UpdateWarehouseInput::class,
    outputClass = UpdateWarehouseOutput::class
) {
    override fun handle(input: UpdateWarehouseInput): UpdateWarehouseOutput =
        UpdateWarehouseOutput(
            warehouseUpdateId = "WU-${UUID.randomUUID()}",
            updatedAt         = Instant.now().toString()
        )
}
