package com.spectro.app.data

/** Validate Beta 7 references before restore. Legacy rows without variants remain valid. */
fun validateZenthaRestore(s: SpectroSnapshot) {
    val d = s.zentha
    val products = s.zenthaProducts.map { it.id }.toSet()
    val orders = s.zenthaOrders.associateBy { it.id }
    val clients = s.clients.map { it.id }.toSet()
    val variants = d.variants.associateBy { it.id }
    val lists = d.priceLists.map { it.id }.toSet()
    fun valid(condition: Boolean) = require(condition) { "Backup Zentha incoerente: riferimenti o giacenze non validi. Nessun dato sostituito." }
    d.variants.forEach { valid(it.productId in products) }
    d.balances.forEach { b -> valid(b.variantId in variants && b.quantityAvailable >= 0 && b.minimumQuantity >= 0); valid(d.movements.filter { it.variantId == b.variantId }.sumOf { it.quantity.toLong() } == b.quantityAvailable.toLong()) }
    d.variants.forEach { v -> valid(d.balances.count { it.variantId == v.id } == 1) }
    d.movements.forEach { m ->
        valid(m.variantId in variants && (m.orderId == null || m.orderId in orders) && m.quantity != 0)
        valid(when(m.type) {
            ZenthaMovementType.LOAD, ZenthaMovementType.RETURN -> m.quantity > 0
            ZenthaMovementType.ORDER_USE, ZenthaMovementType.SAMPLE, ZenthaMovementType.WASTE -> m.quantity < 0
            ZenthaMovementType.ADJUSTMENT -> true
        })
    }
    s.zenthaItems.filter { it.variantId != null }.forEach { valid(variants[it.variantId]?.productId == it.productId && it.orderId in orders && it.quantity > 0); ZenthaBusiness.nonNegative(it.unitPrice, it.unitCost) }
    d.priceRules.forEach { valid(it.productId in products && (it.variantId == null || variants[it.variantId]?.productId == it.productId) && (it.clientId == null || it.clientId in clients) && (it.priceListId == null || it.priceListId in lists)); ZenthaBusiness.validateRule(it, d.priceRules) }
    d.clientProfiles.forEach { valid(it.clientId in clients && (it.assignedPriceListId == null || it.assignedPriceListId in lists)) }
    d.roster.forEach { valid(it.orderId in orders && (it.productId == null || it.productId in products)) }
    d.customizations.forEach { c -> valid(c.orderId in orders && (c.orderItemId == null || s.zenthaItems.any { it.id == c.orderItemId && it.orderId == c.orderId })) }
    d.events.forEach { valid(it.orderId in orders) }
    s.zenthaOrders.forEach { o -> valid(ZenthaBusiness.stockDeltas(s.zenthaItems.filter { it.orderId == o.id }, d.movements, o.id, o.stockPosted).isEmpty()) }
}
