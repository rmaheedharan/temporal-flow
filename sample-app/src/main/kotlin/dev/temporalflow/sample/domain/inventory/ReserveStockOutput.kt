package dev.temporalflow.sample.domain.inventory

data class ReserveStockOutput(val reservationId: String, val reservedItems: List<InventoryItem>)
