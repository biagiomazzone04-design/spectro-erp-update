package com.spectro.app.data

import org.junit.Assert.assertEquals
import org.junit.Test

class Spectro3BusinessTest {
    @Test
    fun cottonHouseFabricCalculatesRealMargin() {
        val order = CottonOrder(
            clientName = "Caprese",
            orderDate = "2026-09-13",
            fabricName = "Voile",
            orderedMeters = 200.0,
            productionCostPerMeter = 3.50,
            printPricePerMeter = 5.50,
            fabricOwnerType = OwnerType.HOUSE,
            fabricCostPerMeter = 1.10
        )
        assertEquals(1100.0, order.grossRevenue, 0.001)
        assertEquals(700.0, order.productionCost, 0.001)
        assertEquals(220.0, order.fabricCost, 0.001)
        assertEquals(920.0, order.totalCost, 0.001)
        assertEquals(180.0, order.margin, 0.001)
        assertEquals(16.363636, order.marginPercent, 0.001)
    }

    @Test
    fun cottonClientFabricDoesNotAddFabricCost() {
        val order = CottonOrder(
            clientName = "Caprese",
            orderDate = "2026-09-13",
            fabricName = "Voile cliente",
            orderedMeters = 200.0,
            productionCostPerMeter = 3.50,
            printPricePerMeter = 5.50,
            fabricOwnerType = OwnerType.CLIENT,
            fabricCostPerMeter = 99.0
        )
        assertEquals(0.0, order.fabricCost, 0.001)
        assertEquals(400.0, order.margin, 0.001)
    }

    @Test
    fun zenthaOrderItemFreezesPriceAndCostSnapshot() {
        val item = ZenthaOrderItem(
            orderId = 1,
            productId = 10,
            productName = "Canotta Uomo Double",
            quantity = 20,
            unitPrice = 25.0,
            unitCost = 12.0
        )
        assertEquals(500.0, item.revenue, 0.001)
        assertEquals(240.0, item.cost, 0.001)
        assertEquals(260.0, item.margin, 0.001)
    }
}
