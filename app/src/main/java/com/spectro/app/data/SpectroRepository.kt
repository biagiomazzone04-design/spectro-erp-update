package com.spectro.app.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

class SpectroRepository(context: Context) {
    private val db = SpectroDatabase(context.applicationContext)

    fun snapshot(): SpectroSnapshot = SpectroSnapshot(
        clients = db.getClients(), inventory = db.getInventory(), production = db.getProduction(), workOrders = db.getWorkOrders(), settings = db.getSettings(),
        clientIssues = db.getClientIssues(), issueUpdates = db.getIssueUpdates(), payments = db.getPayments(),
        fabricRolls = db.getFabricRolls(), stockMovements = db.getStockMovements(), rollUsages = db.getRollUsages(), timelineEvents = db.getTimelineEvents(),
        cottonOrders = db.getCottonOrders(), zenthaProducts = db.getZenthaProducts(), zenthaOrders = db.getZenthaOrders(), zenthaItems = db.getZenthaItems(),
        zenthaExtraCosts = db.getZenthaExtraCosts(), zenthaPayments = db.getZenthaPayments(),
        economicWorks = db.getEconomicWorks(), operationalTasks = db.getOperationalTasks()
    )

    fun saveClient(client: Client): Long {
        require(client.name.isNotBlank()) { "Inserisci il nome del cliente." }
        require(client.defaultPrintPrice >= 0) { "Il prezzo base non può essere negativo." }
        val duplicate = db.getClients().any { it.id != client.id && it.name.equals(client.name.trim(), ignoreCase = true) }
        require(!duplicate) { "Esiste già un cliente con questo nome." }
        return if (client.id == 0L) db.insertClient(client) else { db.updateClient(client); client.id }
    }

    fun deleteClient(id: Long) {
        val client = db.getClients().firstOrNull { it.id == id } ?: return
        val used = db.getProduction().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getWorkOrders().any { it.clientId == id || it.client.equals(client.name, true) } ||
            db.getClientIssues().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getPayments().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getCottonOrders().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getZenthaOrders().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getEconomicWorks().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getOperationalTasks().any { it.clientId == id || it.clientName.equals(client.name, true) }
        require(!used) { "Cliente presente nello storico. Per proteggere ordini, produzione e contabilità non può essere eliminato." }
        db.deleteClient(id)
    }

    fun saveInventory(item: InventoryItem): Long {
        validateInventory(item)
        return if (item.id == 0L) db.transaction { sql ->
            val id = db.insertInventory(item.copy(quantityMeters = 0.0), sql)
            if (item.quantityMeters > 0) addRollInternal(id, "COLLO-001", item.quantityMeters, item.createdDate, "Carico iniziale", emptyList(), sql)
            syncInventoryQuantity(id, sql)
            id
        } else { db.updateInventory(item.copy(quantityMeters = rollTotal(item.id))); item.id }
    }

    fun createInventoryWithRolls(item: InventoryItem, rolls: List<FabricRollInput>): Long {
        validateInventory(item)
        val clean = rolls.filter { it.meters > 0 }
        require(clean.isNotEmpty()) { "Inserisci almeno un collo con metraggio maggiore di zero." }
        return db.transaction { sql ->
            val id = db.insertInventory(item.copy(id = 0, quantityMeters = clean.sumOf { it.meters }), sql)
            clean.forEachIndexed { index, input ->
                val code = input.code.trim().ifBlank { "COLLO-${(index + 1).toString().padStart(3, '0')}" }
                addRollInternal(id, code, input.meters, item.createdDate, input.note.ifBlank { "Carico tessuto" }, input.images, sql)
            }
            syncInventoryQuantity(id, sql)
            id
        }
    }

    fun addRoll(inventoryId: Long, rollCode: String, meters: Double, note: String = "", images: List<String> = emptyList()): Long = db.transaction { sql ->
        require(meters > 0) { "Il metraggio del collo deve essere maggiore di zero." }
        require(db.getInventoryById(inventoryId, sql) != null) { "Tessuto non trovato." }
        val code = rollCode.trim().ifBlank { nextRollCode(inventoryId, sql) }
        val id = addRollInternal(inventoryId, code, meters, LocalDate.now().toString(), note.ifBlank { "Nuovo collo" }, images, sql)
        syncInventoryQuantity(inventoryId, sql)
        id
    }

    fun adjustRoll(rollId: Long, newAvailableMeters: Double, note: String) = db.transaction { sql ->
        require(newAvailableMeters >= 0) { "La giacenza non può essere negativa." }
        require(note.isNotBlank()) { "Inserisci il motivo della rettifica." }
        val roll = db.getFabricRollById(rollId, sql) ?: throw IllegalArgumentException("Collo non trovato.")
        val delta = newAvailableMeters - roll.availableMeters
        if (abs(delta) < .0001) return@transaction
        val status = rollStatus(newAvailableMeters, roll.initialMeters)
        db.updateFabricRoll(roll.copy(availableMeters = newAvailableMeters, status = status), sql)
        db.insertStockMovement(StockMovement(inventoryId = roll.inventoryId, rollId = roll.id, type = StockMovementType.ADJUSTMENT, dateTime = nowDateTime(), meters = delta, note = note.trim()), sql)
        syncInventoryQuantity(roll.inventoryId, sql)
    }

    fun deleteInventory(id: Long) {
        require(db.getProduction().none { it.fabricId == id } && db.getCottonOrders().none { it.inventoryId == id }) { "Questo tessuto è collegato allo storico. Non può essere eliminato." }
        require(db.getRollUsages().none { it.inventoryId == id }) { "Questo tessuto ha movimenti collegati a ordini. Imposta la giacenza a zero invece di eliminarlo." }
        db.transaction { sql -> db.getFabricRolls(sql).filter { it.inventoryId == id }.forEach { db.deleteFabricRoll(it.id, sql) }; db.deleteInventory(id, sql) }
    }

    fun saveSettings(settings: AppSettings) {
        require(settings.dailyFixedCost >= 0 && settings.plotterX1Cost >= 0 && settings.plotterX2Cost >= 0 && settings.protectionPaperCost >= 0) { "I parametri di costo non possono essere negativi." }
        require(settings.zenthaSplitA >= 0 && settings.zenthaSplitB >= 0 && abs(settings.zenthaSplitA + settings.zenthaSplitB - 100.0) < .01) { "Le quote Zentha devono totalizzare 100%." }
        db.saveSettings(settings)
    }

    fun saveWorkOrder(item: WorkOrder): Long = db.transaction { sql ->
        validateWorkOrder(item)
        val previous = if (item.id != 0L) db.getWorkOrderById(item.id, sql) else null
        if (previous == null) {
            val provisional = item.copy(orderCode = item.orderCode.trim())
            val id = db.insertWorkOrder(provisional, sql)
            val code = provisional.orderCode.ifBlank { generateOrderCode("SPE", provisional.receivedDate, id) }
            val saved = provisional.copy(id = id, orderCode = code, status = if (provisional.status == WorkStatus.RECEIVED) WorkStatus.IN_PROCESS else provisional.status)
            db.updateWorkOrder(saved, sql)
            insertSystemTimeline(saved, "Ordine creato", "Ordine inserito in Spectro ERP.", TimelineCategory.STATUS_CHANGE, sql)
            id
        } else {
            val updated = item.copy(orderCode = item.orderCode.ifBlank { previous.orderCode })
            db.updateWorkOrder(updated, sql)
            val changes = mutableListOf<String>()
            if (previous.dueDate != updated.dueDate) changes += "Consegna: ${previous.dueDate.ifBlank { "-" }} → ${updated.dueDate.ifBlank { "-" }}"
            if (abs(previous.orderedMeters - updated.orderedMeters) > .0001) changes += "Metri: ${previous.orderedMeters} → ${updated.orderedMeters}"
            if (previous.fabricInventoryId != updated.fabricInventoryId) changes += "Tessuto/magazzino modificato"
            if (previous.designCode != updated.designCode) changes += "Codice disegno: ${previous.designCode.ifBlank { "-" }} → ${updated.designCode.ifBlank { "-" }}"
            if (previous.description != updated.description) {
                changes += "Note ordine modificate:\nPRIMA: ${previous.description.ifBlank { "-" }}\nDOPO: ${updated.description.ifBlank { "-" }}"
            }
            if (previous.images != updated.images) changes += "Allegati ordine modificati: ${previous.images.size} → ${updated.images.size}"
            if (previous.status != updated.status) changes += "Stato: ${previous.status.name} → ${updated.status.name}"
            if (changes.isNotEmpty()) {
                insertSystemTimeline(
                    updated,
                    "Ordine modificato",
                    changes.joinToString("\n"),
                    TimelineCategory.ORDER_CHANGE,
                    sql,
                    previous.dueDate,
                    updated.dueDate,
                    images = (previous.images + updated.images).distinct()
                )
            }
            updated.id
        }
    }

    fun deleteWorkOrder(id: Long) = db.transaction { sql ->
        // Eliminazione completa ma sicura: prima ripristina gli eventuali consumi di magazzino.
        db.getProduction(sql).filter { it.workOrderId == id }.forEach { record ->
            restoreProductionConsumption(record, sql)
            db.deleteProduction(record.id, sql)
        }
        restoreOrderRollUsages(OrderKind.WORK, id, sql)
        db.getTimelineEvents(sql).filter { it.workOrderId == id }.forEach { db.deleteTimelineEvent(it.id, sql) }
        db.deleteWorkOrder(id, sql)
    }

    fun setWorkStatus(id: Long, status: WorkStatus, note: String = "") = db.transaction { sql ->
        val item = db.getWorkOrderById(id, sql) ?: return@transaction
        if (item.status == status) return@transaction
        val effective = if (status in listOf(WorkStatus.DELIVERED, WorkStatus.COMPLETED, WorkStatus.CLOSED)) LocalDate.now().toString() else item.effectiveDeliveryDate
        val updated = item.copy(status = status, effectiveDeliveryDate = effective, blocker = when (status) { WorkStatus.WAITING_CLIENT -> Blocker.WAITING_CLIENT; WorkStatus.WAITING_MATERIAL -> Blocker.WAITING_MATERIAL; else -> if (status == WorkStatus.BLOCKED) item.blocker else Blocker.NONE })
        db.updateWorkOrder(updated, sql)
        insertSystemTimeline(updated, "Stato ordine aggiornato", "${item.status.name} → ${status.name}${if (note.isNotBlank()) "\n${note.trim()}" else ""}", TimelineCategory.STATUS_CHANGE, sql)
    }

    fun completeWorkOrder(id: Long, completionNote: String = "") = db.transaction { sql ->
        val item = db.getWorkOrderById(id, sql) ?: return@transaction
        if (item.status.isArchivedOrder()) return@transaction
        require(item.status != WorkStatus.CANCELLED) { "Un ordine annullato non può essere evaso." }

        val productions = db.getProduction(sql)
            .filter { it.workOrderId == id && it.type == RecordType.PRODUCTION }
            .sortedWith(compareBy<ProductionRecord> { it.date }.thenBy { it.id })
        require(productions.isNotEmpty()) { "Prima di completare l'ordine crea o collega almeno una produzione." }

        val latestProduction = productions.last()
        val completedDate = LocalDate.now().toString()
        val updated = item.copy(
            status = WorkStatus.DELIVERED,
            effectiveDeliveryDate = completedDate,
            blocker = Blocker.NONE,
            productionRecordId = latestProduction.id
        )
        db.updateWorkOrder(updated, sql)

        val totalPaper = productions.sumOf { it.printedPaperMeters }
        val totalFabric = productions.sumOf { it.fabricUsedMeters }
        val totalRevenue = productions.sumOf { it.grossRevenue }
        val totalProfit = productions.sumOf { it.grossProfit }
        val notes = productions.mapNotNull { it.technicalNotes.trim().takeIf { note -> note.isNotBlank() } }.distinct()
        val detail = buildString {
            append("Ordine completato ed evaso. Tutte le ${productions.size} registrazioni produttive restano collegate allo storico.")
            if (totalPaper > 0) append("\nCarta stampata totale: ${"%.1f".format(totalPaper)} mt.")
            if (totalFabric > 0) append("\nTessuto lavorato totale: ${"%.1f".format(totalFabric)} mt.")
            if (totalRevenue > 0) append("\nFatturato produzioni: € ${"%.2f".format(totalRevenue)}.")
            append("\nUtile produzioni: € ${"%.2f".format(totalProfit)}.")
            if (notes.isNotEmpty()) append("\nNote produzione conservate: ${notes.size}.")
            if (completionNote.isNotBlank()) append("\nNota chiusura: ${completionNote.trim()}")
        }
        insertSystemTimeline(updated, "Ordine completato ed evaso", detail, TimelineCategory.DELIVERY, sql)
    }

    fun reopenWorkOrder(id: Long, note: String = "") = db.transaction { sql ->
        val item = db.getWorkOrderById(id, sql) ?: return@transaction
        if (!item.status.isArchivedOrder()) return@transaction
        val hasProductions = db.getProduction(sql).any { it.workOrderId == id && it.type == RecordType.PRODUCTION }
        val next = if (hasProductions) WorkStatus.IN_PRODUCTION else WorkStatus.IN_PROCESS
        val updated = item.copy(status = next, effectiveDeliveryDate = "")
        db.updateWorkOrder(updated, sql)
        insertSystemTimeline(updated, "Ordine riaperto", note.trim().ifBlank { "Ordine riaperto dallo storico." }, TimelineCategory.STATUS_CHANGE, sql)
    }

    fun cancelWorkOrder(id: Long, reason: String) = db.transaction { sql ->
        require(reason.isNotBlank()) { "Inserisci il motivo dell'annullamento." }
        val item = db.getWorkOrderById(id, sql) ?: return@transaction
        val updated = item.copy(status = WorkStatus.CANCELLED, cancelReason = reason.trim())
        db.updateWorkOrder(updated, sql)
        insertSystemTimeline(updated, "Ordine annullato", reason.trim(), TimelineCategory.STATUS_CHANGE, sql)
    }

    fun saveTimelineEvent(item: OrderTimelineEvent): Long {
        require(db.getWorkOrders().any { it.id == item.workOrderId }) { "Ordine non trovato." }
        require(validDate(item.date)) { "Data evento non valida." }
        require(item.title.isNotBlank()) { "Inserisci il titolo dell'aggiornamento." }
        require(item.description.isNotBlank() || item.images.isNotEmpty()) { "Inserisci una descrizione o almeno un allegato." }
        return if (item.id == 0L) db.insertTimelineEvent(item) else { db.updateTimelineEvent(item); item.id }
    }

    fun resolveTimelineEvent(id: Long, note: String) {
        val item = db.getTimelineEvents().firstOrNull { it.id == id } ?: return
        db.updateTimelineEvent(item.copy(status = TimelineStatus.RESOLVED, resolutionNote = note.trim(), resolvedDate = LocalDate.now().toString()))
    }
    fun deleteTimelineEvent(id: Long) = db.deleteTimelineEvent(id)

    fun saveClientIssue(item: ClientIssue): Long {
        require(item.clientName.isNotBlank()) { "Seleziona il cliente." }
        require(item.title.isNotBlank()) { "Inserisci un titolo breve per la criticità." }
        require(validDate(item.openedDate)) { "Data apertura non valida." }
        require(item.expectedDeliveryDate.isBlank() || validDate(item.expectedDeliveryDate)) { "Data consegna iniziale non valida." }
        require(item.revisedDeliveryDate.isBlank() || validDate(item.revisedDeliveryDate)) { "Nuova data consegna non valida." }
        require(item.delayDays >= 0) { "I giorni di ritardo non possono essere negativi." }
        return if (item.id == 0L) db.insertClientIssue(item) else { db.updateClientIssue(item); item.id }
    }
    fun deleteClientIssue(id: Long) = db.deleteClientIssue(id)
    fun addIssueUpdate(item: IssueUpdate): Long {
        require(item.note.isNotBlank() || item.images.isNotEmpty()) { "Inserisci una nota o almeno una prova fotografica." }
        require(validDate(item.date)) { "Data aggiornamento non valida." }
        require(db.getClientIssues().any { it.id == item.issueId }) { "Criticità non trovata." }
        return db.insertIssueUpdate(item)
    }
    fun deleteIssueUpdate(id: Long) = db.deleteIssueUpdate(id)

    fun savePayment(item: PaymentDeadline): Long {
        require(item.clientName.isNotBlank()) { "Seleziona il cliente." }
        require(item.amount >= 0) { "L'importo non può essere negativo." }
        require(validDate(item.dueDate)) { "Data scadenza non valida." }
        require(item.paidDate.isBlank() || validDate(item.paidDate)) { "Data pagamento non valida." }
        return if (item.id == 0L) db.insertPayment(item) else { db.updatePayment(item); item.id }
    }
    fun deletePayment(id: Long) = db.deletePayment(id)
    fun togglePayment(id: Long) {
        val item = db.getPayments().firstOrNull { it.id == id } ?: return
        db.updatePayment(item.copy(paid = !item.paid, paidDate = if (!item.paid) LocalDate.now().toString() else ""))
    }

    fun saveProduction(input: ProductionRecord, rollAllocations: Map<Long, Double> = emptyMap()): Long {
        validateProduction(input)
        return db.transaction { sql ->
            val settings = db.getSettings(sql)
            val previous = if (input.id != 0L) db.getProductionById(input.id, sql) else null
            if (previous != null) restoreProductionConsumption(previous, sql)
            val calculated = ProductionCalculator.calculate(input, settings, if (input.fabricMode == FabricMode.INVENTORY && input.fabricId != null) db.getInventoryById(input.fabricId, sql) else null)
            val savedId = if (calculated.id == 0L) db.insertProduction(calculated, sql) else { db.updateProduction(calculated, sql); calculated.id }
            val saved = calculated.copy(id = savedId)
            if (saved.type == RecordType.PRODUCTION && saved.fabricMode == FabricMode.INVENTORY && saved.fabricId != null) {
                val consume = max(0.0, saved.fabricUsedMeters) + max(0.0, saved.fabricWasteMeters)
                if (rollAllocations.isNotEmpty()) {
                    require(abs(rollAllocations.values.sum() - consume) <= .05) { "La somma dei colli selezionati deve corrispondere ai metri usati + sfrido." }
                    applyRollAllocations(OrderKind.PRODUCTION, savedId, saved.orderRef, saved.fabricId, rollAllocations, sql)
                } else applyLegacyInventoryConsumption(saved, sql)
            }
            saved.workOrderId?.let { workId ->
                db.getWorkOrderById(workId, sql)?.let { w ->
                    val otherProductions = db.getProduction(sql).filter { it.workOrderId == workId && it.type == RecordType.PRODUCTION && it.id != savedId }
                    val nextStatus = if (w.status.isArchivedOrder()) w.status else WorkStatus.IN_PRODUCTION
                    val updated = w.copy(status = nextStatus, productionRecordId = savedId)
                    db.updateWorkOrder(updated, sql)
                    if (!w.status.isArchivedOrder() && previous == null) {
                        if (otherProductions.isEmpty()) {
                            insertSystemTimeline(updated, "Ordine trasferito in produzione", "Creata la prima registrazione produttiva #$savedId. Tutto lo storico precedente resta associato allo stesso ordine.", TimelineCategory.PRODUCTION, sql)
                        } else {
                            insertSystemTimeline(updated, "Produzione aggiunta", "Aggiunta la registrazione produttiva #$savedId allo stesso ordine. Produzioni collegate: ${otherProductions.size + 1}.", TimelineCategory.PRODUCTION, sql)
                        }
                    }
                    val oldNote = previous?.technicalNotes?.trim().orEmpty()
                    val newNote = saved.technicalNotes.trim()
                    if (newNote.isNotBlank() && newNote != oldNote) {
                        val description = if (oldNote.isBlank()) {
                            newNote
                        } else {
                            "Nota precedente:\n$oldNote\n\nNuova nota di produzione:\n$newNote"
                        }
                        insertSystemTimeline(
                            updated,
                            if (oldNote.isBlank()) "Nota di produzione" else "Nota di produzione aggiornata",
                            description,
                            TimelineCategory.PRODUCTION,
                            sql
                        )
                    }
                }
            }
            savedId
        }
    }

    fun deleteProduction(id: Long) = db.transaction { sql ->
        db.getProductionById(id, sql)?.let { record ->
            record.workOrderId?.let { workId ->
                val linked = db.getWorkOrderById(workId, sql)
                require(linked == null || !linked.status.isArchivedOrder()) { "Questa produzione appartiene a un ordine già evaso. Riapri prima l'ordine se devi rimuovere la produzione." }
            }
            restoreProductionConsumption(record, sql)
            record.workOrderId?.let { workId ->
                db.getWorkOrderById(workId, sql)?.let { w ->
                    val remaining = db.getProduction(sql)
                        .filter { it.workOrderId == workId && it.type == RecordType.PRODUCTION && it.id != id }
                        .sortedWith(compareBy<ProductionRecord> { it.date }.thenBy { it.id })
                    val latest = remaining.lastOrNull()
                    db.updateWorkOrder(
                        w.copy(
                            productionRecordId = latest?.id,
                            status = if (latest != null) WorkStatus.IN_PRODUCTION else WorkStatus.IN_PROCESS
                        ),
                        sql
                    )
                }
            }
        }
        db.deleteProduction(id, sql)
    }

    fun saveCottonOrder(input: CottonOrder, rollAllocations: Map<Long, Double> = emptyMap()): Long = db.transaction { sql ->
        validateCotton(input)
        val previous = if (input.id != 0L) db.getCottonOrderById(input.id, sql) else null
        if (previous != null) restoreOrderRollUsages(OrderKind.COTTON, previous.id, sql)
        val id = if (input.id == 0L) db.insertCottonOrder(input.copy(orderCode = ""), sql) else { db.updateCottonOrder(input, sql); input.id }
        val order = input.copy(id = id, orderCode = input.orderCode.ifBlank { generateOrderCode("COT", input.orderDate, id) })
        db.updateCottonOrder(order, sql)
        if (order.fabricOwnerType == OwnerType.HOUSE && order.inventoryId != null) {
            val need = if (order.actualProducedMeters > 0) order.actualProducedMeters else order.orderedMeters
            require(rollAllocations.isNotEmpty()) { "Seleziona i colli utilizzati per il tessuto aziendale." }
            require(abs(rollAllocations.values.sum() - need) <= .05) { "I metri selezionati dai colli devono corrispondere ai metri di tessuto utilizzati." }
            applyRollAllocations(OrderKind.COTTON, id, order.orderCode, order.inventoryId, rollAllocations, sql)
        }
        id
    }
    fun deleteCottonOrder(id: Long) = db.transaction { sql -> restoreOrderRollUsages(OrderKind.COTTON, id, sql); db.deleteCottonOrder(id, sql) }

    fun saveZenthaProduct(item: ZenthaProduct): Long {
        require(item.name.isNotBlank()) { "Inserisci il nome del prodotto." }
        require(item.standardCost >= 0 && item.standardPrice >= 0) { "Costo e prezzo non possono essere negativi." }
        require(item.costBreakdownIsValid) { "Le componenti di costo non possono essere negative." }
        if (item.sku.isNotBlank()) {
            require(db.getZenthaProducts().none { it.id != item.id && it.sku.trim().equals(item.sku.trim(), true) }) {
                "Esiste già un articolo con codice ${item.sku.trim()}."
            }
        }
        val normalized = if (item.hasCostBreakdown) item.copy(standardCost = ZenthaCatalog.roundMoney(item.costBreakdownTotal)) else item
        return if (normalized.id == 0L) db.insertZenthaProduct(normalized) else { db.updateZenthaProduct(normalized); normalized.id }
    }
    fun deleteZenthaProduct(id: Long) {
        require(db.getZenthaItems().none { it.productId == id }) { "Prodotto già utilizzato in ordini Zentha. Disattivalo invece di eliminarlo." }
        db.deleteZenthaProduct(id)
    }

    fun saveZenthaOrder(order: ZenthaOrder, items: List<ZenthaOrderItem>, extraCosts: List<ZenthaExtraCost>): Long = db.transaction { sql ->
        require(order.clientName.isNotBlank()) { "Inserisci il cliente Zentha." }
        require(validDate(order.orderDate)) { "Data ordine non valida." }
        require(order.dueDate.isBlank() || validDate(order.dueDate)) { "Data consegna non valida." }
        require(items.isNotEmpty()) { "Aggiungi almeno un prodotto all'ordine Zentha." }
        require(items.all { it.quantity > 0 && it.unitPrice >= 0 && it.unitCost >= 0 }) { "Controlla quantità, prezzi e costi dei prodotti." }
        require(items.all { it.productName.isNotBlank() }) { "Ogni riga deve contenere un articolo." }
        require(extraCosts.all { it.amount >= 0 }) { "I costi extra non possono essere negativi." }
        val id = if (order.id == 0L) db.insertZenthaOrder(order.copy(orderCode = ""), sql) else { db.updateZenthaOrder(order, sql); order.id }
        val saved = order.copy(id = id, orderCode = order.orderCode.ifBlank { generateOrderCode("ZEN", order.orderDate, id) })
        db.updateZenthaOrder(saved, sql)
        db.deleteZenthaItems(id, sql); items.forEach { db.insertZenthaItem(it.copy(id = 0, orderId = id), sql) }
        db.deleteZenthaExtraCosts(id, sql); extraCosts.forEach { db.insertZenthaExtraCost(it.copy(id = 0, orderId = id), sql) }
        id
    }
    fun deleteZenthaOrder(id: Long) = db.transaction { sql ->
        require(db.getZenthaPayments(sql).none { it.orderId == id }) { "L'ordine contiene pagamenti. Annullalo invece di eliminarlo." }
        db.deleteZenthaItems(id, sql); db.deleteZenthaExtraCosts(id, sql); db.deleteZenthaOrder(id, sql)
    }

    /** Import idempotente degli ordini creati dal portale. Gli ID negativi non
     *  collidono con SQLite AUTOINCREMENT e vengono mantenuti nel mirror cloud. */
    fun importPendingWebZentha(payload: JSONObject): Int = db.transaction { sql ->
        val records = payload.optJSONArray("records") ?: return@transaction 0
        val workOrders = mutableListOf<WorkOrder>()
        val forcedWorkOrders = mutableSetOf<Long>()
        val productionRecords = mutableListOf<ProductionRecord>()
        val forcedProductionRecords = mutableSetOf<Long>()
        val orders = mutableListOf<ZenthaOrder>()
        val forcedOrders = mutableSetOf<Long>()
        val items = mutableListOf<ZenthaOrderItem>()
        for (index in 0 until records.length()) {
            val envelope = records.optJSONObject(index) ?: continue
            val raw = envelope.optJSONObject("payload") ?: continue
            when (envelope.optString("entity_type")) {
                "work_order" -> {
                    val legacyId = envelope.optLong("legacy_id")
                    if (envelope.optBoolean("force_update")) forcedWorkOrders += legacyId
                    workOrders += WorkOrder(
                        id = legacyId, receivedDate = raw.optString("receivedDate"), client = raw.optString("client"),
                        title = raw.optString("title"), description = raw.optString("description"),
                        status = runCatching { WorkStatus.valueOf(raw.optString("status")) }.getOrDefault(WorkStatus.IN_PROCESS),
                        dueDate = raw.optString("dueDate"),
                        priority = runCatching { Priority.valueOf(raw.optString("priority")) }.getOrDefault(Priority.NORMAL),
                        blocker = runCatching { Blocker.valueOf(raw.optString("blocker")) }.getOrDefault(Blocker.NONE),
                        clientId = raw.optLong("clientId").takeIf { raw.has("clientId") && !raw.isNull("clientId") },
                        orderCode = raw.optString("orderCode"),
                        fabricInventoryId = raw.optLong("fabricInventoryId").takeIf { raw.has("fabricInventoryId") && !raw.isNull("fabricInventoryId") },
                        orderedMeters = raw.optDouble("orderedMeters"), printPricePerMeter = raw.optDouble("printPricePerMeter"),
                        designCode = raw.optString("designCode"),
                        productionRecordId = raw.optLong("productionRecordId").takeIf { raw.has("productionRecordId") && !raw.isNull("productionRecordId") },
                        effectiveDeliveryDate = raw.optString("effectiveDeliveryDate"), cancelReason = raw.optString("cancelReason"),
                        materialReceivedDate = raw.optString("materialReceivedDate"), materialReceiptNotes = raw.optString("materialReceiptNotes")
                    )
                }
                "production_record" -> {
                    val legacyId = envelope.optLong("legacy_id")
                    if (envelope.optBoolean("force_update")) forcedProductionRecords += legacyId
                    val designs = raw.optJSONArray("designs")?.let { values ->
                        buildList { for (itemIndex in 0 until values.length()) add(values.optString(itemIndex)) }
                    } ?: emptyList()
                    val images = raw.optJSONArray("images")?.let { values ->
                        buildList { for (itemIndex in 0 until values.length()) add(values.optString(itemIndex)) }
                    } ?: emptyList()
                    productionRecords += ProductionRecord(
                        id = legacyId,
                        type = runCatching { RecordType.valueOf(raw.optString("type")) }.getOrDefault(RecordType.PRODUCTION),
                        date = raw.optString("date"),
                        clientId = raw.optLong("clientId").takeIf { raw.has("clientId") && !raw.isNull("clientId") },
                        clientName = raw.optString("clientName"), orderRef = raw.optString("orderRef"), designs = designs,
                        printPricePerMeter = raw.optDouble("printPricePerMeter"), plotter = raw.optString("plotter", "X1"),
                        printedPaperMeters = raw.optDouble("printedPaperMeters"), protectionPaperMeters = raw.optDouble("protectionPaperMeters"),
                        paperWasteMeters = raw.optDouble("paperWasteMeters"),
                        fabricMode = runCatching { FabricMode.valueOf(raw.optString("fabricMode")) }.getOrDefault(FabricMode.NONE),
                        fabricId = raw.optLong("fabricId").takeIf { raw.has("fabricId") && !raw.isNull("fabricId") },
                        fabricUsedMeters = raw.optDouble("fabricUsedMeters"), fabricWasteMeters = raw.optDouble("fabricWasteMeters"),
                        fabricSalePricePerMeter = raw.optDouble("fabricSalePricePerMeter"), manualFabricName = raw.optString("manualFabricName"),
                        fabricDisplayName = raw.optString("fabricDisplayName", "Nessuno"), technicalNotes = raw.optString("technicalNotes"),
                        images = images, refundCost = raw.optDouble("refundCost"), fabricLossCost = raw.optDouble("fabricLossCost"),
                        applyFixedCosts = raw.optBoolean("applyFixedCosts", true), paperPrintCost = raw.optDouble("paperPrintCost"),
                        protectionPaperCost = raw.optDouble("protectionPaperCost"), fabricUsedCost = raw.optDouble("fabricUsedCost"),
                        materialCostTotal = raw.optDouble("materialCostTotal"), grossRevenue = raw.optDouble("grossRevenue"),
                        grossProfit = raw.optDouble("grossProfit"),
                        workOrderId = raw.optLong("workOrderId").takeIf { raw.has("workOrderId") && !raw.isNull("workOrderId") },
                        zenthaOrderId = raw.optLong("zenthaOrderId").takeIf { raw.has("zenthaOrderId") && !raw.isNull("zenthaOrderId") }
                    )
                }
                "zentha_order" -> {
                    val legacyId = envelope.optLong("legacy_id")
                    if (envelope.optBoolean("force_update")) forcedOrders += legacyId
                    orders += ZenthaOrder(
                    id = legacyId, orderCode = raw.optString("orderCode"),
                    clientId = raw.optLong("clientId").takeIf { raw.has("clientId") && !raw.isNull("clientId") },
                    clientName = raw.optString("clientName"), orderDate = raw.optString("orderDate"), dueDate = raw.optString("dueDate"),
                    status = runCatching { ZenthaOrderStatus.valueOf(raw.optString("status")) }.getOrDefault(ZenthaOrderStatus.CONFIRMED),
                    notes = raw.optString("notes"), splitPercentA = raw.optDouble("splitPercentA", 50.0),
                    splitPercentB = raw.optDouble("splitPercentB", 50.0), chromiaRatePerMeter = raw.optDouble("chromiaRatePerMeter", 2.5)
                    )
                }
                "zentha_order_item" -> items += ZenthaOrderItem(
                    id = envelope.optLong("legacy_id"), orderId = raw.optLong("orderId"),
                    productId = raw.optLong("productId").takeIf { raw.has("productId") && !raw.isNull("productId") },
                    productName = raw.optString("productName"), quantity = raw.optInt("quantity"),
                    unitPrice = raw.optDouble("unitPrice"), unitCost = raw.optDouble("unitCost"),
                    skuSnapshot = raw.optString("skuSnapshot"), productDescriptionSnapshot = raw.optString("productDescriptionSnapshot"),
                    variant = raw.optString("variant"), color = raw.optString("color"), sizeBreakdown = raw.optString("sizeBreakdown"),
                    personalization = raw.optString("personalization"), productionNotes = raw.optString("productionNotes")
                )
            }
        }
        var imported = 0
        workOrders.forEach { order ->
            val existing = db.getWorkOrderById(order.id, sql)
            if (existing == null || order.id in forcedWorkOrders) {
                db.upsertCloudWorkOrder(order.copy(images = existing?.images ?: order.images, clientId = order.clientId ?: existing?.clientId), sql)
                imported++
            }
        }
        productionRecords.forEach { production ->
            val existing = db.getProductionById(production.id, sql)
            if (existing == null || production.id in forcedProductionRecords) {
                db.upsertCloudProduction(production.copy(images = if (production.images.isNotEmpty()) production.images else existing?.images.orEmpty()), sql)
                imported++
            }
        }
        orders.forEach { order ->
            val existing = db.getZenthaOrderById(order.id, sql)
            if (existing == null || order.id in forcedOrders) {
                db.upsertCloudZenthaOrder(order.copy(attachments = existing?.attachments ?: order.attachments, clientId = order.clientId ?: existing?.clientId), sql)
                if (existing == null) {
                items.filter { it.orderId == order.id }.forEach { db.upsertCloudZenthaItem(it, sql) }
                }
                imported++
            }
        }
        if (imported > 0) {
            val sync = db.getLocalSyncState(sql)
            db.saveLocalSyncState(sync.copy(pendingChanges = true), sql)
        }
        imported
    }
    fun addZenthaPayment(item: ZenthaPayment): Long {
        require(db.getZenthaOrders().any { it.id == item.orderId }) { "Ordine Zentha non trovato." }
        require(validDate(item.date)) { "Data pagamento non valida." }
        require(item.amount > 0) { "L'importo deve essere maggiore di zero." }
        return db.insertZenthaPayment(item.copy(id = 0))
    }
    fun deleteZenthaPayment(id: Long) = db.deleteZenthaPayment(id)

    fun saveEconomicWork(item: EconomicWork): Long {
        require(validDate(item.date)) { "Data lavoro non valida." }
        require(item.title.isNotBlank()) { "Inserisci il nome del lavoro." }
        require(item.quantity >= 0 && item.unitPrice >= 0 && item.manualGrossRevenue >= 0 && item.otherCosts >= 0) { "Quantità, prezzi e costi non possono essere negativi." }
        require(item.materials.all { it.quantity >= 0 && it.unitCost >= 0 }) { "Controlla quantità e costo dei materiali." }
        return if (item.id == 0L) db.insertEconomicWork(item) else { db.updateEconomicWork(item); item.id }
    }
    fun deleteEconomicWork(id: Long) = db.deleteEconomicWork(id)

    fun saveOperationalTask(item: OperationalTask): Long {
        require(validDate(item.createdDate)) { "Data attività non valida." }
        require(item.dueDate.isBlank() || validDate(item.dueDate)) { "Data scadenza attività non valida." }
        require(item.title.isNotBlank()) { "Inserisci il titolo dell'attività." }
        return if (item.id == 0L) db.insertOperationalTask(item) else { db.updateOperationalTask(item); item.id }
    }
    fun deleteOperationalTask(id: Long) = db.deleteOperationalTask(id)

    fun resetAll() = db.clearAll()
    fun replaceAll(snapshot: SpectroSnapshot) = db.replaceAll(snapshot)
    fun localSyncState(): LocalSyncState {
        val current = db.getLocalSyncState()
        if (current.deviceId.isNotBlank()) return current
        return current.copy(deviceId = UUID.randomUUID().toString()).also(db::saveLocalSyncState)
    }
    fun saveLocalSyncState(state: LocalSyncState) = db.saveLocalSyncState(state)

    private fun validateInventory(item: InventoryItem) {
        require(item.ownerName.isNotBlank()) { "Inserisci il proprietario del tessuto." }
        require(item.fabricName.isNotBlank()) { "Inserisci il nome del tessuto." }
        require(item.quantityMeters >= 0 && item.minimumQuantityMeters >= 0) { "Giacenza e soglia minima non possono essere negative." }
        require(item.purchasePricePerMeter >= 0) { "Il costo di acquisto non può essere negativo." }
        require(validDate(item.createdDate)) { "Data ingresso non valida." }
    }

    private fun validateWorkOrder(item: WorkOrder) {
        require(item.client.isNotBlank()) { "Inserisci il cliente dell'ordine." }
        require(item.title.isNotBlank()) { "Inserisci il titolo dell'ordine." }
        require(validDate(item.receivedDate)) { "Data ricezione non valida." }
        require(item.dueDate.isBlank() || validDate(item.dueDate)) { "Data consegna non valida." }
        require(item.orderedMeters >= 0 && item.printPricePerMeter >= 0) { "Metri e prezzo non possono essere negativi." }
    }

    private fun validateProduction(input: ProductionRecord) {
        require(validDate(input.date)) { "Data non valida." }
        require(input.printPricePerMeter >= 0 && input.printedPaperMeters >= 0 && input.protectionPaperMeters >= 0 && input.paperWasteMeters >= 0) { "Metri e prezzi non possono essere negativi." }
        require(input.paperWasteMeters <= input.printedPaperMeters + .0001) { "Lo sfrido carta non può superare i metri stampati." }
        require(input.fabricUsedMeters >= 0 && input.fabricWasteMeters >= 0 && input.fabricSalePricePerMeter >= 0) { "Metri e prezzo del tessuto non possono essere negativi." }
        require(input.refundCost >= 0 && input.fabricLossCost >= 0) { "I costi dell'evento non possono essere negativi." }
        if (input.type == RecordType.PRODUCTION) {
            require(input.clientName.isNotBlank()) { "Seleziona o inserisci un cliente." }
            require(input.printedPaperMeters > 0 || input.fabricUsedMeters > 0) { "Inserisci almeno i metri di carta o di tessuto lavorati." }
            if (input.fabricMode == FabricMode.INVENTORY) require(input.fabricId != null) { "Seleziona un tessuto dal magazzino." }
            if (input.fabricMode == FabricMode.MANUAL) require(input.manualFabricName.isNotBlank()) { "Inserisci il nome del tessuto manuale." }
        }
    }

    private fun validateCotton(input: CottonOrder) {
        require(input.clientName.isNotBlank()) { "Inserisci il cliente." }
        require(input.title.isNotBlank()) { "Inserisci il nome dell'ordine cotone." }
        require(validDate(input.orderDate)) { "Data ordine non valida." }
        require(input.dueDate.isBlank() || validDate(input.dueDate)) { "Data consegna non valida." }
        require(input.fabricName.isNotBlank()) { "Inserisci il tessuto." }
        require(input.orderedMeters > 0) { "Inserisci i metri ordinati." }
        require(input.productionCostPerMeter >= 0 && input.printPricePerMeter >= 0 && input.fabricCostPerMeter >= 0) { "Costi e prezzi non possono essere negativi." }
        if (input.fabricOwnerType == OwnerType.HOUSE) require(input.inventoryId != null) { "Seleziona il tessuto aziendale dal magazzino." }
    }

    private fun addRollInternal(inventoryId: Long, code: String, meters: Double, date: String, note: String, images: List<String>, sql: SQLiteDatabase): Long {
        val roll = FabricRoll(inventoryId = inventoryId, rollCode = code, initialMeters = meters, availableMeters = meters, createdDate = date, note = note, status = if (meters > 0) RollStatus.INTACT else RollStatus.DEPLETED, images = images)
        val id = db.insertFabricRoll(roll, sql)
        db.insertStockMovement(StockMovement(inventoryId = inventoryId, rollId = id, type = StockMovementType.LOAD, dateTime = "${date}T00:00", meters = meters, note = note), sql)
        return id
    }

    private fun nextRollCode(inventoryId: Long, sql: SQLiteDatabase): String {
        val n = db.getFabricRolls(sql).count { it.inventoryId == inventoryId } + 1
        return "COLLO-${n.toString().padStart(3, '0')}"
    }

    private fun rollTotal(inventoryId: Long, sql: SQLiteDatabase = db.readableDatabase): Double = db.getFabricRolls(sql).filter { it.inventoryId == inventoryId }.sumOf { it.availableMeters }
    private fun syncInventoryQuantity(inventoryId: Long, sql: SQLiteDatabase) = db.setInventoryQuantity(inventoryId, rollTotal(inventoryId, sql), sql)
    private fun rollStatus(available: Double, initial: Double) = when { available <= .0001 -> RollStatus.DEPLETED; available + .0001 >= initial -> RollStatus.INTACT; else -> RollStatus.OPEN }

    private fun applyRollAllocations(kind: OrderKind, orderId: Long, orderRef: String, inventoryId: Long, allocations: Map<Long, Double>, sql: SQLiteDatabase) {
        allocations.filterValues { it > 0 }.forEach { (rollId, meters) ->
            val roll = db.getFabricRollById(rollId, sql) ?: throw IllegalArgumentException("Collo non trovato.")
            require(roll.inventoryId == inventoryId) { "Il collo selezionato appartiene a un altro tessuto." }
            require(meters <= roll.availableMeters + .0001) { "Giacenza insufficiente nel collo ${roll.rollCode}: disponibili ${"%.2f".format(roll.availableMeters)} mt." }
            db.updateFabricRoll(roll.copy(availableMeters = roll.availableMeters - meters, status = rollStatus(roll.availableMeters - meters, roll.initialMeters)), sql)
            db.insertRollUsage(RollUsage(orderKind = kind, orderId = orderId, orderRef = orderRef, inventoryId = inventoryId, rollId = rollId, meters = meters, createdAt = nowDateTime()), sql)
            db.insertStockMovement(StockMovement(inventoryId = inventoryId, rollId = rollId, type = StockMovementType.ORDER_USE, dateTime = nowDateTime(), meters = -meters, orderKind = kind, orderId = orderId, orderRef = orderRef, note = "Prelievo per ordine"), sql)
        }
        syncInventoryQuantity(inventoryId, sql)
    }

    private fun restoreOrderRollUsages(kind: OrderKind, orderId: Long, sql: SQLiteDatabase): Boolean {
        val usages = db.getRollUsages(sql).filter { it.orderKind == kind && it.orderId == orderId }
        if (usages.isEmpty()) return false
        usages.forEach { usage ->
            val roll = db.getFabricRollById(usage.rollId, sql) ?: return@forEach
            val updated = roll.copy(availableMeters = roll.availableMeters + usage.meters, status = rollStatus(roll.availableMeters + usage.meters, roll.initialMeters))
            db.updateFabricRoll(updated, sql)
            db.insertStockMovement(StockMovement(inventoryId = usage.inventoryId, rollId = usage.rollId, type = StockMovementType.RETURN, dateTime = nowDateTime(), meters = usage.meters, orderKind = kind, orderId = orderId, orderRef = usage.orderRef, note = "Ripristino per modifica/eliminazione ordine"), sql)
            syncInventoryQuantity(usage.inventoryId, sql)
        }
        db.deleteRollUsages(kind, orderId, sql)
        return true
    }

    private fun restoreProductionConsumption(record: ProductionRecord, sql: SQLiteDatabase) {
        val restoredByRoll = restoreOrderRollUsages(OrderKind.PRODUCTION, record.id, sql)
        if (!restoredByRoll) restoreLegacyInventoryConsumption(record, sql)
    }

    private fun applyLegacyInventoryConsumption(record: ProductionRecord, sql: SQLiteDatabase) {
        if (record.type != RecordType.PRODUCTION || record.fabricMode != FabricMode.INVENTORY || record.fabricId == null) return
        val item = db.getInventoryById(record.fabricId, sql) ?: throw IllegalArgumentException("Tessuto non trovato in magazzino.")
        val consume = max(0.0, record.fabricUsedMeters) + max(0.0, record.fabricWasteMeters)
        require(consume <= item.quantityMeters + .0001) { "Giacenza insufficiente: disponibili ${"%.2f".format(item.quantityMeters)} mt, richiesti ${"%.2f".format(consume)} mt." }
        db.setInventoryQuantity(item.id, item.quantityMeters - consume, sql)
    }

    private fun restoreLegacyInventoryConsumption(record: ProductionRecord, sql: SQLiteDatabase) {
        if (record.type != RecordType.PRODUCTION || record.fabricMode != FabricMode.INVENTORY || record.fabricId == null) return
        val item = db.getInventoryById(record.fabricId, sql) ?: return
        db.setInventoryQuantity(item.id, item.quantityMeters + max(0.0, record.fabricUsedMeters) + max(0.0, record.fabricWasteMeters), sql)
    }

    private fun insertSystemTimeline(
        order: WorkOrder,
        title: String,
        description: String,
        category: TimelineCategory,
        sql: SQLiteDatabase,
        oldDue: String = "",
        newDue: String = "",
        images: List<String> = emptyList()
    ) {
        db.insertTimelineEvent(
            OrderTimelineEvent(
                workOrderId = order.id,
                clientId = order.clientId,
                clientName = order.client,
                orderRef = order.orderCode.ifBlank { order.title },
                date = LocalDate.now().toString(),
                time = LocalTime.now().withSecond(0).withNano(0).toString(),
                category = category,
                responsibility = TimelineResponsibility.NONE,
                severity = TimelineSeverity.INFO,
                status = TimelineStatus.RESOLVED,
                title = title,
                description = description,
                oldDueDate = oldDue,
                newDueDate = newDue,
                images = images,
                resolvedDate = LocalDate.now().toString()
            ),
            sql
        )
    }

    private fun generateOrderCode(prefix: String, date: String, id: Long): String {
        val year = runCatching { LocalDate.parse(date).year }.getOrDefault(LocalDate.now().year)
        return "$prefix-$year-${id.toString().padStart(4, '0')}"
    }

    private fun validDate(value: String) = runCatching { LocalDate.parse(value) }.isSuccess
    private fun nowDateTime() = LocalDateTime.now().withSecond(0).withNano(0).toString()
}
