package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.spectro.app.util.CottonDraft
import com.spectro.app.util.DraftStore
import java.time.LocalDate

@Composable
fun CottonScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    initialOpenOrderId: Long? = null,
    onOpenConsumed: () -> Unit = {}
) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<CottonOrder?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    val finished = WorkStatus.entries.filter { it.isFinishedOrder() }.toSet()

    LaunchedEffect(initialOpenOrderId, vm.state.cottonOrders) {
        if (initialOpenOrderId != null) {
            vm.state.cottonOrders.firstOrNull { it.id == initialOpenOrderId }?.let { editing = it }
            onOpenConsumed()
        }
    }

    val orders = vm.state.cottonOrders.filter {
        (!activeOnly || it.status !in finished) &&
            (query.isBlank() || it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.title.contains(query, true) || it.fabricName.contains(query, true))
    }.sortedWith(compareByDescending<CottonOrder> { it.status !in finished }.thenByDescending { it.orderDate })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Cotone", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Ordini in corso, immagini, costi e margine reale.", color = p.textMuted)
                }
                FilledIconButton(onClick = { createOpen = true }) { Icon(Icons.Rounded.Add, "Nuovo ordine cotone") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Cliente, nome ordine o tessuto…") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FilterChip(selected = activeOnly, onClick = { activeOnly = !activeOnly }, label = { Text(if (activeOnly) "Solo in corso" else "Tutti") }, leadingIcon = { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(18.dp)) })
                if (DraftStore.hasCotton(context)) {
                    Spacer(Modifier.width(8.dp))
                    AssistChip(onClick = { createOpen = true }, label = { Text("Bozza salvata") }, leadingIcon = { Icon(Icons.Rounded.EditNote, null, Modifier.size(16.dp)) })
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Ordini", orders.size.toString(), modifier = Modifier.weight(1f))
                Metric("Ricavi", euro(orders.sumOf { it.grossRevenue }), modifier = Modifier.weight(1f))
                Metric("Margine", euro(orders.sumOf { it.margin }), valueColor = if (orders.sumOf { it.margin } >= 0) p.success else p.danger, modifier = Modifier.weight(1f))
            }
        }
        if (orders.isEmpty()) item { EmptyState(Icons.Rounded.Texture, "Nessun ordine cotone", "Premi + per aggiungere il primo ordine") }
        items(orders, key = { it.id }) { order ->
            SpectroCard(p = p, onClick = { editing = order }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(order.title.ifBlank { order.orderCode.ifBlank { "Ordine cotone" } }, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text("${order.clientName} • ${order.fabricName} • ${order.orderDate}", color = p.textMuted, fontSize = 12.sp)
                        if (order.orderCode.isNotBlank()) Text(order.orderCode, color = p.textMuted, fontSize = 10.sp)
                    }
                    AssistChip(onClick = {}, label = { Text(workStatusLabel(order.status), fontSize = 9.sp) })
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("Metri", meters(order.orderedMeters), modifier = Modifier.weight(1f))
                    Metric("Ricavo", euro(order.grossRevenue), modifier = Modifier.weight(1f))
                    Metric("Margine", euro(order.margin), valueColor = if (order.margin >= 0) p.success else p.danger, modifier = Modifier.weight(1f))
                }
                if (order.images.isNotEmpty()) { Spacer(Modifier.height(8.dp)); ImageStrip(order.images) }
                Spacer(Modifier.height(6.dp))
                Text("Costi: stampa ${euro(order.productionCost)}${if (order.fabricOwnerType == OwnerType.HOUSE) " + tessuto ${euro(order.fabricCost)}" else " • tessuto cliente"} • Margine ${"%.1f".format(order.marginPercent)}%", color = p.textMuted, fontSize = 11.sp)
            }
        }
    }

    if (createOpen) CottonOrderDialog(vm, p, null) { createOpen = false }
    editing?.let { CottonOrderDialog(vm, p, it) { editing = null } }
}

@Composable
private fun CottonOrderDialog(vm: SpectroViewModel, p: SpectroPalette, editing: CottonOrder?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val draftKey = editing?.id?.let { "edit-$it" } ?: "new"
    val draft = remember(editing?.id) { DraftStore.loadCotton(context, draftKey) }
    var title by remember(editing?.id) { mutableStateOf(draft?.title?.takeIf { it.isNotBlank() } ?: editing?.title.orEmpty()) }
    var clientId by remember(editing?.id) { mutableStateOf(draft?.clientId ?: editing?.clientId) }
    var clientName by remember(editing?.id) { mutableStateOf(draft?.clientName?.takeIf { it.isNotBlank() } ?: editing?.clientName.orEmpty()) }
    var date by remember(editing?.id) { mutableStateOf(draft?.date?.takeIf { it.isNotBlank() } ?: editing?.orderDate ?: LocalDate.now().toString()) }
    var due by remember(editing?.id) { mutableStateOf(draft?.due?.takeIf { it.isNotBlank() } ?: editing?.dueDate.orEmpty()) }
    var fabric by remember(editing?.id) { mutableStateOf(draft?.fabric?.takeIf { it.isNotBlank() } ?: editing?.fabricName.orEmpty()) }
    var printType by remember(editing?.id) { mutableStateOf(draft?.printType?.let { runCatching { CottonPrintType.valueOf(it) }.getOrNull() } ?: editing?.printType ?: CottonPrintType.REACTIVE) }
    var ordered by remember(editing?.id) { mutableStateOf(draft?.ordered?.takeIf { it.isNotBlank() } ?: editing?.orderedMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var produced by remember(editing?.id) { mutableStateOf(draft?.produced?.takeIf { it.isNotBlank() } ?: editing?.actualProducedMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var delivered by remember(editing?.id) { mutableStateOf(draft?.delivered?.takeIf { it.isNotBlank() } ?: editing?.deliveredMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var defective by remember(editing?.id) { mutableStateOf(draft?.defective?.takeIf { it.isNotBlank() } ?: editing?.defectiveMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var samples by remember(editing?.id) { mutableStateOf(draft?.samples?.takeIf { it.isNotBlank() } ?: editing?.sampleMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var leftover by remember(editing?.id) { mutableStateOf(draft?.leftover?.takeIf { it.isNotBlank() } ?: editing?.leftoverMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var productionCost by remember(editing?.id) { mutableStateOf(draft?.productionCost?.takeIf { it.isNotBlank() } ?: editing?.productionCostPerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var printPrice by remember(editing?.id) { mutableStateOf(draft?.printPrice?.takeIf { it.isNotBlank() } ?: editing?.printPricePerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var owner by remember(editing?.id) { mutableStateOf(draft?.owner?.let { runCatching { OwnerType.valueOf(it) }.getOrNull() } ?: editing?.fabricOwnerType ?: OwnerType.CLIENT) }
    var inventoryId by remember(editing?.id) { mutableStateOf(draft?.inventoryId ?: editing?.inventoryId) }
    var fabricCost by remember(editing?.id) { mutableStateOf(draft?.fabricCost?.takeIf { it.isNotBlank() } ?: editing?.fabricCostPerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var notes by remember(editing?.id) { mutableStateOf(draft?.notes?.takeIf { it.isNotBlank() } ?: editing?.notes.orEmpty()) }
    var status by remember(editing?.id) { mutableStateOf(draft?.status?.let { runCatching { WorkStatus.valueOf(it) }.getOrNull() } ?: editing?.status ?: WorkStatus.IN_PROCESS) }
    var images by remember(editing?.id) { mutableStateOf(draft?.images?.takeIf { it.isNotEmpty() } ?: editing?.images ?: emptyList()) }
    var rollDialog by remember { mutableStateOf(false) }
    var inventoryDialog by remember { mutableStateOf(false) }
    var allocations by remember(editing?.id) { mutableStateOf<Map<Long, Double>>(draft?.allocations?.takeIf { it.isNotEmpty() } ?: if (editing != null) vm.state.rollUsages.filter { it.orderKind == OrderKind.COTTON && it.orderId == editing.id }.associate { it.rollId to it.meters } else emptyMap()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() }
    }
    val inventory = vm.state.inventory.firstOrNull { it.id == inventoryId }
    val calc = CottonOrder(
        id = editing?.id ?: 0, orderCode = editing?.orderCode.orEmpty(), clientId = clientId, clientName = clientName,
        orderDate = date, dueDate = due, fabricName = fabric, printType = printType, orderedMeters = ordered.toDoubleSafe(),
        actualProducedMeters = produced.toDoubleSafe(), deliveredMeters = delivered.toDoubleSafe(), defectiveMeters = defective.toDoubleSafe(),
        sampleMeters = samples.toDoubleSafe(), leftoverMeters = leftover.toDoubleSafe(), productionCostPerMeter = productionCost.toDoubleSafe(),
        printPricePerMeter = printPrice.toDoubleSafe(), fabricOwnerType = owner, inventoryId = inventoryId, fabricCostPerMeter = fabricCost.toDoubleSafe(),
        notes = notes, status = status, title = title, images = images
    )
    val needed = if (calc.actualProducedMeters > 0) calc.actualProducedMeters else calc.orderedMeters

    LaunchedEffect(title, clientId, clientName, date, due, fabric, printType, ordered, produced, delivered, defective, samples, leftover, productionCost, printPrice, owner, inventoryId, fabricCost, notes, status, images, allocations) {
        DraftStore.saveCotton(context, CottonDraft(title, clientId, clientName, date, due, fabric, printType.name, ordered, produced, delivered, defective, samples, leftover, productionCost, printPrice, owner.name, inventoryId, fabricCost, notes, status.name, images, allocations), draftKey)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuovo ordine cotone" else "Modifica ${editing.title.ifBlank { editing.orderCode }}", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Bozza automatica attiva • anche le modifiche restano salvate se chiudi la schermata o l'app.", color = p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                item { SpectroTextField("Nome ordine", title, { title = it }, placeholder = "Es. Caprese Voile Settembre") }
                item { SimpleSelectField("Cliente", clientName.ifBlank { "Seleziona cliente" }, vm.state.clients.map { it.id to it.name }) { id -> vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; clientName = it.name } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WheelDateField("Data ordine", date, { date = it }, Modifier.weight(1f)); WheelDateField("Consegna", due, { due = it }, Modifier.weight(1f), allowEmpty = true) } }
                item { SpectroTextField("Tessuto", fabric, { fabric = it }, placeholder = "Es. Voile") }
                item { SimpleSelectField("Tipo stampa", when (printType) { CottonPrintType.REACTIVE -> "Reattiva"; CottonPrintType.PIGMENT -> "Pigmento"; CottonPrintType.OTHER -> "Altro" }, CottonPrintType.entries.map { it to when (it) { CottonPrintType.REACTIVE -> "Reattiva"; CottonPrintType.PIGMENT -> "Pigmento"; CottonPrintType.OTHER -> "Altro" } }) { printType = it } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Metri ordinati", ordered, { ordered = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Costo stampa €/mt", productionCost, { productionCost = it }, Modifier.weight(1f), numeric = true) } }
                item { SpectroTextField("Prezzo stampa €/mt", printPrice, { printPrice = it }, numeric = true) }
                item { SegmentedChoice(listOf("CLIENT" to "TESSUTO CLIENTE", "HOUSE" to "TESSUTO AZIENDA"), owner.name, p) { owner = OwnerType.valueOf(it); if (owner == OwnerType.CLIENT) { inventoryId = null; fabricCost = ""; allocations = emptyMap() } } }
                if (owner == OwnerType.HOUSE) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { inventoryDialog = true }, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.Inventory2, null)
                            Spacer(Modifier.width(6.dp))
                            Text(inventory?.let { "${it.fabricName.uppercase()} • ${meters(it.quantityMeters)}" } ?: "Scegli tessuto aziendale")
                        }
                        SpectroTextField("Costo tessuto €/mt", fabricCost, { fabricCost = it }, numeric = true)
                        OutlinedButton(enabled = inventoryId != null && needed > 0, onClick = { rollDialog = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Inventory2, null); Spacer(Modifier.width(6.dp)); Text(if (allocations.isEmpty()) "Scegli colli" else "${allocations.size} colli • ${meters(allocations.values.sum())}") }
                    }
                }
                item { SectionTitle("Consuntivo produzione") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Prodotti mt", produced, { produced = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Consegnati mt", delivered, { delivered = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Difettosi", defective, { defective = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Campioni", samples, { samples = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Avanzati", leftover, { leftover = it }, Modifier.weight(1f), numeric = true) } }
                item { SimpleSelectField("Stato", workStatusLabel(status), WorkStatus.entries.map { it to workStatusLabel(it) }) { status = it } }
                item { OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Allega immagini ordine") } }
                if (images.isNotEmpty()) item { ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
                item { SpectroTextField("Note", notes, { notes = it }, singleLine = false) }
                item { SpectroCard(p = p) { Text("Riepilogo economico", fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Ricavo", euro(calc.grossRevenue), modifier = Modifier.weight(1f)); Metric("Costi", euro(calc.totalCost), modifier = Modifier.weight(1f)); Metric("Margine", euro(calc.margin), valueColor = if (calc.margin >= 0) p.success else p.danger, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(5.dp)); Text("Margine ${"%.2f".format(calc.marginPercent)}%", color = p.textMuted) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank() && clientName.isNotBlank() && fabric.isNotBlank() && ordered.toDoubleSafe() > 0) {
                    vm.saveCottonOrder(calc.copy(title = title.trim(), notes = notes.trim()), allocations) {
                        DraftStore.clearCotton(context, draftKey)
                        onDismiss()
                    }
                }
            }) { Text("Salva") }
        },
        dismissButton = {
            Row {
                if (editing != null) TextButton(onClick = { vm.deleteCottonOrder(editing.id); onDismiss() }) { Text("Elimina", color = p.danger) }
                else TextButton(onClick = { DraftStore.clearCotton(context, draftKey); onDismiss() }) { Text("Scarta bozza", color = p.danger) }
                TextButton(onClick = onDismiss) { Text(if (editing == null) "Chiudi" else "Annulla") }
            }
        }
    )

    if (inventoryDialog) {
        InventoryPickerDialog(
            title = "Scegli tessuto aziendale",
            inventoryItems = vm.state.inventory,
            ownerTypeFilter = OwnerType.HOUSE,
            initialOwner = "AZIENDA",
            onDismiss = { inventoryDialog = false },
            onSelected = { inv ->
                inventoryDialog = false
                inventoryId = inv.id
                fabric = inv.fabricName
                fabricCost = inv.purchasePricePerMeter.toString()
                allocations = emptyMap()
            }
        )
    }

    if (rollDialog && inventoryId != null) {
        val old = allocations
        val rolls = vm.rollsForInventory(inventoryId!!).map { r -> r.copy(availableMeters = r.availableMeters + (old[r.id] ?: 0.0)) }
        RollAllocationDialog(p, rolls, needed, old, onDismiss = { rollDialog = false }) { allocations = it; rollDialog = false }
    }
}
