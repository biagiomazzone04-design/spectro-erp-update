package com.spectro.app.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import java.time.LocalDate

@Composable
fun CottonScreen(vm: SpectroViewModel, p: SpectroPalette) {
    var query by remember { mutableStateOf("") }
    var editing by remember { mutableStateOf<CottonOrder?>(null) }
    var createOpen by remember { mutableStateOf(false) }
    val orders = vm.state.cottonOrders.filter { query.isBlank() || it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.fabricName.contains(query, true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Cotone", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Ordini reattivi/pigmento con costi e margine reale.", color = p.textMuted) }
                FilledIconButton(onClick = { createOpen = true }) { Icon(Icons.Rounded.Add, "Nuovo ordine cotone") }
            }
            Spacer(Modifier.height(12.dp)); OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Cliente, ordine o tessuto…") }, singleLine = true, shape = RoundedCornerShape(16.dp))
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
                    Column(Modifier.weight(1f)) { Text(order.orderCode.ifBlank { "Ordine cotone" }, fontWeight = FontWeight.Black); Text("${order.clientName} • ${order.fabricName} • ${order.orderDate}", color = p.textMuted, fontSize = 12.sp) }
                    AssistChip(onClick = {}, label = { Text(workStatusLabel(order.status), fontSize = 9.sp) })
                }
                Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Metri", meters(order.orderedMeters), modifier = Modifier.weight(1f)); Metric("Ricavo", euro(order.grossRevenue), modifier = Modifier.weight(1f)); Metric("Margine", euro(order.margin), valueColor = if (order.margin >= 0) p.success else p.danger, modifier = Modifier.weight(1f)) }
                Spacer(Modifier.height(6.dp)); Text("Costi: stampa ${euro(order.productionCost)}${if (order.fabricOwnerType == OwnerType.HOUSE) " + tessuto ${euro(order.fabricCost)}" else " • tessuto cliente"} • Margine ${"%.1f".format(order.marginPercent)}%", color = p.textMuted, fontSize = 11.sp)
            }
        }
    }

    if (createOpen) CottonOrderDialog(vm, p, null) { createOpen = false }
    editing?.let { CottonOrderDialog(vm, p, it) { editing = null } }
}

@Composable
private fun CottonOrderDialog(vm: SpectroViewModel, p: SpectroPalette, editing: CottonOrder?, onDismiss: () -> Unit) {
    var clientId by remember(editing?.id) { mutableStateOf(editing?.clientId) }
    var clientName by remember(editing?.id) { mutableStateOf(editing?.clientName.orEmpty()) }
    var date by remember(editing?.id) { mutableStateOf(editing?.orderDate ?: LocalDate.now().toString()) }
    var due by remember(editing?.id) { mutableStateOf(editing?.dueDate.orEmpty()) }
    var fabric by remember(editing?.id) { mutableStateOf(editing?.fabricName.orEmpty()) }
    var printType by remember(editing?.id) { mutableStateOf(editing?.printType ?: CottonPrintType.REACTIVE) }
    var ordered by remember(editing?.id) { mutableStateOf(editing?.orderedMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var produced by remember(editing?.id) { mutableStateOf(editing?.actualProducedMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var delivered by remember(editing?.id) { mutableStateOf(editing?.deliveredMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var defective by remember(editing?.id) { mutableStateOf(editing?.defectiveMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var samples by remember(editing?.id) { mutableStateOf(editing?.sampleMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var leftover by remember(editing?.id) { mutableStateOf(editing?.leftoverMeters?.takeIf { it > 0 }?.toString().orEmpty()) }
    var productionCost by remember(editing?.id) { mutableStateOf(editing?.productionCostPerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var printPrice by remember(editing?.id) { mutableStateOf(editing?.printPricePerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var owner by remember(editing?.id) { mutableStateOf(editing?.fabricOwnerType ?: OwnerType.CLIENT) }
    var inventoryId by remember(editing?.id) { mutableStateOf(editing?.inventoryId) }
    var fabricCost by remember(editing?.id) { mutableStateOf(editing?.fabricCostPerMeter?.takeIf { it > 0 }?.toString().orEmpty()) }
    var notes by remember(editing?.id) { mutableStateOf(editing?.notes.orEmpty()) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: WorkStatus.IN_PROCESS) }
    var rollDialog by remember { mutableStateOf(false) }
    var allocations by remember(editing?.id) { mutableStateOf(vm.state.rollUsages.filter { it.orderKind == OrderKind.COTTON && it.orderId == editing?.id }.associate { it.rollId to it.meters }) }

    val inventory = vm.state.inventory.firstOrNull { it.id == inventoryId }
    val calc = CottonOrder(id = editing?.id ?: 0, orderCode = editing?.orderCode.orEmpty(), clientId = clientId, clientName = clientName, orderDate = date, dueDate = due, fabricName = fabric, printType = printType, orderedMeters = ordered.toDoubleSafe(), actualProducedMeters = produced.toDoubleSafe(), deliveredMeters = delivered.toDoubleSafe(), defectiveMeters = defective.toDoubleSafe(), sampleMeters = samples.toDoubleSafe(), leftoverMeters = leftover.toDoubleSafe(), productionCostPerMeter = productionCost.toDoubleSafe(), printPricePerMeter = printPrice.toDoubleSafe(), fabricOwnerType = owner, inventoryId = inventoryId, fabricCostPerMeter = fabricCost.toDoubleSafe(), notes = notes, status = status)
    val needed = if (calc.actualProducedMeters > 0) calc.actualProducedMeters else calc.orderedMeters

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuovo ordine cotone" else "Modifica ${editing.orderCode}", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SimpleSelectField("Cliente", clientName.ifBlank { "Seleziona cliente" }, vm.state.clients.map { it.id to it.name }) { id -> vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; clientName = it.name } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WheelDateField("Data ordine", date, { date = it }, Modifier.weight(1f)); WheelDateField("Consegna", due, { due = it }, Modifier.weight(1f), allowEmpty = true) } }
                item { SpectroTextField("Tessuto", fabric, { fabric = it }, placeholder = "Es. Voile") }
                item { SimpleSelectField("Tipo stampa", printType.name, CottonPrintType.entries.map { it to when (it) { CottonPrintType.REACTIVE -> "Reattiva"; CottonPrintType.PIGMENT -> "Pigmento"; CottonPrintType.OTHER -> "Altro" } }) { printType = it } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Metri ordinati", ordered, { ordered = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Costo stampa €/mt", productionCost, { productionCost = it }, Modifier.weight(1f), numeric = true) } }
                item { SpectroTextField("Prezzo stampa €/mt", printPrice, { printPrice = it }, numeric = true) }
                item { SegmentedChoice(listOf("CLIENT" to "TESSUTO CLIENTE", "HOUSE" to "TESSUTO AZIENDA"), owner.name, p) { owner = OwnerType.valueOf(it); if (owner == OwnerType.CLIENT) { inventoryId = null; fabricCost = ""; allocations = emptyMap() } } }
                if (owner == OwnerType.HOUSE) item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SimpleSelectField("Tessuto magazzino", inventory?.let { "${it.fabricName} • ${meters(it.quantityMeters)}" } ?: "Seleziona", vm.state.inventory.filter { it.ownerType == OwnerType.HOUSE }.map { it.id to "${it.fabricName} • ${meters(it.quantityMeters)}" }) { id -> inventoryId = id; vm.state.inventory.firstOrNull { it.id == id }?.let { inv -> fabric = inv.fabricName; fabricCost = inv.purchasePricePerMeter.toString() }; allocations = emptyMap() }
                        SpectroTextField("Costo tessuto €/mt", fabricCost, { fabricCost = it }, numeric = true)
                        OutlinedButton(enabled = inventoryId != null && needed > 0, onClick = { rollDialog = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Inventory2, null); Spacer(Modifier.width(6.dp)); Text(if (allocations.isEmpty()) "Scegli colli" else "${allocations.size} colli • ${meters(allocations.values.sum())}") }
                    }
                }
                item { SectionTitle("Consuntivo produzione") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Prodotti mt", produced, { produced = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Consegnati mt", delivered, { delivered = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Difettosi", defective, { defective = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Campioni", samples, { samples = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Avanzati", leftover, { leftover = it }, Modifier.weight(1f), numeric = true) } }
                item { SimpleSelectField("Stato", workStatusLabel(status), WorkStatus.entries.map { it to workStatusLabel(it) }) { status = it } }
                item { SpectroTextField("Note", notes, { notes = it }, singleLine = false) }
                item { SpectroCard(p = p) { Text("Riepilogo economico", fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Ricavo", euro(calc.grossRevenue), modifier = Modifier.weight(1f)); Metric("Costi", euro(calc.totalCost), modifier = Modifier.weight(1f)); Metric("Margine", euro(calc.margin), valueColor = if (calc.margin >= 0) p.success else p.danger, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(5.dp)); Text("Margine ${"%.2f".format(calc.marginPercent)}%", color = p.textMuted) } }
            }
        },
        confirmButton = { Button(onClick = { if (clientName.isNotBlank() && fabric.isNotBlank() && ordered.toDoubleSafe() > 0) { vm.saveCottonOrder(calc, allocations); onDismiss() } }) { Text("Salva") } },
        dismissButton = { Row { if (editing != null) TextButton(onClick = { vm.deleteCottonOrder(editing.id); onDismiss() }) { Text("Elimina", color = p.danger) }; TextButton(onClick = onDismiss) { Text("Annulla") } } }
    )

    if (rollDialog && inventoryId != null) {
        val old = allocations
        val rolls = vm.rollsForInventory(inventoryId!!).map { r -> r.copy(availableMeters = r.availableMeters + (old[r.id] ?: 0.0)) }
        RollAllocationDialog(p, rolls, needed, old, onDismiss = { rollDialog = false }) { allocations = it; rollDialog = false }
    }
}
