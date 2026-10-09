package com.spectro.app.data

import java.util.Locale

/** IDs are authoritative. Name matching is only a fallback for records without a client ID. */
fun Client.matches(recordClientId: Long?, recordName: String): Boolean =
    if (recordClientId != null) recordClientId == id
    else name.trim().replace(Regex("\\s+"), " ").equals(recordName.trim().replace(Regex("\\s+"), " "), true)

sealed class ClientHistoryEntry {
    abstract val key: String
    abstract val date: String
    data class Order(val order: WorkOrder, val productions: List<ProductionRecord>, val fabric: String) : ClientHistoryEntry() {
        override val key = "order-${order.id}"
        override val date = (productions.map { it.date } + order.receivedDate + order.effectiveDeliveryDate).maxOrNull().orEmpty()
    }
    data class Legacy(val production: ProductionRecord) : ClientHistoryEntry() {
        override val key = "legacy-${production.id}"
        override val date = production.date
    }
    data class Cotton(val order: CottonOrder) : ClientHistoryEntry() {
        override val key = "cotton-${order.id}"
        override val date = order.orderDate
    }
    fun matches(query: String): Boolean {
        val fields = when (this) {
            is Order -> listOf(order.title, order.orderCode, order.designCode, order.receivedDate, order.effectiveDeliveryDate, order.description, fabric) + productions.flatMap { it.searchFields() }
            is Legacy -> production.searchFields()
            is Cotton -> listOf(order.title, order.orderCode, order.fabricName, order.orderDate, order.notes)
        }
        return query.trim().lowercase(Locale.ROOT).split(Regex("\\s+")).all { token -> fields.any { it.lowercase(Locale.ROOT).contains(token) } }
    }
}

private fun ProductionRecord.searchFields() = listOf(date, orderRef, fabricDisplayName, manualFabricName, technicalNotes) + designs

fun unifiedClientHistory(client: Client, snapshot: SpectroSnapshot, query: String = ""): List<ClientHistoryEntry> {
    val linked = snapshot.production.filter { it.type == RecordType.PRODUCTION }.groupBy { it.workOrderId }
    return buildList {
        snapshot.workOrders.filter { client.matches(it.clientId, it.client) }.forEach { order ->
            add(ClientHistoryEntry.Order(order, linked[order.id].orEmpty(), snapshot.inventory.firstOrNull { it.id == order.fabricInventoryId }?.fabricName.orEmpty()))
        }
        // Never infer an order link from a name/reference: that would hide legitimate legacy work.
        snapshot.production.filter { it.type == RecordType.PRODUCTION && it.workOrderId == null && client.matches(it.clientId, it.clientName) }
            .forEach { add(ClientHistoryEntry.Legacy(it)) }
        snapshot.cottonOrders.filter { client.matches(it.clientId, it.clientName) }.forEach { add(ClientHistoryEntry.Cotton(it)) }
    }.distinctBy { it.key }.filter { it.matches(query) }.sortedWith(compareByDescending<ClientHistoryEntry> { it.date }.thenByDescending { it.key })
}
