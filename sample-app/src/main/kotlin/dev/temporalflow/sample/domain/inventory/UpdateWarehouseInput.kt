package dev.temporalflow.sample.domain.inventory

data class UpdateWarehouseInput(val availableItems: List<InventoryItem>, val inventoryId: String)
