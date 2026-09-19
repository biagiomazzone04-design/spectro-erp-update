package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.AttachmentStore
import com.spectro.app.util.DraftStore
import com.spectro.app.util.ProductionDraft
import com.spectro.app.util.WorkDraft
import java.time.LocalDate

enum class CreateMode { PRODUCTION, WORK, NEGATIVE }

@Composable
fun CreateScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    initialMode: CreateMode = CreateMode.PRODUCTION,
    editProduction: ProductionRecord? = null,
    editWork: WorkOrder? = null,
    sourceWorkOrder: WorkOrder? = null,
    onDone: (CreateMode) -> Unit
) {
    val context = LocalContext.current
    val recoveredMode = remember(editProduction?.id, editWork?.id, sourceWorkOrder?.id, initialMode) {
        if (editProduction != null || editWork != null || sourceWorkOrder != null) null
        else DraftStore.lastCreateMode(context)?.let { runCatching { CreateMode.valueOf(it) }.getOrNull() }?.takeIf { recovered ->
            when (recovered) {
                CreateMode.PRODUCTION -> DraftStore.hasProduction(context)
                CreateMode.WORK -> DraftStore.hasWork(context)
                CreateMode.NEGATIVE -> false
            }
        }
    }
    var mode by remember(editProduction?.id, editWork?.id, sourceWorkOrder?.id, initialMode) {
        mutableStateOf(if (sourceWorkOrder != null) CreateMode.PRODUCTION else recoveredMode ?: initialMode)
    }
    LaunchedEffect(mode, editProduction?.id, editWork?.id, sourceWorkOrder?.id) {
        if (editProduction == null && editWork == null && sourceWorkOrder == null && mode != CreateMode.NEGATIVE) {
            DraftStore.setLastCreateMode(context, mode.name)
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 130.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text(if (editProduction != null || editWork != null) "Modifica registrazione" else "Nuova registrazione", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Ordini, produzione e registrazioni negative con controlli automatici.", color = p.textMuted)
        }
        item {
            SegmentedChoice(listOf("PRODUCTION" to "PRODUTTIVO", "WORK" to "ORDINE", "NEGATIVE" to "NEGATIVO"), mode.name, p) { if (editProduction == null && editWork == null && sourceWorkOrder == null) mode = CreateMode.valueOf(it) }
            if (editProduction == null && editWork == null && mode != CreateMode.NEGATIVE) {
                Spacer(Modifier.height(8.dp))
                Text("Bozza automatica attiva • puoi uscire dall'app e riprendere senza perdere i campi compilati.", color = p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        item {
            AnimatedContent(mode, label = "createMode") { current ->
                when (current) {
                    CreateMode.PRODUCTION -> ProductionForm(vm, p, editProduction?.takeIf { it.type == RecordType.PRODUCTION }, sourceWorkOrder) { onDone(CreateMode.PRODUCTION) }
                    CreateMode.NEGATIVE -> NegativeForm(vm, p, editProduction?.takeIf { it.type == RecordType.NEGATIVE }) { onDone(CreateMode.NEGATIVE) }
                    CreateMode.WORK -> WorkForm(vm, p, editWork) { onDone(CreateMode.WORK) }
                }
            }
        }
    }
}

@Composable
private fun ProductionForm(vm: SpectroViewModel, p: SpectroPalette, editing: ProductionRecord?, source: WorkOrder?, onDone: () -> Unit) {
    val context = LocalContext.current
    val initialSource = source ?: editing?.workOrderId?.let { id -> vm.state.workOrders.firstOrNull { it.id == id } }
    val savedDraft = remember(editing?.id, source?.id) {
        if (editing == null && source == null) DraftStore.loadProduction(context) else null
    }
    var step by remember(editing?.id, source?.id) { mutableIntStateOf(savedDraft?.step ?: 1) }
    var workOrderId by remember(editing?.id, source?.id) { mutableStateOf(initialSource?.id ?: editing?.workOrderId ?: savedDraft?.workOrderId) }
    var date by remember(editing?.id, source?.id) { mutableStateOf(editing?.date ?: savedDraft?.date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var clientId by remember(editing?.id, source?.id) { mutableStateOf(initialSource?.clientId ?: editing?.clientId ?: savedDraft?.clientId) }
    var clientName by remember(editing?.id, source?.id) { mutableStateOf(initialSource?.client ?: editing?.clientName.orEmpty().ifBlank { savedDraft?.clientName.orEmpty() }) }
    var orderRef by remember(editing?.id, source?.id) { mutableStateOf(initialSource?.let { it.title.ifBlank { it.orderCode } } ?: editing?.orderRef.orEmpty().ifBlank { savedDraft?.orderRef.orEmpty() }) }
    var designs by remember(editing?.id, source?.id) { mutableStateOf(initialSource?.designCode ?: editing?.designs?.joinToString(", ").orEmpty().ifBlank { savedDraft?.designs.orEmpty() }) }
    var printPrice by remember(editing?.id, source?.id) { mutableStateOf((initialSource?.printPricePerMeter ?: editing?.printPricePerMeter ?: 0.0).takeIf { it > 0 }?.toString() ?: savedDraft?.printPrice.orEmpty()) }
    var plotter by remember(editing?.id, source?.id) { mutableStateOf(editing?.plotter ?: savedDraft?.plotter ?: "X1") }
    var paper by remember(editing?.id, source?.id) { mutableStateOf(editing?.printedPaperMeters?.takeIf { it > 0 }?.toString() ?: savedDraft?.paper.orEmpty()) }
    var protection by remember(editing?.id, source?.id) { mutableStateOf(editing?.protectionPaperMeters?.takeIf { it > 0 }?.toString() ?: savedDraft?.protection.orEmpty()) }
    var paperWaste by remember(editing?.id, source?.id) { mutableStateOf(editing?.paperWasteMeters?.takeIf { it > 0 }?.toString() ?: savedDraft?.paperWaste.orEmpty()) }
    var fabricMode by remember(editing?.id, source?.id) { mutableStateOf(if (initialSource?.fabricInventoryId != null) FabricMode.INVENTORY else editing?.fabricMode ?: savedDraft?.fabricMode?.let { runCatching { FabricMode.valueOf(it) }.getOrNull() } ?: FabricMode.NONE) }
    var fabricId by remember(editing?.id, source?.id) { mutableStateOf(initialSource?.fabricInventoryId ?: editing?.fabricId ?: savedDraft?.fabricId) }
    var fabricUsed by remember(editing?.id, source?.id) { mutableStateOf((editing?.fabricUsedMeters ?: initialSource?.orderedMeters ?: 0.0).takeIf { it > 0 }?.toString() ?: savedDraft?.fabricUsed.orEmpty()) }
    var fabricWaste by remember(editing?.id, source?.id) { mutableStateOf(editing?.fabricWasteMeters?.takeIf { it > 0 }?.toString() ?: savedDraft?.fabricWaste.orEmpty()) }
    var salePrice by remember(editing?.id, source?.id) { mutableStateOf(editing?.fabricSalePricePerMeter?.takeIf { it > 0 }?.toString() ?: savedDraft?.salePrice.orEmpty()) }
    var manualFabric by remember(editing?.id, source?.id) { mutableStateOf(editing?.manualFabricName.orEmpty().ifBlank { savedDraft?.manualFabric.orEmpty() }) }
    var notes by remember(editing?.id, source?.id) { mutableStateOf(editing?.technicalNotes.orEmpty().ifBlank { savedDraft?.notes.orEmpty() }) }
    var images by remember(editing?.id, source?.id) { mutableStateOf(editing?.images ?: savedDraft?.images ?: emptyList()) }
    var sourceDialog by remember { mutableStateOf(false) }
    var clientDialog by remember { mutableStateOf(false) }
    var fabricDialog by remember { mutableStateOf(false) }
    var rollDialog by remember { mutableStateOf(false) }
    var allocations by remember(editing?.id, source?.id) { mutableStateOf(if (editing != null) vm.state.rollUsages.filter { it.orderKind == OrderKind.PRODUCTION && it.orderId == editing.id }.associate { it.rollId to it.meters } else savedDraft?.allocations ?: emptyMap()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }
    val consumeMeters = fabricUsed.toDoubleSafe() + fabricWaste.toDoubleSafe()

    LaunchedEffect(step, workOrderId, date, clientId, clientName, orderRef, designs, printPrice, plotter, paper, protection, paperWaste, fabricMode, fabricId, fabricUsed, fabricWaste, salePrice, manualFabric, notes, images, allocations) {
        if (editing == null) {
            DraftStore.setLastCreateMode(context, CreateMode.PRODUCTION.name)
            DraftStore.saveProduction(
                context,
                ProductionDraft(
                    step = step, workOrderId = workOrderId, date = date, clientId = clientId, clientName = clientName,
                    orderRef = orderRef, designs = designs, printPrice = printPrice, plotter = plotter,
                    paper = paper, protection = protection, paperWaste = paperWaste, fabricMode = fabricMode.name,
                    fabricId = fabricId, fabricUsed = fabricUsed, fabricWaste = fabricWaste, salePrice = salePrice,
                    manualFabric = manualFabric, notes = notes, images = images, allocations = allocations
                )
            )
        }
    }

    fun applySource(w: WorkOrder?) {
        workOrderId = w?.id
        if (w != null) {
            clientId = w.clientId; clientName = w.client; orderRef = w.title.ifBlank { w.orderCode }; designs = w.designCode; printPrice = w.printPricePerMeter.takeIf { it > 0 }?.toString().orEmpty()
            if (w.fabricInventoryId != null) { fabricMode = FabricMode.INVENTORY; fabricId = w.fabricInventoryId }
            if (w.orderedMeters > 0 && fabricUsed.isBlank()) fabricUsed = w.orderedMeters.toString()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { repeat(3) { i -> LinearProgressIndicator(progress = { if (step >= i + 1) 1f else 0f }, modifier = Modifier.weight(1f).height(5.dp), color = p.primary, trackColor = p.border) } }
        when (step) {
            1 -> SpectroCard(p = p) {
                SectionTitle("Origine ordine")
                OutlinedButton(onClick = { sourceDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Link, null); Spacer(Modifier.width(6.dp)); Text(workOrderId?.let { id -> vm.state.workOrders.firstOrNull { it.id == id }?.let { "${it.title.ifBlank { it.orderCode }} • ${it.client}" } } ?: "Collega un ordine in processo (facoltativo)") }
                if (workOrderId != null) { Spacer(Modifier.height(6.dp)); Text("Quando salvi, l'ordine viene trasferito automaticamente in produzione mantenendo lo stesso ID e lo storico.", color = p.success, fontSize = 11.sp) }
                Spacer(Modifier.height(12.dp)); WheelDateField("Data produzione", date, { date = it })
                Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { clientDialog = true }, Modifier.fillMaxWidth()) { Text(clientName.ifBlank { "Scegli cliente" }) }
                Spacer(Modifier.height(10.dp)); SpectroTextField("Cliente manuale", clientName, { clientName = it; clientId = null })
                Spacer(Modifier.height(12.dp)); SpectroTextField("Rif. ordine", orderRef, { orderRef = it })
                Spacer(Modifier.height(12.dp)); SpectroTextField("Codici / disegni", designs, { designs = it })
                Spacer(Modifier.height(12.dp)); SpectroTextField("Prezzo stampa €/mt", printPrice, { printPrice = it }, numeric = true)
                Spacer(Modifier.height(12.dp)); SegmentedChoice(listOf("X1" to "X1", "X2" to "X2"), plotter, p) { plotter = it }
            }
            2 -> SpectroCard(p = p) {
                SpectroTextField("Carta stampata (mt)", paper, { paper = it; if (protection.isBlank()) protection = it }, numeric = true)
                Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SpectroTextField("Protezione mt", protection, { protection = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Sfrido carta mt", paperWaste, { paperWaste = it }, Modifier.weight(1f), numeric = true) }
                Spacer(Modifier.height(14.dp)); SegmentedChoice(listOf("NONE" to "NESSUNO", "INVENTORY" to "MAGAZZINO", "MANUAL" to "MANUALE"), fabricMode.name, p) { fabricMode = FabricMode.valueOf(it); allocations = emptyMap() }
                if (fabricMode == FabricMode.INVENTORY) {
                    Spacer(Modifier.height(12.dp)); val selected = vm.state.inventory.firstOrNull { it.id == fabricId }
                    OutlinedButton(onClick = { fabricDialog = true }, Modifier.fillMaxWidth()) { Text(selected?.let { "${it.ownerName} • ${it.fabricName} • ${meters(it.quantityMeters)}" } ?: "Seleziona tessuto") }
                    Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SpectroTextField("Metri usati", fabricUsed, { fabricUsed = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Sfrido mt", fabricWaste, { fabricWaste = it }, Modifier.weight(1f), numeric = true) }
                    if (selected?.ownerType == OwnerType.HOUSE) { Spacer(Modifier.height(12.dp)); SpectroTextField("Prezzo vendita tessuto €/mt", salePrice, { salePrice = it }, numeric = true) }
                    Spacer(Modifier.height(12.dp)); OutlinedButton(enabled = fabricId != null && consumeMeters > 0, onClick = { rollDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Inventory2, null); Spacer(Modifier.width(6.dp)); Text(if (allocations.isEmpty()) "Scegli colli utilizzati" else "${allocations.size} colli • ${meters(allocations.values.sum())}") }
                }
                if (fabricMode == FabricMode.MANUAL) { Spacer(Modifier.height(12.dp)); SpectroTextField("Nome tessuto fornito", manualFabric, { manualFabric = it }); Spacer(Modifier.height(12.dp)); SpectroTextField("Metri lavorati", fabricUsed, { fabricUsed = it }, numeric = true) }
            }
            else -> SpectroCard(p = p) {
                Text("Riepilogo", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp)); Metric("Cliente", clientName.ifBlank { "Non specificato" })
                Spacer(Modifier.height(10.dp)); Metric("Carta", meters(paper.toDoubleSafe()))
                Spacer(Modifier.height(10.dp)); Metric("Tessuto", when (fabricMode) { FabricMode.INVENTORY -> vm.state.inventory.firstOrNull { it.id == fabricId }?.fabricName ?: "Non selezionato"; FabricMode.MANUAL -> manualFabric; else -> "Nessuno" })
                if (allocations.isNotEmpty()) { Spacer(Modifier.height(8.dp)); Text("Colli: ${allocations.entries.joinToString { (id, m) -> "${vm.state.fabricRolls.firstOrNull { it.id == id }?.rollCode ?: id}: ${meters(m)}" }}", color = p.textMuted, fontSize = 11.sp) }
                Spacer(Modifier.height(14.dp)); SpectroTextField("Note tecniche", notes, { notes = it }, singleLine = false)
                Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega foto") }
                if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 1) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Indietro") }
            if (step < 3) Button(onClick = { step++ }, modifier = Modifier.weight(1f)) { Text("Continua") }
            else Button(onClick = {
                vm.saveProduction(ProductionRecord(id = editing?.id ?: 0L, type = RecordType.PRODUCTION, date = date, clientId = clientId, clientName = clientName.trim(), orderRef = orderRef.trim(), designs = designs.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() }, printPricePerMeter = printPrice.toDoubleSafe(), plotter = plotter, printedPaperMeters = paper.toDoubleSafe(), protectionPaperMeters = protection.toDoubleSafe(), paperWasteMeters = paperWaste.toDoubleSafe(), fabricMode = fabricMode, fabricId = fabricId, fabricUsedMeters = fabricUsed.toDoubleSafe(), fabricWasteMeters = fabricWaste.toDoubleSafe(), fabricSalePricePerMeter = salePrice.toDoubleSafe(), manualFabricName = manualFabric.trim(), technicalNotes = notes.trim(), images = images, applyFixedCosts = true, workOrderId = workOrderId), allocations)
                if (editing == null) DraftStore.clearProduction(context)
                onDone()
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("Salva") }
        }
    }

    if (sourceDialog) SelectionDialog("Ordini in processo", vm.inProcessOrders().map { "${it.title.ifBlank { it.orderCode }} • ${it.client} • ${meters(it.orderedMeters)}" to it.id }, onDismiss = { sourceDialog = false }) { id -> sourceDialog = false; applySource(vm.state.workOrders.firstOrNull { it.id == id }); allocations = emptyMap() }
    if (clientDialog) SelectionDialog("Scegli cliente", vm.state.clients.map { it.name to it.id }, onDismiss = { clientDialog = false }) { id -> clientDialog = false; vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; clientName = it.name; printPrice = it.defaultPrintPrice.takeIf { v -> v > 0 }?.toString().orEmpty() } }
    if (fabricDialog) SelectionDialog("Scegli tessuto", vm.state.inventory.map { "${it.ownerName} • ${it.fabricName} (${meters(it.quantityMeters)})" to it.id }, onDismiss = { fabricDialog = false }) { id -> fabricDialog = false; fabricId = id; allocations = emptyMap() }
    if (rollDialog && fabricId != null) {
        val old = allocations
        val rolls = vm.rollsForInventory(fabricId!!).map { r -> r.copy(availableMeters = r.availableMeters + (old[r.id] ?: 0.0)) }
        RollAllocationDialog(p, rolls, consumeMeters, old, onDismiss = { rollDialog = false }) { allocations = it; rollDialog = false }
    }
}

@Composable
private fun WorkForm(vm: SpectroViewModel, p: SpectroPalette, editing: WorkOrder?, onDone: () -> Unit) {
    val context = LocalContext.current
    val savedDraft = remember(editing?.id) { if (editing == null) DraftStore.loadWork(context) else null }
    var date by remember(editing?.id) { mutableStateOf(editing?.receivedDate ?: savedDraft?.date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var clientId by remember(editing?.id) { mutableStateOf(editing?.clientId ?: savedDraft?.clientId) }
    var client by remember(editing?.id) { mutableStateOf(editing?.client.orEmpty().ifBlank { savedDraft?.client.orEmpty() }) }
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty().ifBlank { savedDraft?.title.orEmpty() }) }
    var due by remember(editing?.id) { mutableStateOf(editing?.dueDate.orEmpty().ifBlank { savedDraft?.due.orEmpty() }) }
    var priority by remember(editing?.id) { mutableStateOf(editing?.priority ?: savedDraft?.priority?.let { runCatching { Priority.valueOf(it) }.getOrNull() } ?: Priority.NORMAL) }
    var blocker by remember(editing?.id) { mutableStateOf(editing?.blocker ?: savedDraft?.blocker?.let { runCatching { Blocker.valueOf(it) }.getOrNull() } ?: Blocker.NONE) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: savedDraft?.status?.let { runCatching { WorkStatus.valueOf(it) }.getOrNull() } ?: WorkStatus.IN_PROCESS) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty().ifBlank { savedDraft?.description.orEmpty() }) }
    var images by remember(editing?.id) { mutableStateOf(editing?.images ?: savedDraft?.images ?: emptyList()) }
    var orderedMeters by remember(editing?.id) { mutableStateOf(editing?.orderedMeters?.takeIf { it > 0 }?.toString() ?: savedDraft?.orderedMeters.orEmpty()) }
    var printPrice by remember(editing?.id) { mutableStateOf(editing?.printPricePerMeter?.takeIf { it > 0 }?.toString() ?: savedDraft?.printPrice.orEmpty()) }
    var designCode by remember(editing?.id) { mutableStateOf(editing?.designCode.orEmpty().ifBlank { savedDraft?.designCode.orEmpty() }) }
    var fabricId by remember(editing?.id) { mutableStateOf(editing?.fabricInventoryId ?: savedDraft?.fabricId) }
    var clientDialog by remember { mutableStateOf(false) }
    var fabricDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }

    LaunchedEffect(date, clientId, client, title, due, priority, blocker, status, description, images, orderedMeters, printPrice, designCode, fabricId) {
        if (editing == null) {
            DraftStore.setLastCreateMode(context, CreateMode.WORK.name)
            DraftStore.saveWork(
                context,
                WorkDraft(
                    date = date, clientId = clientId, client = client, title = title, due = due,
                    priority = priority.name, blocker = blocker.name, status = status.name, description = description,
                    images = images, orderedMeters = orderedMeters, printPrice = printPrice, designCode = designCode, fabricId = fabricId
                )
            )
        }
    }

    SpectroCard(p = p) {
        if (editing?.orderCode?.isNotBlank() == true) { Text(editing.orderCode, fontWeight = FontWeight.Black, color = p.primary); Spacer(Modifier.height(10.dp)) }
        WheelDateField("Data ordine", date, { date = it })
        Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { clientDialog = true }, Modifier.fillMaxWidth()) { Text(client.ifBlank { "Scegli cliente" }) }
        Spacer(Modifier.height(8.dp)); SpectroTextField("Cliente manuale", client, { client = it; clientId = null })
        Spacer(Modifier.height(12.dp)); SpectroTextField("Titolo ordine", title, { title = it }, placeholder = "Es. Divise Sunrise")
        Spacer(Modifier.height(12.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Metri ordine", orderedMeters, { orderedMeters = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Prezzo €/mt", printPrice, { printPrice = it }, Modifier.weight(1f), numeric = true) }
        Spacer(Modifier.height(12.dp)); SpectroTextField("Codice disegno", designCode, { designCode = it })
        Spacer(Modifier.height(12.dp)); val selectedFabric = vm.state.inventory.firstOrNull { it.id == fabricId }; OutlinedButton(onClick = { fabricDialog = true }, Modifier.fillMaxWidth()) { Text(selectedFabric?.let { "${it.ownerName} • ${it.fabricName} • ${meters(it.quantityMeters)}" } ?: "Tessuto da magazzino (facoltativo)") }
        Spacer(Modifier.height(12.dp)); WheelDateField("Data consegna", due, { due = it }, allowEmpty = true)
        Spacer(Modifier.height(12.dp)); SimpleSelectField("Stato ordine", workStatusLabel(status), WorkStatus.entries.map { it to workStatusLabel(it) }) { status = it }
        Spacer(Modifier.height(12.dp)); SegmentedChoice(listOf("NORMAL" to "NORMALE", "HIGH" to "ALTA 🔥"), priority.name, p) { priority = Priority.valueOf(it) }
        Spacer(Modifier.height(12.dp)); SegmentedChoice(listOf("NONE" to "NESSUNO", "WAITING_CLIENT" to "CLIENTE", "WAITING_MATERIAL" to "TESSUTO"), blocker.name, p) { blocker = Blocker.valueOf(it) }
        Spacer(Modifier.height(12.dp)); SpectroTextField("Descrizione / note", description, { description = it }, singleLine = false)
        Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega foto") }
        if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
        Spacer(Modifier.height(16.dp)); GradientButton(if (editing == null) "Salva ordine in processo" else "Salva modifiche", p) {
            if (client.isNotBlank() && title.isNotBlank()) {
                vm.saveWorkOrder(WorkOrder(id = editing?.id ?: 0L, receivedDate = date, client = client.trim(), title = title.trim(), description = description.trim(), images = images, status = status, dueDate = due.trim(), priority = priority, blocker = blocker, clientId = clientId, orderCode = editing?.orderCode.orEmpty(), fabricInventoryId = fabricId, orderedMeters = orderedMeters.toDoubleSafe(), printPricePerMeter = printPrice.toDoubleSafe(), designCode = designCode.trim(), productionRecordId = editing?.productionRecordId, effectiveDeliveryDate = editing?.effectiveDeliveryDate.orEmpty(), cancelReason = editing?.cancelReason.orEmpty()))
                if (editing == null) DraftStore.clearWork(context)
                onDone()
            }
        }
    }
    if (clientDialog) SelectionDialog("Scegli cliente", vm.state.clients.map { it.name to it.id }, onDismiss = { clientDialog = false }) { id -> clientDialog = false; vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; client = it.name; printPrice = it.defaultPrintPrice.takeIf { v -> v > 0 }?.toString().orEmpty() } }
    if (fabricDialog) SelectionDialog("Scegli tessuto", vm.state.inventory.map { "${it.ownerName} • ${it.fabricName} (${meters(it.quantityMeters)})" to it.id }, onDismiss = { fabricDialog = false }) { id -> fabricDialog = false; fabricId = id }
}

@Composable
private fun NegativeForm(vm: SpectroViewModel, p: SpectroPalette, editing: ProductionRecord?, onDone: () -> Unit) {
    val context = LocalContext.current
    var date by remember(editing?.id) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var description by remember(editing?.id) { mutableStateOf(editing?.technicalNotes.orEmpty()) }
    var refund by remember(editing?.id) { mutableStateOf(editing?.refundCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var fabricCost by remember(editing?.id) { mutableStateOf(editing?.fabricLossCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var fixed by remember(editing?.id) { mutableStateOf(editing?.applyFixedCosts ?: true) }
    var images by remember(editing?.id) { mutableStateOf(editing?.images ?: emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }
    SpectroCard(p = p) {
        Text("Evento negativo", color = p.danger, fontWeight = FontWeight.Black, fontSize = 20.sp)
        Spacer(Modifier.height(14.dp)); WheelDateField("Data evento", date, { date = it })
        Spacer(Modifier.height(12.dp)); SpectroTextField("Descrizione", description, { description = it }, singleLine = false)
        Spacer(Modifier.height(12.dp)); SpectroTextField("Rimborso / extra €", refund, { refund = it }, numeric = true)
        Spacer(Modifier.height(12.dp)); SpectroTextField("Costo tessuto buttato €", fabricCost, { fabricCost = it }, numeric = true)
        Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Includi costi fissi?", Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(fixed, { fixed = it }) }
        Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega prova") }
        if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
        Spacer(Modifier.height(16.dp)); GradientButton("Registra perdita / evento", p) { vm.saveProduction(ProductionRecord(id = editing?.id ?: 0L, type = RecordType.NEGATIVE, date = date, clientName = "EVENTO NEGATIVO", technicalNotes = description.trim(), images = images, refundCost = refund.toDoubleSafe(), fabricLossCost = fabricCost.toDoubleSafe(), applyFixedCosts = fixed)); onDone() }
    }
}

@Composable
private fun SelectionDialog(title: String, entries: List<Pair<String, Long>>, onDismiss: () -> Unit, onSelected: (Long) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }, title = { Text(title) }, text = {
        if (entries.isEmpty()) Text("Nessun elemento disponibile") else LazyColumn(Modifier.heightIn(max = 420.dp)) { items(entries, key = { it.second }) { item -> ListItem(headlineContent = { Text(item.first) }, modifier = Modifier.clickable { onSelected(item.second) }); HorizontalDivider() } }
    })
}
