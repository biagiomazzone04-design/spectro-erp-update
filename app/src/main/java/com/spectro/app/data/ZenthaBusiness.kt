package com.spectro.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.temporal.ChronoUnit

object ZenthaBusiness {
    fun money(value: Double): Double {
        require(value.isFinite()) { "Importo non valido." }
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).toDouble()
    }
    fun nonNegative(vararg values: Double) = require(values.all { it.isFinite() && it >= 0 }) { "Importi non validi o negativi." }
    fun validateProduct(p: ZenthaProduct) {
        require(p.name.isNotBlank()) { "Inserisci il nome prodotto." }
        nonNegative(p.standardCost, p.standardPrice, p.printingCost, p.sewingCost, p.accessoriesCost, p.packagingCost, p.otherCost)
    }
    fun validateOrder(o: ZenthaOrder, items: List<ZenthaOrderItem>, extras: List<ZenthaExtraCost>) {
        require(o.clientName.isNotBlank()) { "Seleziona il cliente." }
        LocalDate.parse(o.orderDate)
        if (o.dueDate.isNotBlank()) LocalDate.parse(o.dueDate)
        require(items.isNotEmpty() && items.all { it.quantity > 0 && it.productName.isNotBlank() }) { "Inserisci prodotti e quantità intere positive." }
        items.forEach { nonNegative(it.unitPrice, it.unitCost) }
        extras.forEach { nonNegative(it.amount); require(it.label.isNotBlank()) { "Descrivi ogni costo extra." } }
        nonNegative(o.discount, o.shippingRevenue, o.otherRevenue, o.splitPercentA, o.splitPercentB)
        require(kotlin.math.abs(o.splitPercentA + o.splitPercentB - 100) < .001) { "Le quote devono totalizzare 100%." }
        require(o.discount <= money(items.sumOf { money(it.revenue) })) { "Lo sconto supera il valore dei prodotti." }
    }
    fun totals(o: ZenthaOrder, items: List<ZenthaOrderItem>, extras: List<ZenthaExtraCost>, payments: List<ZenthaPayment>): ZenthaTotals {
        val revenue = money(items.filter { it.orderId == o.id }.sumOf { money(it.revenue) } - o.discount + o.shippingRevenue + o.otherRevenue)
        val cost = money(items.filter { it.orderId == o.id }.sumOf { money(it.cost) })
        val extra = money(extras.filter { it.orderId == o.id }.sumOf { it.amount })
        val profit = money(revenue - cost - extra)
        val paid = money(payments.filter { it.orderId == o.id }.sumOf { it.amount })
        val share = money(profit * o.splitPercentA / 100)
        val paymentState = when {
            paid >= revenue -> ZenthaPaymentState.PAID
            paid <= 0 -> ZenthaPaymentState.UNPAID
            o.status in setOf(ZenthaOrderStatus.DELIVERED, ZenthaOrderStatus.CLOSED) -> ZenthaPaymentState.PARTIAL
            else -> ZenthaPaymentState.DEPOSIT
        }
        return ZenthaTotals(revenue, cost, extra, profit, share, money(profit - share), paid,
            money((revenue - paid).coerceAtLeast(0.0)), if (revenue > 0) profit / revenue * 100 else 0.0, paymentState, money((paid - revenue).coerceAtLeast(0.0)))
    }
    /** Quantity is the total for this product in the order, across its variants. */
    fun price(product: ZenthaProduct, variantId: Long?, quantity: Int, clientId: Long?, data: ZenthaData): Double {
        require(quantity > 0)
        val activeLists = data.priceLists.filter { it.active }.map { it.id }.toSet()
        val listId = data.clientProfiles.firstOrNull { it.clientId == clientId }?.assignedPriceListId?.takeIf { it in activeLists }
            ?: data.priceLists.firstOrNull { it.active && it.name.equals("Standard", true) }?.id
        val matches = data.priceRules.filter {
            it.productId == product.id && (it.variantId == null || it.variantId == variantId) &&
                quantity >= it.minQty && (it.maxQty == null || quantity <= it.maxQty) &&
                ((clientId != null && it.clientId == clientId) || (it.clientId == null && it.priceListId == listId && listId in activeLists))
        }.sortedWith(compareByDescending<ZenthaPriceListRule> { it.clientId != null }
            .thenByDescending { it.variantId != null }.thenByDescending { it.minQty }.thenBy { it.id })
        return matches.firstOrNull()?.unitPrice ?: product.standardPrice
    }
    fun validateRule(rule: ZenthaPriceListRule, existing: List<ZenthaPriceListRule>) {
        nonNegative(rule.unitPrice)
        require((rule.priceListId != null) xor (rule.clientId != null)) { "Scegli un listino oppure un prezzo cliente." }
        require(rule.minQty > 0 && (rule.maxQty == null || rule.maxQty >= rule.minQty)) { "Fascia quantità non valida." }
        require(existing.none { other -> other.id != rule.id && other.priceListId == rule.priceListId && other.clientId == rule.clientId && other.productId == rule.productId && other.variantId == rule.variantId && rule.minQty <= (other.maxQty ?: Int.MAX_VALUE) && other.minQty <= (rule.maxQty ?: Int.MAX_VALUE) }) { "Questa fascia si sovrappone a una regola esistente." }
    }
    fun stockAfter(balance: Int, type: ZenthaMovementType, quantity: Int): Int {
        require(quantity != 0) { "La quantità deve essere diversa da zero." }
        require(when(type) {
            ZenthaMovementType.LOAD, ZenthaMovementType.RETURN -> quantity > 0
            ZenthaMovementType.ORDER_USE, ZenthaMovementType.SAMPLE, ZenthaMovementType.WASTE -> quantity < 0
            ZenthaMovementType.ADJUSTMENT -> true
        }) { "Segno del movimento non valido." }
        val next = balance.toLong() + quantity
        require(next in 0..Int.MAX_VALUE.toLong()) { "Giacenza insufficiente o quantità troppo elevata." }
        return next.toInt()
    }
    /** Compare committed usage with desired usage: repeated saves have a zero delta. */
    fun stockDeltas(items: List<ZenthaOrderItem>, movements: List<ZenthaStockMovement>, orderId: Long, posted: Boolean): Map<Long, Int> {
        val desired = if (posted) items.filter { it.variantId != null }.groupBy { it.variantId!! }.mapValues { (_, rows) -> rows.sumOf { it.quantity.toLong() } } else emptyMap()
        val committed = movements.filter { it.orderId == orderId }.groupBy { it.variantId }.mapValues { (_, rows) -> -rows.sumOf { it.quantity.toLong() } }
        return (desired.keys + committed.keys).associateWith { id ->
            val delta = (committed[id] ?: 0) - (desired[id] ?: 0)
            require(delta in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) { "Quantità troppo elevata." }
            delta.toInt()
        }.filterValues { it != 0 }
    }
}

data class ZenthaStatistics(
    val revenue: Double, val profit: Double, val paid: Double, val outstanding: Double, val stockValue: Double,
    val byMonth: List<Pair<String, Double>>, val byClient: List<Pair<String, Double>>, val byProduct: List<Pair<String, Double>>,
    val bySize: List<Pair<String, Double>>, val productMargins: List<Pair<String, Double>>, val averageDeliveryDays: Double?
)
fun zenthaStatistics(s: SpectroSnapshot, period: String = ""): ZenthaStatistics {
    val orders = s.zenthaOrders.filter { it.status != ZenthaOrderStatus.CANCELLED && it.orderDate.startsWith(period) }
    val ids = orders.map { it.id }.toSet()
    val items = s.zenthaItems.filter { it.orderId in ids }
    val totals = orders.associate { it.id to ZenthaBusiness.totals(it, s.zenthaItems, s.zenthaExtraCosts, s.zenthaPayments) }
    val delivered = orders.mapNotNull { order ->
        s.zentha.events.filter { it.orderId == order.id && it.toStatus == ZenthaOrderStatus.DELIVERED.name }.minByOrNull { it.dateTime }?.let { event ->
            runCatching { ChronoUnit.DAYS.between(LocalDate.parse(order.orderDate), LocalDate.parse(event.dateTime.take(10))).takeIf { it >= 0 }?.toDouble() }.getOrNull()
        }
    }
    fun List<ZenthaOrderItem>.groupMetric(key: (ZenthaOrderItem) -> String, value: (ZenthaOrderItem) -> Double) = groupBy(key).map { (k, rows) -> k to rows.sumOf(value) }.sortedByDescending { it.second }
    fun productMetric(value: (ZenthaOrderItem) -> Double) = items.groupBy { it.productId?.let { id -> "id:$id" } ?: "name:${it.productName.trim().lowercase()}" }.values.map { rows ->
        val product = s.zenthaProducts.firstOrNull { it.id == rows.first().productId }
        (product?.let { "${it.name}${if(it.sku.isBlank()) "" else " • ${it.sku}"}" } ?: rows.first().productName) to rows.sumOf(value)
    }.sortedByDescending { it.second }
    return ZenthaStatistics(totals.values.sumOf { it.revenue }, totals.values.sumOf { it.profit }, totals.values.sumOf { it.paid }, totals.values.sumOf { it.outstanding },
        s.zentha.balances.sumOf { b -> b.quantityAvailable * (s.zentha.variants.firstOrNull { it.id == b.variantId }?.let { v -> s.zenthaProducts.firstOrNull { it.id == v.productId }?.realCost } ?: 0.0) },
        orders.groupBy { it.orderDate.take(7) }.map { (month, rows) -> month to rows.sumOf { totals.getValue(it.id).revenue } }.sortedBy { it.first },
        orders.groupBy { it.clientId?.toString() ?: it.clientName.trim().lowercase() }.values.map { rows -> rows.first().clientName to rows.sumOf { totals.getValue(it.id).revenue } }.sortedByDescending { it.second },
        productMetric { it.quantity.toDouble() }, items.groupMetric({ it.sizeSnapshot.ifBlank { "Non specificata (legacy)" } }, { it.quantity.toDouble() }),
        productMetric { it.margin }, delivered.takeIf { it.isNotEmpty() }?.average())
}
