package com.spectro.app.data

import org.junit.Assert.*
import org.junit.Test

class Beta7BusinessTest {
    private val product = ZenthaProduct(id = 1, name = "Kit", standardCost = 5.0, standardPrice = 30.0, printingCost = 2.0, sewingCost = 1.0, accessoriesCost = .5, packagingCost = .5, otherCost = 1.0)
    private fun item(qty: Int = 10) = ZenthaOrderItem(orderId = 7, productId = 1, productName = "Kit", quantity = qty, unitPrice = 20.0, unitCost = 10.0, variantId = 2)
    private val order = ZenthaOrder(id = 7, clientName = "Club", orderDate = "2026-10-01", discount = 10.0, shippingRevenue = 5.0, otherRevenue = 2.0)

    @Test fun gocciaHistoryHasTwoOrdersAndSevenLegacyProductions() {
        val client = Client(id = 1, name = "Goccia", defaultPrintPrice = 2.0)
        val orders = (1L..2L).map { WorkOrder(id = it, receivedDate = "2026-01-01", client = "Goccia", clientId = 1, title = "Ordine $it", orderCode = "ORD-$it") }
        val records = (1L..10L).map { ProductionRecord(id = it, date = "2026-02-${it.toString().padStart(2,'0')}", clientId = 1, clientName = "Goccia", workOrderId = if(it <= 3) (it % 2) + 1 else null, fabricDisplayName = "Lycra", designs = listOf("Fiori"), grossRevenue = 100.0) }
        val snapshot = SpectroSnapshot(clients = listOf(client), workOrders = orders, production = records)
        val history = unifiedClientHistory(client, snapshot)
        assertEquals(9, history.size)
        assertEquals(2, history.count { it is ClientHistoryEntry.Order })
        assertEquals(7, history.count { it is ClientHistoryEntry.Legacy })
        assertEquals(9, history.map { it.key }.distinct().size)
        assertEquals(history.map { it.date }.sortedDescending(), history.map { it.date })
        assertEquals(9, unifiedClientHistory(client, snapshot, "lycra fiori").size)
        assertEquals(1, unifiedClientHistory(client, snapshot, "2026-02-10").size)
        assertEquals(1000.0, snapshot.production.sumOf { it.grossRevenue }, .001)
        assertEquals(1, unifiedClientHistory(client, snapshot, "ORD-1").size)
    }
    @Test fun clientIdIsAuthoritativeAndNamesOnlyMatchLegacyNullIds() {
        val c = Client(id = 1, name = "Goccia", defaultPrintPrice = 1.0)
        assertTrue(c.matches(1, "Nome precedente")); assertFalse(c.matches(2, "Goccia")); assertTrue(c.matches(null, "  GOCCIA  "))
    }
    @Test fun configurableBandsAndClientOverrideHaveExplicitPrecedence() {
        val rules = listOf(ZenthaPriceListRule(id = 1, priceListId = 1, productId = 1, minQty = 1, maxQty = 9, unitPrice = 25.0), ZenthaPriceListRule(id = 2, priceListId = 1, productId = 1, minQty = 10, unitPrice = 20.0), ZenthaPriceListRule(id = 3, priceListId = 1, productId = 1, variantId = 2, minQty = 10, unitPrice = 19.0), ZenthaPriceListRule(id = 4, productId = 1, minQty = 10, unitPrice = 17.0, clientId = 8))
        val data = ZenthaData(priceLists = listOf(ZenthaPriceList(1, "Standard")), priceRules = rules)
        assertEquals(25.0, ZenthaBusiness.price(product, 2, 9, null, data), 0.0)
        assertEquals(19.0, ZenthaBusiness.price(product, 2, 10, null, data), 0.0)
        assertEquals(17.0, ZenthaBusiness.price(product, 2, 10, 8, data), 0.0)
        assertEquals(20.0, ZenthaBusiness.price(product, 3, 50, null, data), 0.0)
        assertThrows(IllegalArgumentException::class.java) { ZenthaBusiness.validateRule(rules[0].copy(id = 9, minQty = 9, maxQty = 12), rules) }
        ZenthaBusiness.validateRule(rules[1], rules)
    }
    @Test fun orderEconomicsRoundSharesAndTrackMultiplePayments() {
        assertEquals(10.0, product.realCost, .001)
        val extras = listOf(ZenthaExtraCost(orderId = 7, label = "Loghi", amount = 10.01))
        val payments = listOf(ZenthaPayment(orderId = 7, date = "2026-10-01", amount = 30.0), ZenthaPayment(orderId = 7, date = "2026-10-02", amount = 20.0))
        val totals = ZenthaBusiness.totals(order, listOf(item()), extras, payments)
        assertEquals(197.0, totals.revenue, .001); assertEquals(86.99, totals.profit, .001)
        assertEquals(43.5, totals.shareA, .001); assertEquals(43.49, totals.shareB, .001)
        assertEquals(147.0, totals.outstanding, .001); assertEquals(ZenthaPaymentState.DEPOSIT, totals.paymentState)
        assertEquals(ZenthaPaymentState.PARTIAL, ZenthaBusiness.totals(order.copy(status = ZenthaOrderStatus.DELIVERED), listOf(item()), extras, payments).paymentState)
        assertEquals(ZenthaPaymentState.UNPAID, ZenthaBusiness.totals(order, listOf(item()), extras, emptyList()).paymentState)
        val paid = ZenthaBusiness.totals(order, listOf(item()), extras, listOf(payments[0].copy(amount = 200.0)))
        assertEquals(ZenthaPaymentState.PAID, paid.paymentState); assertEquals(3.0, paid.overpayment, .001)
        assertThrows(IllegalArgumentException::class.java) { ZenthaBusiness.validateOrder(order.copy(discount = 201.0), listOf(item()), extras) }
        assertThrows(IllegalArgumentException::class.java) { ZenthaBusiness.validateOrder(order, listOf(item().copy(unitCost = Double.NaN)), extras) }
    }
    @Test fun stockRejectsNegativeBalancesAndReconcilesOnlyTheDifference() {
        assertEquals(5, ZenthaBusiness.stockAfter(10, ZenthaMovementType.ORDER_USE, -5))
        assertThrows(IllegalArgumentException::class.java) { ZenthaBusiness.stockAfter(2, ZenthaMovementType.WASTE, -3) }
        assertThrows(IllegalArgumentException::class.java) { ZenthaBusiness.stockAfter(2, ZenthaMovementType.LOAD, -1) }
        val movement = ZenthaStockMovement(variantId = 2, type = ZenthaMovementType.ORDER_USE, quantity = -10, dateTime = "2026-10-01T10:00", orderId = 7)
        assertTrue(ZenthaBusiness.stockDeltas(listOf(item()), listOf(movement), 7, true).isEmpty())
        assertEquals(mapOf(2L to -2), ZenthaBusiness.stockDeltas(listOf(item(12)), listOf(movement), 7, true))
        assertEquals(mapOf(2L to 10), ZenthaBusiness.stockDeltas(emptyList(), listOf(movement), 7, false))
        assertTrue(ZenthaBusiness.stockDeltas(listOf(item().copy(variantId = null)), emptyList(), 7, true).isEmpty())
    }
    @Test fun statsCountEachOrderOnceAndExcludeCancelledOrders() {
        val s = SpectroSnapshot(zenthaOrders = listOf(order, order.copy(id = 8, status = ZenthaOrderStatus.CANCELLED)), zenthaItems = listOf(item(), item().copy(orderId = 8)), zentha = ZenthaData(events = listOf(ZenthaOrderEvent(orderId = 7, dateTime = "2026-10-06T10:00", toStatus = "DELIVERED"))))
        val stats = zenthaStatistics(s, "2026")
        assertEquals(197.0, stats.revenue, .001); assertEquals(10.0, stats.byProduct.single().second, .001); assertEquals(5.0, stats.averageDeliveryDays!!, .001)
        assertEquals(0.0, zenthaStatistics(s, "2025").revenue, .001)
    }
    @Test fun disabledAssignedListFallsBackToStandardAndProductStatisticsUseIds() {
        val data = ZenthaData(priceLists = listOf(ZenthaPriceList(1, "Standard"), ZenthaPriceList(2, "VIP", active = false)), priceRules = listOf(ZenthaPriceListRule(priceListId = 1, productId = 1, unitPrice = 19.0)), clientProfiles = listOf(ZenthaClientProfile(clientId = 8, assignedPriceListId = 2)))
        assertEquals(19.0, ZenthaBusiness.price(product, null, 10, 8, data), .001)
        val stats = zenthaStatistics(SpectroSnapshot(zenthaOrders = listOf(order), zenthaItems = listOf(item().copy(productName = "Old name"), item().copy(productName = "New name")), zenthaProducts = listOf(product)))
        assertEquals(1, stats.byProduct.size); assertEquals(20.0, stats.byProduct.single().second, .001)
    }

    @Test fun dashboardViewsMatchTheirCountsAndExcludeFinishedLateOrders() {
        val due = order.copy(dueDate = "2026-10-01", status = ZenthaOrderStatus.PRODUCTION)
        assertTrue(matchesZenthaOrderView(due, "In ritardo", 100.0, "2026-10-09"))
        assertTrue(matchesZenthaOrderView(due, "In produzione", 100.0, "2026-10-09"))
        assertFalse(matchesZenthaOrderView(due.copy(status = ZenthaOrderStatus.DELIVERED), "In ritardo", 100.0, "2026-10-09"))
        assertFalse(matchesZenthaOrderView(due.copy(status = ZenthaOrderStatus.CANCELLED), "Da incassare", 100.0, "2026-10-09"))
        assertTrue(matchesZenthaOrderView(due, "Pagati", 0.0, "2026-10-09"))
        assertFalse(matchesZenthaOrderView(due, "Pronti", 0.0, "2026-10-09"))
    }

}
