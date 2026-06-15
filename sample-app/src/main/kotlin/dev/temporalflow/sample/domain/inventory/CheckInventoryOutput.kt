package dev.temporalflow.sample.domain.inventory

data class CheckInventoryOutput(val availableItems: List<InventoryItem>, val inventoryId: String)
