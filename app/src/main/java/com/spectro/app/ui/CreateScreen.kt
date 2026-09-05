package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Search
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
import java.time.LocalDate

enum class CreateMode { PRODUCTION, WORK, NEGATIVE }

@Composable
fun CreateScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    initialMode: CreateMode = CreateMode.PRODUCTION,
    editProduction: ProductionRecord? = null,
    editWork: WorkOrder? = null,
    onDone: (CreateMode) -> Unit
) {
    var mode by remember(editProduction?.id, editWork?.id, initialMode) { mutableStateOf(initialMode) }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 130.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(if (editProduction != null || editWork != null) "Modifica registrazione" else "Nuova registrazione", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Inserimento rapido con controlli automatici.", color = p.textMuted)
        }
        item {
            SegmentedChoice(
                listOf("PRODUCTION" to "PRODUTTIVO", "WORK" to "APPUNTO", "NEGATIVE" to "NEGATIVO"),
                mode.name,
                p
            ) { if (editProduction == null && editWork == null) mode = CreateMode.valueOf(it) }
        }
        item {
            AnimatedContent(mode, label = "createMode") { current ->
                when (current) {
                    CreateMode.PRODUCTION -> ProductionForm(vm, p, editProduction?.takeIf { it.type == RecordType.PRODUCTION }) { onDone(CreateMode.PRODUCTION) }
                    CreateMode.NEGATIVE -> NegativeForm(vm, p, editProduction?.takeIf { it.type == RecordType.NEGATIVE }) { onDone(CreateMode.NEGATIVE) }
                    CreateMode.WORK -> WorkForm(vm, p, editWork) { onDone(CreateMode.WORK) }
                }
            }
        }
    }
}

@Composable
private fun ProductionForm(vm: SpectroViewModel, p: SpectroPalette, editing: ProductionRecord?, onDone: () -> Unit) {
    val context = LocalContext.current
    var step by remember(editing?.id) { mutableIntStateOf(1) }
    var date by remember(editing?.id) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var clientId by remember(editing?.id) { mutableStateOf(editing?.clientId) }
    var clientName by remember(editing?.id) { mutableStateOf(editing?.clientName.orEmpty()) }
    var orderRef by remember(editing?.id) { mutableStateOf(editing?.orderRef.orEmpty()) }
    var designs by remember(editing?.id) { mutableStateOf(editing?.designs?.joinToString(", ").orEmpty()) }
    var printPrice by remember(editing?.id) { mutableStateOf(editing?.printPricePerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var plotter by remember(editing?.id) { mutableStateOf(editing?.plotter ?: "X1") }
    var paper by remember(editing?.id) { mutableStateOf(editing?.printedPaperMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var protection by remember(editing?.id) { mutableStateOf(editing?.protectionPaperMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var paperWaste by remember(editing?.id) { mutableStateOf(editing?.paperWasteMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var fabricMode by remember(editing?.id) { mutableStateOf(editing?.fabricMode ?: FabricMode.NONE) }
    var fabricId by remember(editing?.id) { mutableStateOf(editing?.fabricId) }
    var fabricUsed by remember(editing?.id) { mutableStateOf(editing?.fabricUsedMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var fabricWaste by remember(editing?.id) { mutableStateOf(editing?.fabricWasteMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var salePrice by remember(editing?.id) { mutableStateOf(editing?.fabricSalePricePerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var manualFabric by remember(editing?.id) { mutableStateOf(editing?.manualFabricName.orEmpty()) }
    var notes by remember(editing?.id) { mutableStateOf(editing?.technicalNotes.orEmpty()) }
    var images by remember(editing?.id) { mutableStateOf(editing?.images ?: emptyList()) }
    var clientDialog by remember { mutableStateOf(false) }
    var fabricDialog by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() }
    }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { i -> LinearProgressIndicator(progress = { if (step >= i + 1) 1f else 0f }, modifier = Modifier.weight(1f).height(5.dp), color = p.primary, trackColor = p.border) }
        }
        when (step) {
            1 -> SpectroCard(p = p) {
                WheelDateField("Data produzione", date, { date = it })
                Spacer(Modifier.height(12.dp))
                Text("CLIENTE", fontSize = 11.sp, fontWeight = FontWeight.Black, color = p.textMuted)
                Spacer(Modifier.height(7.dp))
                OutlinedButton(onClick = { clientDialog = true }, Modifier.fillMaxWidth()) { Text(clientName.ifBlank { "Scegli dalla rubrica o inserisci manualmente" }) }
                Spacer(Modifier.height(10.dp))
                SpectroTextField("Nome cliente manuale", clientName, { clientName = it; clientId = null }, placeholder = "Usalo solo se non è in rubrica")
                Spacer(Modifier.height(12.dp))
                SpectroTextField("Rif. ordine generale", orderRef, { orderRef = it }, placeholder = "Es. Maglie Estate")
                Spacer(Modifier.height(12.dp))
                SpectroTextField("Codici / disegni", designs, { designs = it }, placeholder = "Es. D102, D118")
                Spacer(Modifier.height(12.dp))
                SpectroTextField("Prezzo stampa €/mt", printPrice, { printPrice = it }, numeric = true)
                Spacer(Modifier.height(12.dp))
                Text("PLOTTER", fontSize = 11.sp, fontWeight = FontWeight.Black, color = p.textMuted)
                Spacer(Modifier.height(7.dp))
                SegmentedChoice(listOf("X1" to "X1", "X2" to "X2"), plotter, p) { plotter = it }
            }
            2 -> SpectroCard(p = p) {
                SpectroTextField("Carta stampata (mt)", paper, { paper = it; if (protection.isBlank()) protection = it }, numeric = true)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpectroTextField("Protezione mt", protection, { protection = it }, Modifier.weight(1f), numeric = true)
                    SpectroTextField("Sfrido carta mt", paperWaste, { paperWaste = it }, Modifier.weight(1f), numeric = true)
                }
                Spacer(Modifier.height(14.dp))
                Text("TESSUTO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = p.textMuted)
                Spacer(Modifier.height(7.dp))
                SegmentedChoice(listOf("NONE" to "NESSUNO", "INVENTORY" to "MAGAZZINO", "MANUAL" to "MANUALE"), fabricMode.name, p) { fabricMode = FabricMode.valueOf(it) }
                if (fabricMode == FabricMode.INVENTORY) {
                    Spacer(Modifier.height(12.dp))
                    val selected = vm.state.inventory.firstOrNull { it.id == fabricId }
                    OutlinedButton(onClick = { fabricDialog = true }, Modifier.fillMaxWidth()) { Text(selected?.let { "${it.ownerName} • ${it.fabricName} • ${meters(it.quantityMeters)}" } ?: "Seleziona tessuto") }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpectroTextField("Metri usati", fabricUsed, { fabricUsed = it }, Modifier.weight(1f), numeric = true)
                        SpectroTextField("Sfrido mt", fabricWaste, { fabricWaste = it }, Modifier.weight(1f), numeric = true)
                    }
                    if (selected?.ownerType == OwnerType.HOUSE) {
                        Spacer(Modifier.height(12.dp)); SpectroTextField("Prezzo vendita tessuto €/mt", salePrice, { salePrice = it }, numeric = true)
                    }
                }
                if (fabricMode == FabricMode.MANUAL) {
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Nome tessuto fornito", manualFabric, { manualFabric = it })
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Metri lavorati", fabricUsed, { fabricUsed = it }, numeric = true)
                }
            }
            else -> SpectroCard(p = p) {
                Text("Riepilogo", fontWeight = FontWeight.Black, fontSize = 18.sp)
                Spacer(Modifier.height(12.dp))
                Metric("Cliente", clientName.ifBlank { "Non specificato" })
                Spacer(Modifier.height(10.dp)); Metric("Carta", meters(paper.toDoubleSafe()))
                Spacer(Modifier.height(10.dp)); Metric("Tessuto", when (fabricMode) { FabricMode.INVENTORY -> vm.state.inventory.firstOrNull { it.id == fabricId }?.fabricName ?: "Non selezionato"; FabricMode.MANUAL -> manualFabric; else -> "Nessuno" })
                Spacer(Modifier.height(14.dp))
                SpectroTextField("Note tecniche", notes, { notes = it }, placeholder = "Gradi calandra, profilo colore, anomalie...", singleLine = false)
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega foto multiple") }
                if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (step > 1) OutlinedButton(onClick = { step-- }, modifier = Modifier.weight(1f)) { Text("Indietro") }
            if (step < 3) Button(onClick = { step++ }, modifier = Modifier.weight(1f)) { Text("Continua") }
            else Button(onClick = {
                vm.saveProduction(ProductionRecord(
                    id = editing?.id ?: 0L,
                    type = RecordType.PRODUCTION,
                    date = date,
                    clientId = clientId,
                    clientName = clientName.trim(),
                    orderRef = orderRef.trim(),
                    designs = designs.split(',', '\n').map { it.trim() }.filter { it.isNotBlank() },
                    printPricePerMeter = printPrice.toDoubleSafe(),
                    plotter = plotter,
                    printedPaperMeters = paper.toDoubleSafe(),
                    protectionPaperMeters = protection.toDoubleSafe(),
                    paperWasteMeters = paperWaste.toDoubleSafe(),
                    fabricMode = fabricMode,
                    fabricId = fabricId,
                    fabricUsedMeters = fabricUsed.toDoubleSafe(),
                    fabricWasteMeters = fabricWaste.toDoubleSafe(),
                    fabricSalePricePerMeter = salePrice.toDoubleSafe(),
                    manualFabricName = manualFabric.trim(),
                    technicalNotes = notes.trim(),
                    images = images,
                    applyFixedCosts = true
                )); onDone()
            }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(7.dp)); Text("Salva") }
        }
    }

    if (clientDialog) SelectionDialog("Scegli cliente", vm.state.clients.map { it.name to it.id }, onDismiss = { clientDialog = false }) { selection ->
        clientDialog = false
        val c = vm.state.clients.firstOrNull { it.id == selection }
        if (c != null) { clientId = c.id; clientName = c.name; printPrice = c.defaultPrintPrice.takeIf { it > 0 }?.toString().orEmpty() }
    }
    if (fabricDialog) SelectionDialog("Scegli tessuto", vm.state.inventory.map { "${it.ownerName} • ${it.fabricName} (${meters(it.quantityMeters)})" to it.id }, onDismiss = { fabricDialog = false }) {
        fabricDialog = false; fabricId = it
    }
}

@Composable
private fun WorkForm(vm: SpectroViewModel, p: SpectroPalette, editing: WorkOrder?, onDone: () -> Unit) {
    val context = LocalContext.current
    var date by remember(editing?.id) { mutableStateOf(editing?.receivedDate ?: LocalDate.now().toString()) }
    var client by remember(editing?.id) { mutableStateOf(editing?.client.orEmpty()) }
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var due by remember(editing?.id) { mutableStateOf(editing?.dueDate.orEmpty()) }
    var priority by remember(editing?.id) { mutableStateOf(editing?.priority ?: Priority.NORMAL) }
    var blocker by remember(editing?.id) { mutableStateOf(editing?.blocker ?: Blocker.NONE) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: WorkStatus.RECEIVED) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var images by remember(editing?.id) { mutableStateOf(editing?.images ?: emptyList()) }
    var clientDialog by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }

    SpectroCard(p = p) {
        WheelDateField("Data ricezione", date, { date = it })
        Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { clientDialog = true }, Modifier.fillMaxWidth()) { Text(client.ifBlank { "Scegli cliente" }) }
        Spacer(Modifier.height(10.dp)); SpectroTextField("Cliente manuale", client, { client = it })
        Spacer(Modifier.height(12.dp)); SpectroTextField("Titolo lavorazione", title, { title = it }, placeholder = "Es. Teli mare in arrivo")
        Spacer(Modifier.height(12.dp)); WheelDateField("Data consegna", due, { due = it }, allowEmpty = true)
        Spacer(Modifier.height(14.dp)); SimpleSelectField(
            "Stato ordine", workStatusLabel(status), WorkStatus.entries.map { it to workStatusLabel(it) }
        ) { status = it }
        Spacer(Modifier.height(14.dp)); Text("URGENZA", fontSize = 11.sp, fontWeight = FontWeight.Black, color = p.textMuted)
        Spacer(Modifier.height(7.dp)); SegmentedChoice(listOf("NORMAL" to "NORMALE", "HIGH" to "ALTA 🔥"), priority.name, p) { priority = Priority.valueOf(it) }
        Spacer(Modifier.height(14.dp)); Text("IMPEDIMENTO", fontSize = 11.sp, fontWeight = FontWeight.Black, color = p.textMuted)
        Spacer(Modifier.height(7.dp)); SegmentedChoice(listOf("NONE" to "NESSUNO", "WAITING_CLIENT" to "CLIENTE", "WAITING_MATERIAL" to "TESSUTO"), blocker.name, p) { blocker = Blocker.valueOf(it) }
        Spacer(Modifier.height(12.dp)); SpectroTextField("Descrizione lavorazione", description, { description = it }, singleLine = false)
        Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega foto") }
        if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
        Spacer(Modifier.height(16.dp)); GradientButton("Salva lavorazione", p) {
            if (client.isNotBlank() && title.isNotBlank()) {
                vm.saveWorkOrder(WorkOrder(editing?.id ?: 0L, date, client.trim(), title.trim(), description.trim(), images, status, due.trim(), priority, blocker)); onDone()
            }
        }
    }
    if (clientDialog) SelectionDialog("Scegli cliente", vm.state.clients.map { it.name to it.id }, onDismiss = { clientDialog = false }) {
        clientDialog = false; client = vm.state.clients.firstOrNull { c -> c.id == it }?.name.orEmpty()
    }
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
        Text("Fermo, rimborso, scarto o problema di produzione.", color = p.textMuted)
        Spacer(Modifier.height(14.dp)); WheelDateField("Data evento", date, { date = it })
        Spacer(Modifier.height(12.dp)); SpectroTextField("Descrizione", description, { description = it }, placeholder = "Es. giornata persa, ristampa cliente", singleLine = false)
        Spacer(Modifier.height(12.dp)); SpectroTextField("Rimborso / extra €", refund, { refund = it }, numeric = true)
        Spacer(Modifier.height(12.dp)); SpectroTextField("Costo tessuto buttato €", fabricCost, { fabricCost = it }, numeric = true)
        Spacer(Modifier.height(12.dp)); Row(verticalAlignment = Alignment.CenterVertically) { Text("Includi costi fissi della giornata?", Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(fixed, { fixed = it }) }
        Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega prova / ricevuta") }
        if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
        Spacer(Modifier.height(16.dp)); GradientButton("Registra perdita / evento", p) {
            vm.saveProduction(ProductionRecord(
                id = editing?.id ?: 0L,
                type = RecordType.NEGATIVE,
                date = date,
                clientName = "EVENTO NEGATIVO",
                technicalNotes = description.trim(),
                images = images,
                refundCost = refund.toDoubleSafe(),
                fabricLossCost = fabricCost.toDoubleSafe(),
                applyFixedCosts = fixed
            )); onDone()
        }
    }
}

@Composable
private fun SelectionDialog(title: String, items: List<Pair<String, Long>>, onDismiss: () -> Unit, onSelected: (Long) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        title = { Text(title) },
        text = {
            if (items.isEmpty()) Text("Nessun elemento disponibile")
            else LazyColumn(Modifier.heightIn(max = 420.dp)) {
                items(items, key = { it.second }) { item ->
                    ListItem(
                        headlineContent = { Text(item.first) },
                        modifier = Modifier.clickable { onSelected(item.second) }
                    )
                    HorizontalDivider()
                }
            }
        }
    )
}
