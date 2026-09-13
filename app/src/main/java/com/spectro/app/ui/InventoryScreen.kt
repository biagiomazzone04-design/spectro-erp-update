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
fun InventoryScreen(vm: SpectroViewModel, p: SpectroPalette) {
    var query by remember { mutableStateOf("") }
    var ownerFilter by remember { mutableStateOf("ALL") }
    var loadOpen by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<InventoryItem?>(null) }

    val filtered = vm.state.inventory.filter { item ->
        val matchOwner = ownerFilter == "ALL" || (ownerFilter == "HOUSE" && item.ownerType == OwnerType.HOUSE) || (ownerFilter == "CLIENT" && item.ownerType == OwnerType.CLIENT)
        matchOwner && (query.isBlank() || item.fabricName.contains(query, true) || item.ownerName.contains(query, true) || item.lotCode.contains(query, true) || vm.rollsForInventory(item.id).any { it.rollCode.contains(query, true) })
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Magazzino", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Tessuti per cliente e azienda, colli e giacenza reale.", color = p.textMuted)
                }
                FilledIconButton(onClick = { loadOpen = true }) { Icon(Icons.Rounded.Add, "Nuovo carico") }
            }
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Cliente, tessuto, lotto o collo…") }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(10.dp))
            SegmentedChoice(listOf("ALL" to "TUTTI", "CLIENT" to "CLIENTI", "HOUSE" to "AZIENDA"), ownerFilter, p) { ownerFilter = it }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("Tessuti", filtered.size.toString(), modifier = Modifier.weight(1f))
                Metric("Colli", filtered.sumOf { vm.rollsForInventory(it.id).count { r -> r.availableMeters > 0 } }.toString(), modifier = Modifier.weight(1f))
                Metric("Metri", meters(filtered.sumOf { it.quantityMeters }), modifier = Modifier.weight(1f))
            }
        }
        if (filtered.isEmpty()) item { EmptyState(Icons.Rounded.Inventory2, "Nessun tessuto", "Premi + per caricare tessuto e colli") }
        items(filtered, key = { it.id }) { item ->
            val rolls = vm.rollsForInventory(item.id)
            val low = item.minimumQuantityMeters > 0 && item.quantityMeters <= item.minimumQuantityMeters
            SpectroCard(p = p, onClick = { selected = item }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(item.fabricName, fontWeight = FontWeight.Black, fontSize = 18.sp)
                        Text(if (item.ownerType == OwnerType.HOUSE) "AZIENDA" else item.ownerName, color = p.textMuted, fontSize = 12.sp)
                    }
                    Text(meters(item.quantityMeters), fontWeight = FontWeight.Black, color = if (low) p.danger else p.primary)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Metric("Colli", rolls.count { it.availableMeters > 0 }.toString(), modifier = Modifier.weight(1f))
                    Metric("Lotto", item.lotCode.ifBlank { "-" }, modifier = Modifier.weight(1f))
                    Metric("Costo", if (item.ownerType == OwnerType.HOUSE) euro(item.purchasePricePerMeter) + "/mt" else "Cliente", modifier = Modifier.weight(1f))
                }
                if (low) { Spacer(Modifier.height(8.dp)); Text("SCORTA BASSA", color = p.danger, fontWeight = FontWeight.Black, fontSize = 11.sp) }
            }
        }
    }

    if (loadOpen) NewInventoryLoadDialog(vm, p, onDismiss = { loadOpen = false })
    selected?.let { item -> InventoryDetailDialog(vm, p, item, onDismiss = { selected = null }) }
}

@Composable
private fun NewInventoryLoadDialog(vm: SpectroViewModel, p: SpectroPalette, onDismiss: () -> Unit) {
    var ownerType by remember { mutableStateOf(OwnerType.CLIENT) }
    var clientId by remember { mutableStateOf<Long?>(null) }
    var fabric by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var composition by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var lot by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var rolls by remember { mutableStateOf(listOf("")) }
    val ownerName = if (ownerType == OwnerType.HOUSE) "AZIENDA" else vm.state.clients.firstOrNull { it.id == clientId }?.name.orEmpty()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo carico tessuto", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 610.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SegmentedChoice(listOf("CLIENT" to "CLIENTE", "HOUSE" to "AZIENDA"), ownerType.name, p) { ownerType = OwnerType.valueOf(it) } }
                if (ownerType == OwnerType.CLIENT) item {
                    SimpleSelectField("Cliente", ownerName.ifBlank { "Seleziona cliente" }, vm.state.clients.map { it.id to it.name }) { clientId = it }
                }
                item { SpectroTextField("Nome tessuto", fabric, { fabric = it }, placeholder = "Es. Piquet 140") }
                item { SpectroTextField("Fornitore", supplier, { supplier = it }, placeholder = "Facoltativo") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Composizione", composition, { composition = it }, Modifier.weight(1f)); SpectroTextField("Grammatura", weight, { weight = it }, Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Variante", variant, { variant = it }, Modifier.weight(1f)); SpectroTextField("Lotto", lot, { lot = it }, Modifier.weight(1f)) } }
                if (ownerType == OwnerType.HOUSE) item { SpectroTextField("Costo €/mt", cost, { cost = it }, numeric = true) }
                item { SpectroTextField("Soglia minima mt", minimum, { minimum = it }, numeric = true) }
                item { WheelDateField("Data ingresso", date, { date = it }) }
                item { SectionTitle("Colli", "+ Aggiungi") { rolls = rolls + "" } }
                items(rolls.size) { index ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        SpectroTextField("Collo ${index + 1} - metri", rolls[index], { value -> rolls = rolls.toMutableList().also { it[index] = value } }, Modifier.weight(1f), numeric = true)
                        if (rolls.size > 1) IconButton(onClick = { rolls = rolls.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
                    }
                }
                item { Text("Totale carico: ${meters(rolls.sumOf { it.toDoubleSafe() })}", fontWeight = FontWeight.Black, color = p.primary) }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (fabric.isNotBlank() && ownerName.isNotBlank() && rolls.any { it.toDoubleSafe() > 0 }) {
                    vm.createInventoryWithRolls(
                        InventoryItem(ownerType = ownerType, ownerName = ownerName, fabricName = fabric.trim(), quantityMeters = 0.0, purchasePricePerMeter = cost.toDoubleSafe(), createdDate = date, minimumQuantityMeters = minimum.toDoubleSafe(), supplierName = supplier.trim(), composition = composition.trim(), weightGsm = weight.trim(), variant = variant.trim(), lotCode = lot.trim()),
                        rolls.map { it.toDoubleSafe() }
                    ); onDismiss()
                }
            }) { Text("Salva carico") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun InventoryDetailDialog(vm: SpectroViewModel, p: SpectroPalette, item: InventoryItem, onDismiss: () -> Unit) {
    var addRoll by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf<FabricRoll?>(null) }
    val rolls = vm.rollsForInventory(item.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${item.fabricName} • ${item.ownerName}", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 600.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Giacenza totale ${meters(item.quantityMeters)} • ${rolls.count { it.availableMeters > 0 }} colli disponibili", color = p.textMuted) }
                items(rolls, key = { it.id }) { roll ->
                    SpectroCard(p = p) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) { Text(roll.rollCode, fontWeight = FontWeight.Black); Text("Iniziali ${meters(roll.initialMeters)}", color = p.textMuted, fontSize = 12.sp) }
                            Text(meters(roll.availableMeters), fontWeight = FontWeight.Black, color = if (roll.availableMeters <= 0) p.danger else p.primary)
                        }
                        val moves = vm.movementsForRoll(roll.id).take(3)
                        if (moves.isNotEmpty()) { Spacer(Modifier.height(8.dp)); moves.forEach { m -> Text("${m.dateTime.take(16).replace('T', ' ')}  ${if (m.meters >= 0) "+" else ""}${"%.1f".format(m.meters)} mt  ${m.orderRef}", fontSize = 11.sp, color = p.textMuted) } }
                        Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { adjust = roll }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(6.dp)); Text("Rettifica / inventario") }
                    }
                }
                item { OutlinedButton(onClick = { addRoll = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Aggiungi collo") } }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )

    if (addRoll) AddRollDialog(vm, item.id) { addRoll = false }
    adjust?.let { roll -> AdjustRollDialog(vm, roll) { adjust = null } }
}

@Composable
private fun AddRollDialog(vm: SpectroViewModel, inventoryId: Long, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var meters by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aggiungi collo") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SpectroTextField("Codice collo", code, { code = it }, placeholder = "Automatico se vuoto"); SpectroTextField("Metri", meters, { meters = it }, numeric = true); SpectroTextField("Nota", note, { note = it }) } }, confirmButton = { Button(onClick = { if (meters.toDoubleSafe() > 0) { vm.addRoll(inventoryId, code, meters.toDoubleSafe(), note); onDismiss() } }) { Text("Aggiungi") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

@Composable
private fun AdjustRollDialog(vm: SpectroViewModel, roll: FabricRoll, onDismiss: () -> Unit) {
    var meterText by remember { mutableStateOf(roll.availableMeters.toString()) }
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rettifica ${roll.rollCode}") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Disponibili attuali: ${meters(roll.availableMeters)}")
                SpectroTextField("Nuova giacenza mt", meterText, { meterText = it }, numeric = true)
                SpectroTextField("Motivo rettifica", note, { note = it }, singleLine = false)
            }
        },
        confirmButton = { Button(onClick = { if (note.isNotBlank()) { vm.adjustRoll(roll.id, meterText.toDoubleSafe(), note); onDismiss() } }) { Text("Conferma") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
