package com.spectro.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CottonAccountingTest {
    @Test fun completedCottonUsesCompletionMonthAndActualQuantities() {
        val order = CottonOrder(
            clientName = "Borriello", orderDate = "2026-10-28", completedDate = "2026-11-12",
            fabricName = "Cotone", status = WorkStatus.DELIVERED, orderedMeters = 100.0,
            actualProducedMeters = 95.0, deliveredMeters = 90.0, productionCostPerMeter = 1.5,
            printPricePerMeter = 4.0, fabricOwnerType = OwnerType.HOUSE, fabricCostPerMeter = 0.5
        )
        assertEquals("2026-11-12", order.accountingDate)
        assertEquals(360.0, order.grossRevenue, 0.001)
        assertEquals(190.0, order.totalCost, 0.001)
        assertEquals("2026-10-28", order.copy(completedDate = "").accountingDate)
    }

    @Test fun clientIdWinsOverNameAndLegacyNamesIgnoreWhitespace() {
        val client = Client(12, "Borriello  Srl", 0.0)
        assertTrue(matchesClient(client, 12, "Nome precedente"))
        assertTrue(matchesClient(client, null, " BORRIELLO SRL "))
        assertFalse(matchesClient(client, 13, "Borriello Srl"))
    }
}
