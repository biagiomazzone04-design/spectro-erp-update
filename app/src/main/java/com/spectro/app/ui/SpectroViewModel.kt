package com.spectro.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spectro.app.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class SpectroUiState(
    val loading: Boolean = true,
    val clients: List<Client> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val production: List<ProductionRecord> = emptyList(),
    val workOrders: List<WorkOrder> = emptyList(),
    val clientIssues: List<ClientIssue> = emptyList(),
    val issueUpdates: List<IssueUpdate> = emptyList(),
    val payments: List<PaymentDeadline> = emptyList(),
    val settings: AppSettings = AppSettings(),
    val message: String? = null
)

class SpectroViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SpectroRepository(app)
    var state by mutableStateOf(SpectroUiState())
        private set

    init { refresh() }

    fun refresh(message: String? = null) {
        viewModelScope.launch {
            state = state.copy(loading = true)
            val s = withContext(Dispatchers.IO) { repo.snapshot() }
            state = SpectroUiState(false, s.clients, s.inventory, s.production, s.workOrders, s.clientIssues, s.issueUpdates, s.payments, s.settings, message)
        }
    }
    fun consumeMessage() { state = state.copy(message = null) }

    private fun runWrite(success: String, block: () -> Unit) {
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                val s = withContext(Dispatchers.IO) { repo.snapshot() }
                state = state.copy(loading = false, clients = s.clients, inventory = s.inventory, production = s.production, workOrders = s.workOrders, clientIssues = s.clientIssues, issueUpdates = s.issueUpdates, payments = s.payments, settings = s.settings, message = success)
            } catch (t: Throwable) { state = state.copy(loading = false, message = t.message ?: "Operazione non riuscita") }
        }
    }

    fun saveClient(v: Client) = runWrite("Cliente salvato") { repo.saveClient(v) }
    fun deleteClient(id: Long) = runWrite("Cliente eliminato") { repo.deleteClient(id) }
    fun saveInventory(v: InventoryItem) = runWrite("Magazzino aggiornato") { repo.saveInventory(v) }
    fun deleteInventory(id: Long) = runWrite("Tessuto eliminato") { repo.deleteInventory(id) }
    fun saveProduction(v: ProductionRecord) = runWrite("Registrazione salvata") { repo.saveProduction(v) }
    fun deleteProduction(id: Long) = runWrite("Registrazione eliminata e magazzino riallineato") { repo.deleteProduction(id) }
    fun saveWorkOrder(v: WorkOrder) = runWrite("Ordine/lavorazione salvato") { repo.saveWorkOrder(v) }
    fun deleteWorkOrder(id: Long) = runWrite("Lavorazione eliminata") { repo.deleteWorkOrder(id) }
    fun setWorkStatus(id: Long, status: WorkStatus) = runWrite("Stato ordine aggiornato") { repo.setWorkStatus(id, status) }
    fun saveClientIssue(v: ClientIssue) = runWrite("Criticità salvata") { repo.saveClientIssue(v) }
    fun deleteClientIssue(id: Long) = runWrite("Criticità eliminata") { repo.deleteClientIssue(id) }
    fun addIssueUpdate(v: IssueUpdate) = runWrite("Aggiornamento aggiunto alla cronologia") { repo.addIssueUpdate(v) }
    fun deleteIssueUpdate(id: Long) = runWrite("Aggiornamento eliminato") { repo.deleteIssueUpdate(id) }
    fun savePayment(v: PaymentDeadline) = runWrite("Scadenza salvata") { repo.savePayment(v) }
    fun deletePayment(id: Long) = runWrite("Scadenza eliminata") { repo.deletePayment(id) }
    fun togglePayment(id: Long) = runWrite("Stato pagamento aggiornato") { repo.togglePayment(id) }
    fun saveSettings(v: AppSettings) = runWrite("Impostazioni salvate") { repo.saveSettings(v) }
    fun resetAll() = runWrite("Archivio azzerato") { repo.resetAll() }
    fun restore(s: SpectroSnapshot) = runWrite("Backup ripristinato") { repo.replaceAll(s) }

    fun snapshot() = SpectroSnapshot(state.clients, state.inventory, state.production, state.workOrders, state.settings, state.clientIssues, state.issueUpdates, state.payments)

    fun dashboardDays(query: String = ""): List<DashboardDay> {
        val filtered = state.production.filter { r -> query.isBlank() || r.clientName.contains(query, true) || r.orderRef.contains(query, true) || r.designs.any { it.contains(query, true) } }
        return filtered.groupBy { it.date }.map { (date, records) ->
            val revenue = records.sumOf { it.grossRevenue }; val materials = records.sumOf { it.materialCostTotal }; val fixed = if (records.any { it.applyFixedCosts }) state.settings.dailyFixedCost else 0.0
            DashboardDay(date, revenue, materials, fixed, revenue - materials - fixed, records)
        }.sortedByDescending { it.date }
    }

    fun topClients(limit: Int = 5): List<AnalyticsSlice> = state.production.filter { it.type == RecordType.PRODUCTION }.groupBy { it.clientName.ifBlank { "Senza cliente" } }.map { AnalyticsSlice(it.key, it.value.sumOf { r -> r.grossRevenue }) }.filter { it.value > 0 }.sortedByDescending { it.value }.take(limit)
    fun topFabrics(limit: Int = 5): List<AnalyticsSlice> = state.production.filter { it.type == RecordType.PRODUCTION && it.fabricDisplayName != "Nessuno" }.groupBy { it.fabricDisplayName.substringBefore(" (") }.map { AnalyticsSlice(it.key, it.value.sumOf { r -> r.fabricUsedMeters }) }.filter { it.value > 0 }.sortedByDescending { it.value }.take(limit)

    fun openIssues() = state.clientIssues.filter { it.status !in listOf(IssueStatus.RESOLVED, IssueStatus.CLOSED) }
    fun overduePayments(): List<PaymentDeadline> = state.payments.filter { !it.paid && runCatching { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false) }
    fun lowStock(): List<InventoryItem> = state.inventory.filter { it.minimumQuantityMeters > 0 && it.quantityMeters <= it.minimumQuantityMeters }
    fun overdueWork(): List<WorkOrder> = state.workOrders.filter { it.status !in listOf(WorkStatus.COMPLETED, WorkStatus.DELIVERED) && it.dueDate.isNotBlank() && runCatching { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false) }
}
