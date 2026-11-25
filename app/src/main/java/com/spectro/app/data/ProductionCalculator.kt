package com.spectro.app.data

import kotlin.math.max

object ProductionCalculator {
    /** Preserva le regole economiche dell'app originale, rendendole però centralizzate e testabili. */
    fun calculate(input: ProductionRecord, settings: AppSettings, inventoryItem: InventoryItem?): ProductionRecord {
        if (input.type == RecordType.NEGATIVE) {
            val cost = max(0.0, input.refundCost) + max(0.0, input.fabricLossCost)
            return input.copy(
                clientName = if (input.clientName.isBlank()) "EVENTO NEGATIVO" else input.clientName,
                grossRevenue = 0.0,
                paperPrintCost = 0.0,
                protectionPaperCost = 0.0,
                fabricUsedCost = 0.0,
                materialCostTotal = cost,
                grossProfit = -cost
            )
        }

        val paperNet = max(0.0, input.printedPaperMeters - input.paperWasteMeters)
        val plotterCost = if (input.plotter.equals("X2", true)) settings.plotterX2Cost else settings.plotterX1Cost
        val paperCost = paperNet * plotterCost
        val protectionCost = max(0.0, input.protectionPaperMeters) * settings.protectionPaperCost
        var fabricCost = 0.0
        var fabricRevenue = 0.0
        var displayName = "Nessuno"

        when (input.fabricMode) {
            FabricMode.INVENTORY -> {
                val item = inventoryItem ?: error("Il tessuto selezionato non esiste più in magazzino.")
                displayName = "${item.fabricName} (${if (item.ownerType == OwnerType.HOUSE) "Nostro" else "Cliente"})"
                if (item.ownerType == OwnerType.HOUSE) {
                    // Mantiene il comportamento economico della versione React: costo sui metri usati, ricavo sui metri venduti.
                    fabricCost = max(0.0, input.fabricUsedMeters) * item.actualCostPerMeter
                    val salePrice = if (input.fabricSalePricePerMeter > 0) input.fabricSalePricePerMeter else item.defaultSalePricePerMeter
                    fabricRevenue = max(0.0, input.fabricUsedMeters) * max(0.0, salePrice)
                }
            }
            FabricMode.MANUAL -> displayName = input.manualFabricName.ifBlank { "Tessuto manuale" }
            FabricMode.NONE -> Unit
        }

        val revenue = max(0.0, input.printedPaperMeters) * max(0.0, input.printPricePerMeter) + fabricRevenue
        val materials = paperCost + protectionCost + fabricCost
        return input.copy(
            fabricDisplayName = displayName,
            fabricSalePricePerMeter = if (input.fabricMode == FabricMode.INVENTORY && input.fabricSalePricePerMeter <= 0) inventoryItem?.defaultSalePricePerMeter ?: 0.0 else input.fabricSalePricePerMeter,
            paperPrintCost = paperCost,
            protectionPaperCost = protectionCost,
            fabricUsedCost = fabricCost,
            materialCostTotal = materials,
            grossRevenue = revenue,
            grossProfit = revenue - materials
        )
    }
}
