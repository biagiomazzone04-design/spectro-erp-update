package com.spectro.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProductionCalculatorTest {
    @Test fun productionWithoutFabricMatchesLegacyFormula() {
        val result = ProductionCalculator.calculate(
            ProductionRecord(date="2026-09-04", clientName="Test", printPricePerMeter=2.0, printedPaperMeters=100.0, protectionPaperMeters=100.0, paperWasteMeters=5.0),
            AppSettings(dailyFixedCost=760.0, plotterX1Cost=.8, plotterX2Cost=1.0, protectionPaperCost=.1), null
        )
        assertEquals(200.0, result.grossRevenue, .001)
        assertEquals(86.0, result.materialCostTotal, .001)
        assertEquals(114.0, result.grossProfit, .001)
    }

    @Test fun houseFabricAddsPurchaseCostAndSaleRevenue() {
        val fabric = InventoryItem(7, OwnerType.HOUSE, "Interno", "Poly", 100.0, 3.0, "2026-09-04")
        val result = ProductionCalculator.calculate(
            ProductionRecord(date="2026-09-04", clientName="Test", printedPaperMeters=0.0, fabricMode=FabricMode.INVENTORY, fabricId=7, fabricUsedMeters=20.0, fabricSalePricePerMeter=5.0),
            AppSettings(), fabric
        )
        assertEquals(100.0, result.grossRevenue, .001)
        assertEquals(60.0, result.materialCostTotal, .001)
        assertEquals(40.0, result.grossProfit, .001)
    }

    @Test fun houseFabricUsesProcessingCostAndDefaultSalePrice() {
        val fabric = InventoryItem(
            id = 7, ownerType = OwnerType.HOUSE, ownerName = "Azienda", fabricName = "Poly",
            quantityMeters = 100.0, purchasePricePerMeter = 1.5, createdDate = "2026-10-20",
            processingCostPerMeter = .5, defaultSalePricePerMeter = 2.0
        )
        val result = ProductionCalculator.calculate(
            ProductionRecord(date = "2026-10-20", clientName = "Adshape", fabricMode = FabricMode.INVENTORY, fabricId = 7, fabricUsedMeters = 26.0),
            AppSettings(), fabric
        )
        assertEquals(52.0, result.grossRevenue, .001)
        assertEquals(52.0, result.fabricUsedCost, .001)
        assertEquals(2.0, result.fabricSalePricePerMeter, .001)
    }

    @Test fun negativeEventProducesLoss() {
        val result = ProductionCalculator.calculate(
            ProductionRecord(type=RecordType.NEGATIVE, date="2026-09-04", refundCost=50.0, fabricLossCost=20.0), AppSettings(), null
        )
        assertEquals(70.0, result.materialCostTotal, .001)
        assertEquals(-70.0, result.grossProfit, .001)
    }
}
