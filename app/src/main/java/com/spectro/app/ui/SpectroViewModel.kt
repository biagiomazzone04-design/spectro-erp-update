package com.spectro.app.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.spectro.app.auth.AppPermission
import com.spectro.app.auth.AuthState
import com.spectro.app.auth.SupabaseAuthRepository
import com.spectro.app.data.*
import com.spectro.app.sync.CloudSyncRepository
import com.spectro.app.sync.CloudSyncCoordinator
import com.spectro.app.sync.CloudSyncWork
import com.spectro.app.sync.SyncInspection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.YearMonth

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
    val fabricRolls: List<FabricRoll> = emptyList(),
    val stockMovements: List<StockMovement> = emptyList(),
    val rollUsages: List<RollUsage> = emptyList(),
    val timelineEvents: List<OrderTimelineEvent> = emptyList(),
    val cottonOrders: List<CottonOrder> = emptyList(),
    val zenthaProducts: List<ZenthaProduct> = emptyList(),
    val zenthaOrders: List<ZenthaOrder> = emptyList(),
    val zenthaItems: List<ZenthaOrderItem> = emptyList(),
    val zenthaExtraCosts: List<ZenthaExtraCost> = emptyList(),
    val zenthaPayments: List<ZenthaPayment> = emptyList(),
    val economicWorks: List<EconomicWork> = emptyList(),
    val operationalTasks: List<OperationalTask> = emptyList(),
    val message: String? = null
)

data class ZenthaTotals(
    val revenue: Double,
    val productCosts: Double,
    val extraCosts: Double,
    val profit: Double,
    val shareA: Double,
    val shareB: Double,
    val paid: Double,
    val outstanding: Double
)

data class CloudSyncUiState(
    val loading: Boolean = false,
    val inspection: SyncInspection? = null,
    val message: String? = null,
    val error: String? = null
)

class SpectroViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = SpectroRepository(app)
    private val authRepository = SupabaseAuthRepository(app)
    private val cloudSyncRepository = CloudSyncRepository(app, authRepository, repo)
    var state by mutableStateOf(SpectroUiState())
        private set
    var authState by mutableStateOf<AuthState>(AuthState.Loading)
        private set
    var authMessage by mutableStateOf<String?>(null)
        private set
    var cloudSyncState by mutableStateOf(CloudSyncUiState())
        private set
    private var automaticSyncJob: Job? = null

    init {
        viewModelScope.launch {
            authState = authRepository.restore()
            if (authState is AuthState.SignedIn) {
                refresh()
                resumeAutomaticSync()
            }
            else state = state.copy(loading = false)
        }
    }

    fun signIn(email: String, password: String) {
        viewModelScope.launch {
            authState = AuthState.Loading
            authMessage = null
            try {
                authState = authRepository.signIn(email, password)
                if (authState is AuthState.SignedIn) {
                    refresh()
                    resumeAutomaticSync()
                }
            } catch (t: Throwable) {
                authState = AuthState.SignedOut
                authMessage = t.message ?: "Accesso non riuscito."
            }
        }
    }

    fun signUp(email: String, password: String, displayName: String) {
        viewModelScope.launch {
            authMessage = runCatching { authRepository.signUp(email, password, displayName) }
                .getOrElse { it.message ?: "Registrazione non riuscita." }
        }
    }

    fun resendConfirmation(email: String) {
        viewModelScope.launch {
            authMessage = runCatching { authRepository.resendSignUpConfirmation(email) }
                .getOrElse { it.message ?: "Invio del nuovo link non riuscito." }
        }
    }

    fun retryAccess() {
        viewModelScope.launch {
            authState = AuthState.Loading
            authState = runCatching { authRepository.retryAccess() }
                .getOrElse { AuthState.AwaitingAccess("", it.message ?: "Impossibile verificare i permessi.") }
            if (authState is AuthState.SignedIn) {
                refresh()
                resumeAutomaticSync()
            }
        }
    }

    fun bootstrapOrganization() {
        viewModelScope.launch {
            val email = (authState as? AuthState.AwaitingAccess)?.email.orEmpty()
            authState = AuthState.Loading
            authMessage = null
            authState = runCatching { authRepository.bootstrapOrganization() }
                .getOrElse {
                    AuthState.AwaitingAccess(
                        email,
                        it.message ?: "Impossibile creare la prima azienda. Se esiste già, chiedi a un amministratore di assegnarti un ruolo."
                    )
                }
            if (authState is AuthState.SignedIn) {
                refresh("Azienda Spectro configurata. Sei l’amministratore iniziale.")
                resumeAutomaticSync()
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            automaticSyncJob?.cancel()
            authRepository.signOut()
            authState = AuthState.SignedOut
            authMessage = null
            cloudSyncState = CloudSyncUiState()
            state = SpectroUiState(loading = false)
        }
    }

    fun consumeAuthMessage() { authMessage = null }

    fun can(permission: AppPermission): Boolean =
        (authState as? AuthState.SignedIn)?.access?.can(permission) == true

    fun refresh(message: String? = null) {
        viewModelScope.launch {
            state = state.copy(loading = true)
            val s = withContext(Dispatchers.IO) { repo.snapshot() }
            state = s.toUiState(message)
        }
    }
    fun consumeMessage() { state = state.copy(message = null) }

    private fun runWrite(permission: AppPermission, success: String, block: () -> Unit) {
        if (!can(permission)) {
            state = state.copy(loading = false, message = "Il tuo ruolo non consente questa operazione.")
            return
        }
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val sync = repo.localSyncState()
                    repo.saveLocalSyncState(sync.copy(pendingChanges = true))
                    block()
                }
                val s = withContext(Dispatchers.IO) { repo.snapshot() }
                state = s.toUiState(success)
                CloudSyncWork.enqueue(getApplication())
                scheduleAutomaticSync()
            } catch (t: Throwable) { state = state.copy(loading = false, message = t.message ?: "Operazione non riuscita") }
        }
    }

    fun saveClient(v: Client) = runWrite(AppPermission.CLIENTS_MANAGE, "Cliente salvato") { repo.saveClient(v) }
    fun deleteClient(id: Long) = runWrite(AppPermission.CLIENTS_MANAGE, "Cliente eliminato") { repo.deleteClient(id) }

    fun saveInventory(v: InventoryItem) = runWrite(AppPermission.INVENTORY_MANAGE, "Magazzino aggiornato") { repo.saveInventory(v) }
    fun createInventoryWithRolls(v: InventoryItem, rolls: List<FabricRollInput>) = runWrite(AppPermission.INVENTORY_MANAGE, "Carico tessuto salvato") { repo.createInventoryWithRolls(v, rolls) }
    fun addRoll(inventoryId: Long, code: String, meters: Double, note: String = "", images: List<String> = emptyList()) = runWrite(AppPermission.INVENTORY_MANAGE, "Collo aggiunto") { repo.addRoll(inventoryId, code, meters, note, images) }
    fun adjustRoll(rollId: Long, newMeters: Double, note: String) = runWrite(AppPermission.INVENTORY_MANAGE, "Giacenza collo rettificata") { repo.adjustRoll(rollId, newMeters, note) }
    fun deleteInventory(id: Long) = runWrite(AppPermission.INVENTORY_MANAGE, "Tessuto eliminato") { repo.deleteInventory(id) }

    fun saveProduction(v: ProductionRecord, rollAllocations: Map<Long, Double> = emptyMap()) = runWrite(AppPermission.PRODUCTION_MANAGE, "Registrazione produttiva salvata") { repo.saveProduction(v, rollAllocations) }
    fun deleteProduction(id: Long) = runWrite(AppPermission.PRODUCTION_MANAGE, "Registrazione eliminata e magazzino riallineato") { repo.deleteProduction(id) }

    fun saveWorkOrder(v: WorkOrder) = runWrite(AppPermission.ORDERS_MANAGE, "Ordine salvato") { repo.saveWorkOrder(v) }
    fun deleteWorkOrder(id: Long) = runWrite(AppPermission.ORDERS_MANAGE, "Ordine eliminato") { repo.deleteWorkOrder(id) }
    fun setWorkStatus(id: Long, status: WorkStatus, note: String = "") = runWrite(AppPermission.ORDERS_MANAGE, "Stato ordine aggiornato") { repo.setWorkStatus(id, status, note) }
    fun completeWorkOrder(id: Long, note: String = "") = runWrite(AppPermission.ORDERS_MANAGE, "Ordine completato e archiviato nello storico") { repo.completeWorkOrder(id, note) }
    fun reopenWorkOrder(id: Long, note: String = "") = runWrite(AppPermission.ORDERS_MANAGE, "Ordine riaperto") { repo.reopenWorkOrder(id, note) }
    fun cancelWorkOrder(id: Long, reason: String) = runWrite(AppPermission.ORDERS_MANAGE, "Ordine annullato") { repo.cancelWorkOrder(id, reason) }

    fun saveTimelineEvent(v: OrderTimelineEvent) = runWrite(AppPermission.ISSUES_MANAGE, "Aggiornamento aggiunto alla timeline") { repo.saveTimelineEvent(v) }
    fun resolveTimelineEvent(id: Long, note: String) = runWrite(AppPermission.ISSUES_MANAGE, "Criticità risolta") { repo.resolveTimelineEvent(id, note) }
    fun deleteTimelineEvent(id: Long) = runWrite(AppPermission.ISSUES_MANAGE, "Voce timeline eliminata") { repo.deleteTimelineEvent(id) }

    fun saveClientIssue(v: ClientIssue) = runWrite(AppPermission.ISSUES_MANAGE, "Criticità legacy salvata") { repo.saveClientIssue(v) }
    fun deleteClientIssue(id: Long) = runWrite(AppPermission.ISSUES_MANAGE, "Criticità eliminata") { repo.deleteClientIssue(id) }
    fun addIssueUpdate(v: IssueUpdate) = runWrite(AppPermission.ISSUES_MANAGE, "Aggiornamento aggiunto alla cronologia") { repo.addIssueUpdate(v) }
    fun deleteIssueUpdate(id: Long) = runWrite(AppPermission.ISSUES_MANAGE, "Aggiornamento eliminato") { repo.deleteIssueUpdate(id) }

    fun savePayment(v: PaymentDeadline) = runWrite(AppPermission.FINANCE_MANAGE, "Scadenza salvata") { repo.savePayment(v) }
    fun deletePayment(id: Long) = runWrite(AppPermission.FINANCE_MANAGE, "Scadenza eliminata") { repo.deletePayment(id) }
    fun togglePayment(id: Long) = runWrite(AppPermission.FINANCE_MANAGE, "Stato pagamento aggiornato") { repo.togglePayment(id) }

    fun saveCottonOrder(v: CottonOrder, rollAllocations: Map<Long, Double> = emptyMap()) = runWrite(AppPermission.COTTON_MANAGE, "Ordine cotone salvato") { repo.saveCottonOrder(v, rollAllocations) }
    fun deleteCottonOrder(id: Long) = runWrite(AppPermission.COTTON_MANAGE, "Ordine cotone eliminato") { repo.deleteCottonOrder(id) }

    fun saveZenthaProduct(v: ZenthaProduct) = runWrite(AppPermission.ZENTHA_MANAGE, "Prodotto Zentha salvato") { repo.saveZenthaProduct(v) }
    fun deleteZenthaProduct(id: Long) = runWrite(AppPermission.ZENTHA_MANAGE, "Prodotto Zentha eliminato") { repo.deleteZenthaProduct(id) }
    fun saveZenthaOrder(order: ZenthaOrder, items: List<ZenthaOrderItem>, extraCosts: List<ZenthaExtraCost>) = runWrite(AppPermission.ZENTHA_MANAGE, "Ordine Zentha salvato") { repo.saveZenthaOrder(order, items, extraCosts) }
    fun deleteZenthaOrder(id: Long) = runWrite(AppPermission.ZENTHA_MANAGE, "Ordine Zentha eliminato") { repo.deleteZenthaOrder(id) }
    fun addZenthaPayment(v: ZenthaPayment) = runWrite(AppPermission.ZENTHA_MANAGE, "Pagamento Zentha registrato") { repo.addZenthaPayment(v) }
    fun deleteZenthaPayment(id: Long) = runWrite(AppPermission.ZENTHA_MANAGE, "Pagamento Zentha eliminato") { repo.deleteZenthaPayment(id) }

    fun saveEconomicWork(v: EconomicWork) = runWrite(AppPermission.FINANCE_MANAGE, "Lavoro economico salvato") { repo.saveEconomicWork(v) }
    fun deleteEconomicWork(id: Long) = runWrite(AppPermission.FINANCE_MANAGE, "Lavoro economico eliminato") { repo.deleteEconomicWork(id) }

    fun saveOperationalTask(v: OperationalTask) = runWrite(AppPermission.TASKS_MANAGE, "Attività salvata") { repo.saveOperationalTask(v) }
    fun deleteOperationalTask(id: Long) = runWrite(AppPermission.TASKS_MANAGE, "Attività eliminata") { repo.deleteOperationalTask(id) }

    fun saveSettings(v: AppSettings) = runWrite(AppPermission.SETTINGS_MANAGE, "Impostazioni salvate") { repo.saveSettings(v) }
    fun resetAll() = runWrite(AppPermission.BACKUP_MANAGE, "Archivio azzerato") { repo.resetAll() }
    fun restore(s: SpectroSnapshot) = runWrite(AppPermission.BACKUP_MANAGE, "Backup ripristinato e migrato") { repo.replaceAll(s) }

    fun inspectCloudWorkspace() = runCloudSync { access ->
        val inspection = cloudSyncRepository.inspect(access)
        CloudSyncUiState(
            inspection = inspection,
            message = if (inspection.remote.exists) "Workspace cloud verificato." else "Il cloud è vuoto: questa postazione può pubblicare la prima revisione."
        )
    }

    fun publishCloudWorkspace() = runCloudSync { access ->
        val inspection = cloudSyncRepository.inspect(access)
        if (!inspection.local.pendingChanges && inspection.remote.exists) {
            return@runCloudSync CloudSyncUiState(
                inspection = inspection,
                message = "Workspace già allineato alla revisione ${inspection.remote.revision}. Non è stata creata una revisione duplicata."
            )
        }
        val result = cloudSyncRepository.publish(access)
        CloudSyncUiState(
            inspection = result.inspection,
            message = "Revisione ${result.inspection.remote.revision} pubblicata. Backup locale di recupero: ${result.recoveryBackup.name}"
        )
    }

    fun downloadCloudWorkspace() = runCloudSync(refreshData = true) { access ->
        val result = cloudSyncRepository.download(access)
        CloudSyncUiState(
            inspection = result.inspection,
            message = "Revisione ${result.inspection.remote.revision} applicata. La copia precedente è in ${result.recoveryBackup.name}."
        )
    }

    fun clearCloudSyncMessage() {
        cloudSyncState = cloudSyncState.copy(message = null, error = null)
    }

    private fun runCloudSync(
        refreshData: Boolean = false,
        block: suspend (com.spectro.app.auth.UserAccess) -> CloudSyncUiState
    ) {
        if (!can(AppPermission.SYNC_USE)) {
            cloudSyncState = CloudSyncUiState(error = "Il tuo ruolo non consente la sincronizzazione cloud.")
            return
        }
        val access = (authState as? AuthState.SignedIn)?.access ?: return
        viewModelScope.launch {
            cloudSyncState = cloudSyncState.copy(loading = true, message = null, error = null)
            try {
                automaticSyncJob?.cancel()
                val result = CloudSyncCoordinator.transferMutex.withLock { withContext(Dispatchers.IO) { block(access) } }
                if (refreshData) {
                    val refreshed = withContext(Dispatchers.IO) { repo.snapshot() }
                    state = refreshed.toUiState("Workspace cloud scaricato in sicurezza")
                }
                cloudSyncState = result
            } catch (t: Throwable) {
                cloudSyncState = cloudSyncState.copy(
                    loading = false,
                    error = t.message ?: "Sincronizzazione cloud non riuscita."
                )
            } finally {
                if (cloudSyncState.loading) cloudSyncState = cloudSyncState.copy(loading = false)
            }
        }
    }

    private fun scheduleAutomaticSync(
        delayMillis: Long = 2_500,
        onlyWhenPending: Boolean = true
    ) {
        // If the current job is already transferring, it will inspect pendingChanges again
        // after publishing and immediately process the next batch.
        if (automaticSyncJob?.isActive == true && CloudSyncCoordinator.transferMutex.isLocked) return
        automaticSyncJob?.cancel()
        if (!can(AppPermission.SYNC_USE)) return
        val access = (authState as? AuthState.SignedIn)?.access ?: return
        automaticSyncJob = viewModelScope.launch {
            var nextDelay = delayMillis
            while (true) {
                delay(nextDelay)
                val outcome = CloudSyncCoordinator.transferMutex.withLock {
                    val pending = withContext(Dispatchers.IO) { repo.localSyncState().pendingChanges }
                    if (onlyWhenPending && !pending) return@withLock 0
                    cloudSyncState = cloudSyncState.copy(loading = true, message = null, error = null)
                    try {
                        val result = withContext(Dispatchers.IO) { cloudSyncRepository.publish(access) }
                        cloudSyncState = CloudSyncUiState(
                            inspection = result.inspection,
                            message = "Sincronizzazione automatica completata • revisione ${result.inspection.remote.revision}."
                        )
                        1
                    } catch (t: Throwable) {
                        cloudSyncState = cloudSyncState.copy(
                            loading = false,
                            error = "Modifiche salvate sul dispositivo; sincronizzazione automatica in attesa. ${t.message.orEmpty()}".trim()
                        )
                        -1
                    }
                }
                if (outcome == 0) return@launch
                if (outcome < 0) {
                    nextDelay = 30_000
                    continue
                }
                val stillPending = withContext(Dispatchers.IO) { repo.localSyncState().pendingChanges }
                if (!stillPending) return@launch
                nextDelay = 2_500
            }
        }
    }

    private suspend fun resumeAutomaticSync() {
        val access = (authState as? AuthState.SignedIn)?.access ?: return
        CloudSyncWork.enqueuePeriodic(getApplication())
        val pending = withContext(Dispatchers.IO) {
            cloudSyncRepository.prepareAttachmentMirrorUpgrade()
            cloudSyncRepository.prepareRecordMirrorUpgrade()
            val imported = cloudSyncRepository.importPendingWebZentha(access)
            if (imported > 0) {
                val refreshed = repo.snapshot()
                withContext(Dispatchers.Main) { state = refreshed.toUiState("$imported ordini Zentha creati dal web acquisiti.") }
            }
            repo.localSyncState().pendingChanges
        }
        if (!pending) return
        CloudSyncWork.enqueue(getApplication(), 1_500)
        scheduleAutomaticSync(1_500, onlyWhenPending = true)
    }

    fun snapshot() = SpectroSnapshot(
        clients = state.clients, inventory = state.inventory, production = state.production, workOrders = state.workOrders,
        settings = state.settings, clientIssues = state.clientIssues, issueUpdates = state.issueUpdates, payments = state.payments,
        fabricRolls = state.fabricRolls, stockMovements = state.stockMovements, rollUsages = state.rollUsages, timelineEvents = state.timelineEvents,
        cottonOrders = state.cottonOrders, zenthaProducts = state.zenthaProducts, zenthaOrders = state.zenthaOrders, zenthaItems = state.zenthaItems,
        zenthaExtraCosts = state.zenthaExtraCosts, zenthaPayments = state.zenthaPayments,
        economicWorks = state.economicWorks, operationalTasks = state.operationalTasks
    )

    fun rollsForInventory(inventoryId: Long): List<FabricRoll> = state.fabricRolls.filter { it.inventoryId == inventoryId }.sortedBy { it.id }
    fun activeInventory(): List<InventoryItem> = state.inventory.filter { it.quantityMeters > .0001 }
    fun inventoryHistory(): List<InventoryItem> = state.inventory.sortedWith(compareByDescending<InventoryItem> { it.createdDate }.thenByDescending { it.id })
    fun movementsForRoll(rollId: Long): List<StockMovement> = state.stockMovements.filter { it.rollId == rollId }.sortedByDescending { it.dateTime }
    fun timelineForOrder(workOrderId: Long): List<OrderTimelineEvent> = state.timelineEvents.filter { it.workOrderId == workOrderId }.sortedWith(compareBy<OrderTimelineEvent> { it.date }.thenBy { it.time }.thenBy { it.id })
    fun productionsForOrder(workOrderId: Long): List<ProductionRecord> = state.production
        .filter { it.workOrderId == workOrderId && it.type == RecordType.PRODUCTION }
        .sortedWith(compareBy<ProductionRecord> { it.date }.thenBy { it.id })
    fun productionsForZenthaOrder(zenthaOrderId: Long): List<ProductionRecord> = state.production
        .filter { it.zenthaOrderId == zenthaOrderId && it.type == RecordType.PRODUCTION }
        .sortedWith(compareBy<ProductionRecord> { it.date }.thenBy { it.id })
    fun zenthaPaperMeters(zenthaOrderId: Long): Double = productionsForZenthaOrder(zenthaOrderId).sumOf { it.printedPaperMeters }
    fun inProcessOrders(): List<WorkOrder> = state.workOrders.filter { it.status in listOf(WorkStatus.RECEIVED, WorkStatus.IN_PROCESS, WorkStatus.WAITING_CLIENT, WorkStatus.WAITING_MATERIAL, WorkStatus.BLOCKED, WorkStatus.ON_HOLD) && productionsForOrder(it.id).isEmpty() }

    fun zenthaTotals(orderId: Long): ZenthaTotals {
        val order = state.zenthaOrders.firstOrNull { it.id == orderId }
        val items = state.zenthaItems.filter { it.orderId == orderId }
        val extras = state.zenthaExtraCosts.filter { it.orderId == orderId }
        val pays = state.zenthaPayments.filter { it.orderId == orderId }
        val revenue = items.sumOf { it.revenue }
        val productCosts = items.sumOf { it.cost }
        val extraCosts = extras.sumOf { it.amount }
        val profit = revenue - productCosts - extraCosts
        val splitA = order?.splitPercentA ?: state.settings.zenthaSplitA
        val splitB = order?.splitPercentB ?: state.settings.zenthaSplitB
        val paid = pays.sumOf { it.amount }
        return ZenthaTotals(revenue, productCosts, extraCosts, profit, profit * splitA / 100.0, profit * splitB / 100.0, paid, (revenue - paid).coerceAtLeast(0.0))
    }

    fun dashboardDays(query: String = ""): List<DashboardDay> {
        val filtered = state.production.filter { r -> query.isBlank() || r.clientName.contains(query, true) || r.orderRef.contains(query, true) || r.designs.any { it.contains(query, true) } }
        return filtered.groupBy { it.date }.map { (date, records) ->
            val revenue = records.sumOf { it.grossRevenue }; val materials = records.sumOf { it.materialCostTotal }; val fixed = if (records.any { it.applyFixedCosts }) state.settings.dailyFixedCost else 0.0
            DashboardDay(date, revenue, materials, fixed, revenue - materials - fixed, records)
        }.sortedByDescending { it.date }
    }

    fun topClients(limit: Int = 5): List<AnalyticsSlice> = state.production.filter { it.type == RecordType.PRODUCTION }.groupBy { it.clientName.ifBlank { "Senza cliente" } }.map { AnalyticsSlice(it.key, it.value.sumOf { r -> r.grossRevenue }) }.filter { it.value > 0 }.sortedByDescending { it.value }.take(limit)
    fun topFabrics(limit: Int = 5): List<AnalyticsSlice> = state.production.filter { it.type == RecordType.PRODUCTION && it.fabricDisplayName != "Nessuno" }.groupBy { it.fabricDisplayName.substringBefore(" (") }.map { AnalyticsSlice(it.key, it.value.sumOf { r -> r.fabricUsedMeters }) }.filter { it.value > 0 }.sortedByDescending { it.value }.take(limit)

    fun monthlyStats(month: YearMonth = YearMonth.now()): MonthlyStats {
        val prefix = month.toString()
        val productions = state.production.filter { it.date.startsWith(prefix) }
        val productive = productions.filter { it.type == RecordType.PRODUCTION }
        val cotton = state.cottonOrders.filter { it.orderDate.startsWith(prefix) }
        val economic = state.economicWorks.filter { it.date.startsWith(prefix) && it.status != EconomicWorkStatus.CANCELLED }
        val criticalEvents = state.timelineEvents.filter { it.date.startsWith(prefix) && it.severity != TimelineSeverity.INFO }
        val legacyIssues = state.clientIssues.filter { it.openedDate.startsWith(prefix) }
        val completedOrders = state.workOrders.filter { it.status.isArchivedOrder() && it.effectiveDeliveryDate.startsWith(prefix) }

        val productionRevenue = productive.sumOf { it.grossRevenue }
        val cottonRevenue = cotton.sumOf { it.grossRevenue }
        val otherRevenue = economic.sumOf { it.grossRevenue }
        val materialCosts = productions.sumOf { it.materialCostTotal }
        val cottonCosts = cotton.sumOf { it.totalCost }
        val otherCosts = economic.sumOf { it.totalCosts }
        val fixedDays = productions.filter { it.applyFixedCosts }.map { it.date }.distinct().size
        val fixedCosts = fixedDays * state.settings.dailyFixedCost
        val totalRevenue = productionRevenue + cottonRevenue + otherRevenue
        val totalCosts = materialCosts + cottonCosts + otherCosts + fixedCosts
        val netProfit = totalRevenue - totalCosts
        val paperMeters = productive.sumOf { it.printedPaperMeters }
        val cottonMeters = cotton.sumOf { if (it.actualProducedMeters > 0) it.actualProducedMeters else it.orderedMeters }

        val names = (
            productive.map { it.clientName.ifBlank { "Senza cliente" } } +
            cotton.map { it.clientName.ifBlank { "Senza cliente" } } +
            economic.map { it.clientName.ifBlank { "Senza cliente" } } +
            criticalEvents.map { it.clientName.ifBlank { "Senza cliente" } } +
            legacyIssues.map { it.clientName.ifBlank { "Senza cliente" } }
        ).distinct()

        val clients = names.map { name ->
            val prods = productive.filter { it.clientName.equals(name, true) }
            val cottonRows = cotton.filter { it.clientName.equals(name, true) }
            val economicRows = economic.filter { it.clientName.equals(name, true) }
            val crits = criticalEvents.count { it.clientName.equals(name, true) } + legacyIssues.count { it.clientName.equals(name, true) }
            ClientMonthlyStat(
                clientName = name,
                revenue = prods.sumOf { it.grossRevenue } + cottonRows.sumOf { it.grossRevenue } + economicRows.sumOf { it.grossRevenue },
                paperMeters = prods.sumOf { it.printedPaperMeters },
                cottonMeters = cottonRows.sumOf { if (it.actualProducedMeters > 0) it.actualProducedMeters else it.orderedMeters },
                profit = prods.sumOf { it.grossProfit } + cottonRows.sumOf { it.margin } + economicRows.sumOf { it.netProfit },
                records = prods.size + cottonRows.size + economicRows.size,
                criticalities = crits,
                otherRevenue = economicRows.sumOf { it.grossRevenue },
                completedOrders = completedOrders.count { it.client.equals(name, true) }
            )
        }.sortedByDescending { it.revenue }

        return MonthlyStats(
            month = prefix,
            productionRevenue = productionRevenue,
            cottonRevenue = cottonRevenue,
            materialCosts = materialCosts,
            cottonCosts = cottonCosts,
            fixedCosts = fixedCosts,
            totalRevenue = totalRevenue,
            totalCosts = totalCosts,
            netProfit = netProfit,
            printedPaperMeters = paperMeters,
            cottonMeters = cottonMeters,
            productionCount = productive.size,
            cottonOrderCount = cotton.size,
            clients = clients,
            otherRevenue = otherRevenue,
            otherCosts = otherCosts,
            otherNetProfit = otherRevenue - otherCosts,
            economicWorkCount = economic.size,
            criticalityCount = criticalEvents.size + legacyIssues.size,
            completedOrderCount = completedOrders.size
        )
    }

    fun monthlyRevenueByClient(month: YearMonth = YearMonth.now(), limit: Int = 8): List<AnalyticsSlice> =
        monthlyStats(month).clients.map { AnalyticsSlice(it.clientName, it.revenue) }.filter { it.value > 0 }.take(limit)

    fun monthlyPaperMetersByClient(month: YearMonth = YearMonth.now(), limit: Int = 8): List<AnalyticsSlice> =
        monthlyStats(month).clients.map { AnalyticsSlice(it.clientName, it.paperMeters) }.filter { it.value > 0 }.sortedByDescending { it.value }.take(limit)

    fun openTasks(): List<OperationalTask> = state.operationalTasks.filter { it.status !in listOf(OperationalTaskStatus.DONE, OperationalTaskStatus.CANCELLED) }
    fun urgentTasks(): List<OperationalTask> = openTasks().filter { task ->
        task.priority == Priority.HIGH || (task.dueDate.isNotBlank() && runCatching { !LocalDate.parse(task.dueDate).isAfter(LocalDate.now()) }.getOrDefault(false))
    }
    fun openIssues(): List<OrderTimelineEvent> = state.timelineEvents.filter { it.status == TimelineStatus.OPEN && it.severity != TimelineSeverity.INFO }
    fun overduePayments(): List<PaymentDeadline> = state.payments.filter { !it.paid && runCatching { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false) }
    fun lowStock(): List<InventoryItem> = state.inventory.filter { it.quantityMeters > .0001 && it.minimumQuantityMeters > 0 && it.quantityMeters <= it.minimumQuantityMeters }
    fun overdueWork(): List<WorkOrder> = state.workOrders.filter { it.status !in listOf(WorkStatus.COMPLETED, WorkStatus.DELIVERED, WorkStatus.CLOSED, WorkStatus.CANCELLED) && it.dueDate.isNotBlank() && runCatching { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false) }

    private fun SpectroSnapshot.toUiState(message: String?) = SpectroUiState(
        loading = false, clients = clients, inventory = inventory, production = production, workOrders = workOrders,
        clientIssues = clientIssues, issueUpdates = issueUpdates, payments = payments, settings = settings,
        fabricRolls = fabricRolls, stockMovements = stockMovements, rollUsages = rollUsages, timelineEvents = timelineEvents,
        cottonOrders = cottonOrders, zenthaProducts = zenthaProducts, zenthaOrders = zenthaOrders, zenthaItems = zenthaItems,
        zenthaExtraCosts = zenthaExtraCosts, zenthaPayments = zenthaPayments, economicWorks = economicWorks,
        operationalTasks = operationalTasks, message = message
    )
}
