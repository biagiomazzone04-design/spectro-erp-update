package com.spectro.app.data

import android.database.sqlite.SQLiteDatabase
import java.time.LocalDate
import java.time.LocalDateTime

/** All stock/order operations share the same SQLite transaction and preserve historical row IDs. */
class ZenthaRepository(private val db: SpectroDatabase) {
    private fun now() = LocalDateTime.now().toString()
    fun saveProduct(p: ZenthaProduct): Long = db.transaction { sql ->
        ZenthaBusiness.validateProduct(p)
        if (p.id == 0L) db.insertZenthaProduct(p, sql) else {
            require(db.getZenthaProducts(sql).any { it.id == p.id }) { "Prodotto non trovato." }
            db.updateZenthaProduct(p, sql); p.id
        }
    }
    fun deleteProduct(id: Long) = db.transaction { sql ->
        require(db.getZenthaItems(sql).none { it.productId == id } && ZenthaStore.variants(sql).none { it.productId == id } && ZenthaStore.priceRules(sql).none { it.productId == id } && ZenthaStore.roster(sql).none { it.productId == id }) { "Prodotto collegato allo storico. Disattivalo per conservarlo." }
        db.deleteZenthaProduct(id, sql)
    }
    fun saveVariant(v: ZenthaProductVariant): Long = db.transaction { sql ->
        require(db.getZenthaProducts(sql).any { it.id == v.productId }) { "Prodotto non trovato." }
        require(v.size.isNotBlank()) { "Inserisci una taglia (o Unica)." }
        val previous = ZenthaStore.variants(sql).firstOrNull { it.id == v.id }
        require(previous == null || previous.productId == v.productId) { "Non puoi spostare una variante su un altro prodotto." }
        val id = ZenthaStore.save(sql, v.copy(sku = v.sku.trim(), size = v.size.trim()))
        if (v.id == 0L) ZenthaStore.save(sql, ZenthaStockBalance(variantId = id))
        id
    }
    fun saveBalanceSettings(v: ZenthaStockBalance) = db.transaction { sql ->
        require(v.minimumQuantity >= 0)
        val current = ZenthaStore.balances(sql).firstOrNull { it.variantId == v.variantId } ?: error("Variante non trovata.")
        ZenthaStore.save(sql, current.copy(minimumQuantity = v.minimumQuantity, location = v.location.trim()))
    }
    fun moveStock(m: ZenthaStockMovement): Long = db.transaction { sql ->
        require(m.orderId == null && m.type != ZenthaMovementType.ORDER_USE) { "Usa lo scarico dalla scheda ordine." }
        require(m.note.isNotBlank()) { "Inserisci il motivo del movimento." }
        LocalDateTime.parse(m.dateTime)
        applyMovement(sql, m.copy(id = 0))
    }
    private fun applyMovement(sql: SQLiteDatabase, m: ZenthaStockMovement): Long {
        val balance = ZenthaStore.balances(sql).firstOrNull { it.variantId == m.variantId } ?: error("Giacenza variante non trovata.")
        val next = ZenthaBusiness.stockAfter(balance.quantityAvailable, m.type, m.quantity)
        ZenthaStore.save(sql, balance.copy(quantityAvailable = next))
        return ZenthaStore.save(sql, m)
    }
    fun savePriceList(v: ZenthaPriceList): Long = db.transaction { sql ->
        require(v.name.isNotBlank()) { "Inserisci il nome del listino." }
        require(ZenthaStore.priceLists(sql).none { it.id != v.id && it.name.equals(v.name.trim(), true) }) { "Nome listino già utilizzato." }
        ZenthaStore.save(sql, v.copy(name = v.name.trim()))
    }
    fun savePriceRule(v: ZenthaPriceListRule): Long = db.transaction { sql ->
        ZenthaBusiness.validateRule(v, ZenthaStore.priceRules(sql))
        require(db.getZenthaProducts(sql).any { it.id == v.productId }) { "Prodotto non trovato." }
        if (v.variantId != null) require(ZenthaStore.variants(sql).any { it.id == v.variantId && it.productId == v.productId }) { "Variante non appartenente al prodotto." }
        if (v.priceListId != null) require(ZenthaStore.priceLists(sql).any { it.id == v.priceListId }) { "Listino non trovato." }
        if (v.clientId != null) require(db.getClients(sql).any { it.id == v.clientId }) { "Cliente non trovato." }
        ZenthaStore.save(sql, v)
    }
    fun deletePriceRule(id: Long) = db.writableDatabase.delete("zentha_priceRules", "id=?", arrayOf(id.toString()))
    fun saveClientProfile(v: ZenthaClientProfile): Long = db.transaction { sql ->
        require(db.getClients(sql).any { it.id == v.clientId }) { "Cliente non trovato." }
        require(v.assignedPriceListId == null || ZenthaStore.priceLists(sql).any { it.id == v.assignedPriceListId && it.active }) { "Listino non disponibile." }
        val old = ZenthaStore.clientProfiles(sql).firstOrNull { it.clientId == v.clientId }
        ZenthaStore.save(sql, v.copy(id = old?.id ?: 0))
    }
    fun saveOrder(order: ZenthaOrder, items: List<ZenthaOrderItem>, extras: List<ZenthaExtraCost>): Long = db.transaction { sql ->
        ZenthaBusiness.validateOrder(order, items, extras)
        if (order.clientId != null) require(db.getClients(sql).any { it.id == order.clientId }) { "Cliente non trovato." }
        val previous = if (order.id != 0L) db.getZenthaOrderById(order.id, sql) ?: error("Ordine non trovato.") else null
        val variants = ZenthaStore.variants(sql)
        val oldItems = db.getZenthaItems(sql).filter { it.orderId == order.id }
        require(items.filter { it.id != 0L }.map { it.id }.distinct().size == items.count { it.id != 0L }) { "Riga duplicata." }
        items.forEach { item ->
            val old = oldItems.firstOrNull { it.id == item.id }
            require(item.id == 0L || old != null) { "Riga appartenente a un altro ordine." }
            if (item.variantId != null) {
                val variant = variants.firstOrNull { it.id == item.variantId } ?: error("Variante non trovata.")
                require(variant.productId == item.productId) { "La variante non appartiene al prodotto." }
                require(variant.active || old?.variantId == variant.id) { "Variante disattivata." }
            }
            if (item.productId != null) require(db.getZenthaProducts(sql).any { it.id == item.productId && (it.active || old?.productId == it.id) }) { "Prodotto non disponibile." }
        }
        val id = previous?.id ?: db.insertZenthaOrder(order.copy(id = 0, stockPosted = false), sql)
        val posted = previous?.stockPosted == true && order.status != ZenthaOrderStatus.CANCELLED
        val saved = order.copy(id = id, orderCode = previous?.orderCode?.ifBlank { null } ?: order.orderCode.ifBlank { "ZEN-${order.orderDate.take(4)}-${id.toString().padStart(4, '0')}" }, stockPosted = posted)
        val normalized = items.map { row ->
            val old = oldItems.firstOrNull { it.id == row.id }
            val variant = variants.firstOrNull { it.id == row.variantId }
            // Preserve variant descriptions in existing rows even after catalogue changes.
            if (old != null && old.variantId == row.variantId) row.copy(orderId = id, variantLabel = old.variantLabel, sizeSnapshot = old.sizeSnapshot, skuSnapshot = old.skuSnapshot)
            else row.copy(orderId = id, variantLabel = variant?.label.orEmpty(), sizeSnapshot = variant?.size.orEmpty(), skuSnapshot = variant?.sku.orEmpty())
        }
        reconcileStock(sql, id, normalized, posted)
        db.updateZenthaOrder(saved, sql)
        val retained = items.filter { it.id != 0L }.map { it.id }.toSet()
        oldItems.filter { it.id !in retained }.forEach { old ->
            // Customization survives line removal and becomes order-level.
            sql.execSQL("UPDATE zentha_customizations SET orderItemId=NULL WHERE orderItemId=?", arrayOf(old.id))
            sql.delete("zentha_order_items", "id=?", arrayOf(old.id.toString()))
        }
        normalized.forEach { if(it.id == 0L) db.insertZenthaItem(it, sql) else db.updateZenthaItem(it, sql) }
        val oldExtras = db.getZenthaExtraCosts(sql).filter { it.orderId == id }
        require(extras.filter { it.id != 0L }.map { it.id }.distinct().size == extras.count { it.id != 0L })
        require(extras.all { it.id == 0L || oldExtras.any { old -> old.id == it.id } })
        oldExtras.filter { old -> extras.none { it.id == old.id } }.forEach { sql.delete("zentha_extra_costs", "id=?", arrayOf(it.id.toString())) }
        extras.forEach { if(it.id == 0L) db.insertZenthaExtraCost(it.copy(orderId = id), sql) else db.updateZenthaExtraCost(it.copy(orderId = id), sql) }
        if(previous == null || previous.status != saved.status) addEvent(sql, saved, previous?.status, if(previous == null) "Ordine creato" else "Cambio stato")
        else if(previous != saved || oldItems != normalized || oldExtras != extras.map { it.copy(orderId = id) }) addEvent(sql, saved, saved.status, "Dati ordine aggiornati; distinta, allegati e cronologia conservati")
        id
    }
    private fun addEvent(sql: SQLiteDatabase, order: ZenthaOrder, from: ZenthaOrderStatus?, note: String) =
        ZenthaStore.save(sql, ZenthaOrderEvent(orderId = order.id, dateTime = now(), fromStatus = from?.name.orEmpty(), toStatus = order.status.name, note = note))
    private fun reconcileStock(sql: SQLiteDatabase, id: Long, items: List<ZenthaOrderItem>, posted: Boolean) {
        ZenthaBusiness.stockDeltas(items, ZenthaStore.movements(sql), id, posted).forEach { (variant, delta) ->
            applyMovement(sql, ZenthaStockMovement(variantId = variant, type = if(delta < 0) ZenthaMovementType.ORDER_USE else ZenthaMovementType.RETURN, quantity = delta, dateTime = now(), orderId = id, note = "Allineamento quantità ordine"))
        }
    }
    fun postStock(orderId: Long) = db.transaction { sql ->
        val order = db.getZenthaOrderById(orderId, sql) ?: error("Ordine non trovato.")
        require(order.status != ZenthaOrderStatus.CANCELLED) { "Ordine annullato." }
        val items = db.getZenthaItems(sql).filter { it.orderId == orderId }
        require(items.any { it.variantId != null }) { "Associa almeno una variante alle righe prima dello scarico." }
        reconcileStock(sql, orderId, items, true)
        if (!order.stockPosted) {
            db.updateZenthaOrder(order.copy(stockPosted = true), sql)
            addEvent(sql, order, order.status, "Scarico magazzino confermato per le righe con variante; righe legacy senza variante escluse")
        }
    }
    fun changeStatus(id: Long, status: ZenthaOrderStatus) = db.transaction { sql ->
        val order = db.getZenthaOrderById(id, sql) ?: error("Ordine non trovato.")
        if(order.status != status) {
            if (status == ZenthaOrderStatus.CANCELLED) reconcileStock(sql, id, emptyList(), false)
            db.updateZenthaOrder(order.copy(status = status, stockPosted = order.stockPosted && status != ZenthaOrderStatus.CANCELLED), sql)
            addEvent(sql, order.copy(status = status), order.status, "Cambio stato")
        }
    }
    fun deleteOrder(id: Long) { error("Per conservare distinta, movimenti e allegati, annulla l'ordine invece di eliminarlo.") }
    fun addPayment(p: ZenthaPayment): Long = db.transaction { sql ->
        require(db.getZenthaOrderById(p.orderId, sql) != null) { "Ordine non trovato." }
        LocalDate.parse(p.date)
        ZenthaBusiness.nonNegative(p.amount); require(ZenthaBusiness.money(p.amount) > 0) { "L'importo deve essere positivo." }
        val id = db.insertZenthaPayment(p.copy(id = 0, amount = ZenthaBusiness.money(p.amount)), sql)
        val order = db.getZenthaOrderById(p.orderId, sql)!!
        addEvent(sql, order, order.status, "Pagamento registrato: ${ZenthaBusiness.money(p.amount)} • ${p.date} • ${p.method.name}")
        id
    }
    fun saveRoster(v: ZenthaRosterEntry): Long = db.transaction { sql ->
        require(db.getZenthaOrderById(v.orderId, sql) != null)
        require(v.surname.isNotBlank() || v.name.isNotBlank()) { "Inserisci il nome dell'atleta." }
        if (v.id != 0L) require(ZenthaStore.roster(sql).any { it.id == v.id && it.orderId == v.orderId })
        if(v.productId != null) require(db.getZenthaProducts(sql).any { it.id == v.productId })
        val id = ZenthaStore.save(sql, v)
        val order = db.getZenthaOrderById(v.orderId, sql)!!
        addEvent(sql, order, order.status, "Distinta aggiornata: ${v.surname} ${v.name} • #${v.number} • maglia ${v.shirtSize} / pantalone ${v.shortsSize}")
        id
    }
    fun deleteRoster(id: Long) = db.transaction { sql ->
        val row = ZenthaStore.roster(sql).firstOrNull { it.id == id } ?: return@transaction
        val order = db.getZenthaOrderById(row.orderId, sql) ?: error("Ordine non trovato.")
        addEvent(sql, order, order.status, "Rimosso dalla distinta: ${row.surname} ${row.name} • #${row.number} • maglia ${row.shirtSize} / pantalone ${row.shortsSize} • ${row.notes}")
        sql.delete("zentha_roster", "id=?", arrayOf(id.toString()))
        Unit
    }
    fun saveCustomization(v: ZenthaCustomization): Long = db.transaction { sql ->
        require(db.getZenthaOrderById(v.orderId, sql) != null)
        require(v.type.isNotBlank() && (v.value.isNotBlank() || v.notes.isNotBlank() || v.attachments.isNotEmpty())) { "Descrivi la personalizzazione o allega un file." }
        if(v.orderItemId != null) require(db.getZenthaItems(sql).any { it.id == v.orderItemId && it.orderId == v.orderId })
        if(v.id != 0L) require(ZenthaStore.customizations(sql).any { it.id == v.id && it.orderId == v.orderId })
        val id = ZenthaStore.save(sql, v)
        val order = db.getZenthaOrderById(v.orderId, sql)!!
        addEvent(sql, order, order.status, "Personalizzazione ${v.type}: ${if(v.approved) "mockup approvato" else "da approvare"}")
        id
    }
}
