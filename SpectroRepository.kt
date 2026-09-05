package com.spectro.app.data

import android.content.Context
import kotlin.math.max

class SpectroRepository(context: Context) {
    private val db = SpectroDatabase(context.applicationContext)

    fun snapshot(): SpectroSnapshot = SpectroSnapshot(
        clients = db.getClients(), inventory = db.getInventory(), production = db.getProduction(), workOrders = db.getWorkOrders(), settings = db.getSettings(),
        clientIssues = db.getClientIssues(), issueUpdates = db.getIssueUpdates(), payments = db.getPayments()
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
            db.getWorkOrders().any { it.client.equals(client.name, true) } ||
            db.getClientIssues().any { it.clientId == id || it.clientName.equals(client.name, true) } ||
            db.getPayments().any { it.clientId == id || it.clientName.equals(client.name, true) }
        require(!used) { "Cliente presente nello storico. Per proteggere produzione, criticità o scadenze non può essere eliminato." }
        db.deleteClient(id)
    }

    fun saveInventory(item: InventoryItem): Long {
        require(item.ownerName.isNotBlank()) { "Inserisci il proprietario del tessuto." }
        require(item.fabricName.isNotBlank()) { "Inserisci il nome del tessuto." }
        require(item.quantityMeters >= 0 && item.minimumQuantityMeters >= 0) { "Giacenza e soglia minima non possono essere negative." }
        require(item.purchasePricePerMeter >= 0) { "Il costo di acquisto non può essere negativo." }
        return if (item.id == 0L) db.insertInventory(item) else { db.updateInventory(item); item.id }
    }

    fun deleteInventory(id: Long) {
        require(db.getProduction().none { it.fabricId == id }) { "Questo tessuto è collegato allo storico produzione. Imposta la giacenza a 0 invece di eliminarlo." }
        db.deleteInventory(id)
    }

    fun saveSettings(settings: AppSettings) {
        require(settings.dailyFixedCost >= 0 && settings.plotterX1Cost >= 0 && settings.plotterX2Cost >= 0 && settings.protectionPaperCost >= 0) { "I parametri di costo non possono essere negativi." }
        db.saveSettings(settings)
    }

    fun saveWorkOrder(item: WorkOrder): Long {
        require(item.client.isNotBlank()) { "Inserisci il cliente della lavorazione." }
        require(item.title.isNotBlank()) { "Inserisci il titolo della lavorazione." }
        require(validDate(item.receivedDate)) { "Data ricezione non valida." }
        require(item.dueDate.isBlank() || validDate(item.dueDate)) { "Data consegna non valida." }
        return if (item.id == 0L) db.insertWorkOrder(item) else { db.updateWorkOrder(item); item.id }
    }
    fun deleteWorkOrder(id: Long) = db.deleteWorkOrder(id)
    fun setWorkStatus(id: Long, status: WorkStatus) {
        val item = db.getWorkOrders().firstOrNull { it.id == id } ?: return
        db.updateWorkOrder(item.copy(status = status))
    }

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
        db.updatePayment(item.copy(paid = !item.paid, paidDate = if (!item.paid) java.time.LocalDate.now().toString() else ""))
    }

    fun saveProduction(input: ProductionRecord): Long {
        validateProduction(input)
        return db.transaction { sql ->
            val settings = db.getSettings(sql)
            val previous = if (input.id != 0L) db.getProductionById(input.id, sql) else null
            if (previous != null) restoreInventoryConsumption(previous, sql)
            val calculated = ProductionCalculator.calculate(input, settings, if (input.fabricMode == FabricMode.INVENTORY && input.fabricId != null) db.getInventoryById(input.fabricId, sql) else null)
            applyInventoryConsumption(calculated, sql)
            if (calculated.id == 0L) db.insertProduction(calculated, sql) else { db.updateProduction(calculated, sql); calculated.id }
        }
    }

    fun deleteProduction(id: Long) = db.transaction { sql ->
        db.getProductionById(id, sql)?.let { restoreInventoryConsumption(it, sql) }
        db.deleteProduction(id, sql)
    }

    fun resetAll() = db.clearAll()
    fun replaceAll(snapshot: SpectroSnapshot) = db.replaceAll(snapshot)

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

    private fun applyInventoryConsumption(record: ProductionRecord, sql: android.database.sqlite.SQLiteDatabase) {
        if (record.type != RecordType.PRODUCTION || record.fabricMode != FabricMode.INVENTORY || record.fabricId == null) return
        val item = db.getInventoryById(record.fabricId, sql) ?: throw IllegalArgumentException("Tessuto non trovato in magazzino.")
        val consume = max(0.0, record.fabricUsedMeters) + max(0.0, record.fabricWasteMeters)
        require(consume <= item.quantityMeters + .0001) { "Giacenza insufficiente: disponibili ${"%.2f".format(item.quantityMeters)} mt, richiesti ${"%.2f".format(consume)} mt." }
        db.setInventoryQuantity(item.id, item.quantityMeters - consume, sql)
    }

    private fun restoreInventoryConsumption(record: ProductionRecord, sql: android.database.sqlite.SQLiteDatabase) {
        if (record.type != RecordType.PRODUCTION || record.fabricMode != FabricMode.INVENTORY || record.fabricId == null) return
        val item = db.getInventoryById(record.fabricId, sql) ?: return
        db.setInventoryQuantity(item.id, item.quantityMeters + max(0.0, record.fabricUsedMeters) + max(0.0, record.fabricWasteMeters), sql)
    }

    private fun validDate(value: String) = runCatching { java.time.LocalDate.parse(value) }.isSuccess
}
