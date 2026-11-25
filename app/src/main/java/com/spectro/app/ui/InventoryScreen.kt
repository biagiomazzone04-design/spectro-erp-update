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

private data class NewRollDraft(
    val meters: String = "",
    val images: List<String> = emptyList()
)

@Composable
fun InventoryScreen(vm: SpectroViewModel, p: SpectroPalette) {
    var query by remember { mutableStateOf("") }
    var ownerFilter by remember { mutableStateOf("ALL") }
    var mode by remember { mutableStateOf("ACTIVE") }
    var loadOpen by remember { mutableStateOf(false) }
    var selectedOwner by remember { mutableStateOf<String?>(null) }
    var selectedGroup by remember { mutableStateOf<List<InventoryItem>?>(null) }
    var selectedItem by remember { mutableStateOf<InventoryItem?>(null) }

    val sourceItems = if (mode == "ACTIVE") vm.activeInventory() else vm.inventoryHistory()
    val matchingItems = sourceItems.filter { item ->
        val matchOwner = ownerFilter == "ALL" || (ownerFilter == "HOUSE" && item.ownerType == OwnerType.HOUSE) || (ownerFilter == "CLIENT" && item.ownerType == OwnerType.CLIENT)
        val rolls = vm.rollsForInventory(item.id)
        val matchQuery = query.isBlank() ||
            item.fabricName.contains(query, true) ||
            item.ownerName.contains(query, true) ||
            item.lotCode.contains(query, true) ||
            item.createdDate.contains(query, true) ||
            item.variant.contains(query, true) ||
            item.supplierName.contains(query, true) ||
            rolls.any { roll -> roll.rollCode.contains(query, true) || roll.note.contains(query, true) }
        matchOwner && matchQuery
    }
    val groups = matchingItems.groupBy { if (it.ownerType == OwnerType.HOUSE) "AZIENDA" else it.ownerName }
        .toList().sortedBy { it.first.lowercase() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Magazzino", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Cliente → tessuto → carico → collo, con storico completo.", color = p.textMuted)
                }
                FilledIconButton(onClick = { loadOpen = true }) { Icon(Icons.Rounded.Add, "Nuovo carico") }
            }
            Spacer(Modifier.height(14.dp))
            SegmentedChoice(listOf("ACTIVE" to "ATTUALE", "HISTORY" to "STORICO"), mode, p) {
                mode = it
                selectedOwner = null
                selectedGroup = null
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                query,
                { query = it },
                Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(if (mode == "ACTIVE") "Cliente, tessuto, lotto o collo…" else "Cerca tessuto storico, data, lotto, cartellino…") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            Spacer(Modifier.height(10.dp))
            SegmentedChoice(listOf("ALL" to "TUTTI", "CLIENT" to "CLIENTI", "HOUSE" to "AZIENDA"), ownerFilter, p) { ownerFilter = it }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (mode == "ACTIVE") {
                    Metric("Clienti", groups.size.toString(), modifier = Modifier.weight(1f))
                    Metric("Colli", matchingItems.sumOf { vm.rollsForInventory(it.id).count { r -> r.availableMeters > .0001 } }.toString(), modifier = Modifier.weight(1f))
                    Metric("Metri", meters(matchingItems.sumOf { it.quantityMeters }), modifier = Modifier.weight(1f))
                } else {
                    Metric("Carichi", matchingItems.size.toString(), modifier = Modifier.weight(1f))
                    Metric("Esauriti", matchingItems.count { it.quantityMeters <= .0001 }.toString(), modifier = Modifier.weight(1f))
                    Metric("Storico mt", meters(matchingItems.sumOf { item -> vm.rollsForInventory(item.id).sumOf { it.initialMeters } }), modifier = Modifier.weight(1f))
                }
            }
        }

        if (mode == "ACTIVE") {
            if (groups.isEmpty()) item { EmptyState(Icons.Rounded.Inventory2, "Nessun tessuto disponibile", "I tessuti esauriti vengono spostati automaticamente nello Storico") }

            items(groups, key = { it.first }) { (owner, fabrics) ->
                val rolls = fabrics.flatMap { vm.activeRollsForInventory(it.id) }
                SpectroCard(p = p, onClick = { selectedOwner = owner }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                            Icon(if (owner == "AZIENDA") Icons.Rounded.Warehouse else Icons.Rounded.Person, null, tint = p.primary, modifier = Modifier.size(30.dp))
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(owner, fontWeight = FontWeight.Black, fontSize = 19.sp)
                            Text("${fabrics.map { it.fabricName.uppercase() }.distinct().size} tessuti • ${rolls.count { it.availableMeters > .0001 }} colli", color = p.textMuted, fontSize = 12.sp)
                        }
                        Text(meters(fabrics.sumOf { it.quantityMeters }), fontWeight = FontWeight.Black, color = p.primary)
                        Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                    }
                }
            }
        } else {
            item {
                Text(
                    "Lo storico conserva anche i tessuti esauriti: data di carico, lotto, colli, movimenti e immagini/cartellini restano sempre consultabili.",
                    color = p.textMuted,
                    fontSize = 11.sp
                )
            }
            if (matchingItems.isEmpty()) item { EmptyState(Icons.Rounded.History, "Storico vuoto", "Non risultano carichi corrispondenti alla ricerca") }
            items(matchingItems, key = { "history-${it.id}" }) { item ->
                val rolls = vm.rollsForInventory(item.id)
                val exhausted = item.quantityMeters <= .0001
                SpectroCard(p = p, onClick = { selectedItem = item }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.fabricName.uppercase(), fontWeight = FontWeight.Black, fontSize = 18.sp, color = p.text)
                            Text(
                                "${if (item.ownerType == OwnerType.HOUSE) "AZIENDA" else item.ownerName} • caricato ${item.createdDate}${item.lotCode.takeIf { it.isNotBlank() }?.let { " • lotto $it" }.orEmpty()}",
                                color = p.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        AssistChip(
                            onClick = {},
                            label = { Text(if (exhausted) "ESAURITO" else "ATTIVO", fontSize = 9.sp, fontWeight = FontWeight.Black) },
                            colors = AssistChipDefaults.assistChipColors(labelColor = if (exhausted) p.textMuted else p.success)
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Iniziali", meters(rolls.sumOf { it.initialMeters }), modifier = Modifier.weight(1f))
                        Metric("Disponibili", meters(item.quantityMeters), if (exhausted) p.textMuted else p.primary, Modifier.weight(1f))
                        Metric("Colli", rolls.size.toString(), modifier = Modifier.weight(1f))
                    }
                    val pictures = rolls.flatMap { it.images }.take(3)
                    if (pictures.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp))
                        ImageStrip(pictures)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Tocca per aprire colli, cartellini e movimenti completi", color = p.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (loadOpen) NewInventoryLoadDialog(vm, p, onDismiss = { loadOpen = false })
    selectedOwner?.let { owner ->
        InventoryOwnerSheet(vm, p, owner, onDismiss = { selectedOwner = null }, onOpenGroup = {
            selectedOwner = null
            selectedGroup = it
        })
    }
    selectedGroup?.let { group ->
        InventoryFabricGroupSheet(vm, p, group, onDismiss = { selectedGroup = null }, onOpenItem = {
            selectedGroup = null
            selectedItem = it
        })
    }
    selectedItem?.let { item -> InventoryDetailSheet(vm, p, item, onDismiss = { selectedItem = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryOwnerSheet(
    vm: SpectroViewModel,
    p: SpectroPalette,
    owner: String,
    onDismiss: () -> Unit,
    onOpenGroup: (List<InventoryItem>) -> Unit
) {
    val fabrics = vm.activeInventory().filter { (if (it.ownerType == OwnerType.HOUSE) "AZIENDA" else it.ownerName).equals(owner, true) }
        .sortedWith(compareBy<InventoryItem> { it.fabricName.lowercase() }.thenBy { it.variant.lowercase() })
    val groups = fabrics.groupBy { it.fabricName.trim().uppercase() }.toList().sortedBy { it.first }
    val totalMeters = fabrics.sumOf { it.quantityMeters }
    val rolls = fabrics.flatMap { vm.activeRollsForInventory(it.id) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(owner, fontSize = 27.sp, fontWeight = FontWeight.Black, color = p.text)
                Text("Magazzino tessuti", color = p.textMuted)
            }
            item {
                SpectroCard(p = p) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Tessuti", groups.size.toString(), modifier = Modifier.weight(1f))
                        Metric("Colli attivi", rolls.count { it.availableMeters > 0 }.toString(), modifier = Modifier.weight(1f))
                        Metric("Giacenza", meters(totalMeters), p.primary, Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("Tessuti presenti") }
            if (groups.isEmpty()) item { Text("Nessun tessuto caricato per questo cliente.", color = p.textMuted) }
            items(groups, key = { it.first }) { (fabricName, entries) ->
                val groupRolls = entries.flatMap { vm.rollsForInventory(it.id) }
                SpectroCard(p = p, onClick = { onOpenGroup(entries) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(fabricName, fontWeight = FontWeight.Black, fontSize = 18.sp, color = p.text)
                            val lots = entries.mapNotNull { it.lotCode.takeIf { code -> code.isNotBlank() } }.distinct()
                            Text(
                                "${entries.size} carichi${if (lots.isNotEmpty()) " • ${lots.size} lotti" else ""} • ${groupRolls.count { it.availableMeters > 0 }} colli",
                                color = p.textMuted,
                                fontSize = 11.sp
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(meters(entries.sumOf { it.quantityMeters }), fontWeight = FontWeight.Black, color = p.primary)
                            Text("giacenza attuale", color = p.textMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.width(4.dp)); Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InventoryFabricGroupSheet(
    vm: SpectroViewModel,
    p: SpectroPalette,
    initialItems: List<InventoryItem>,
    onDismiss: () -> Unit,
    onOpenItem: (InventoryItem) -> Unit
) {
    val ids = remember(initialItems) { initialItems.map { it.id }.toSet() }
    val entries = vm.state.inventory.filter { it.id in ids }.sortedByDescending { it.createdDate }
    val fabricName = entries.firstOrNull()?.fabricName?.uppercase() ?: initialItems.firstOrNull()?.fabricName?.uppercase().orEmpty()
    val ownerName = entries.firstOrNull()?.ownerName ?: initialItems.firstOrNull()?.ownerName.orEmpty()
    val allRolls = entries.flatMap { vm.activeRollsForInventory(it.id) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(fabricName, fontSize = 26.sp, fontWeight = FontWeight.Black, color = p.text)
                Text(ownerName, color = p.textMuted)
            }
            item {
                SpectroCard(p = p) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Giacenza", meters(entries.sumOf { it.quantityMeters }), p.primary, Modifier.weight(1f))
                        Metric("Carichi", entries.size.toString(), modifier = Modifier.weight(1f))
                        Metric("Colli attivi", allRolls.count { it.availableMeters > 0 }.toString(), modifier = Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("Carichi, lotti e colli") }
            items(entries, key = { it.id }) { item ->
                val rolls = vm.activeRollsForInventory(item.id)
                SpectroCard(p = p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.lotCode.takeIf { it.isNotBlank() }?.let { "LOTTO $it" } ?: "CARICO ${item.createdDate}", fontWeight = FontWeight.Black, color = p.text)
                            val details = listOf(item.variant, item.weightGsm.takeIf { it.isNotBlank() }?.let { "$it g/m²" }.orEmpty(), item.composition).filter { it.isNotBlank() }
                            if (details.isNotEmpty()) Text(details.joinToString(" • "), color = p.textMuted, fontSize = 11.sp)
                        }
                        Text(meters(item.quantityMeters), color = p.primary, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp))
                    if (rolls.isEmpty()) {
                        Text("Nessun collo registrato.", color = p.textMuted, fontSize = 11.sp)
                    } else {
                        rolls.forEach { roll ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(roll.rollCode, Modifier.weight(1f), color = p.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("${meters(roll.availableMeters)} / ${meters(roll.initialMeters)}", color = p.textMuted, fontSize = 11.sp)
                            }
                            if (roll.images.isNotEmpty()) {
                                Spacer(Modifier.height(5.dp))
                                ImageStrip(roll.images)
                            }
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    OutlinedButton(onClick = { onOpenItem(item) }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Rounded.ManageSearch, null); Spacer(Modifier.width(6.dp)); Text("Apri e gestisci questo carico")
                    }
                }
            }
        }
    }
}

@Composable
private fun NewInventoryLoadDialog(vm: SpectroViewModel, p: SpectroPalette, onDismiss: () -> Unit) {
    var ownerType by remember { mutableStateOf(OwnerType.CLIENT) }
    var customerName by remember { mutableStateOf("") }
    var fabric by remember { mutableStateOf("") }
    var supplier by remember { mutableStateOf("") }
    var composition by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var variant by remember { mutableStateOf("") }
    var lot by remember { mutableStateOf("") }
    var cost by remember { mutableStateOf("") }
    var processingCost by remember { mutableStateOf("") }
    var salePrice by remember { mutableStateOf("") }
    var minimum by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var rolls by remember { mutableStateOf(listOf(NewRollDraft())) }
    val ownerName = if (ownerType == OwnerType.HOUSE) "AZIENDA" else customerName.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuovo carico tessuto", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { SegmentedChoice(listOf("CLIENT" to "CLIENTE", "HOUSE" to "AZIENDA"), ownerType.name, p) { ownerType = OwnerType.valueOf(it) } }
                if (ownerType == OwnerType.CLIENT) {
                    item {
                        SpectroTextField("Nome cliente o proprietario", customerName, { customerName = it }, placeholder = "Scrivi il nome")
                    }
                    if (vm.state.clients.isNotEmpty()) item {
                        SimpleSelectField("Rubrica (facoltativa)", "Scegli dalla rubrica", vm.state.clients.map { it.name to it.name }) {
                            customerName = it
                        }
                    }
                }
                item { SpectroTextField("Nome tessuto", fabric, { fabric = it }, placeholder = "Es. Piquet 140") }
                item { SpectroTextField("Fornitore", supplier, { supplier = it }, placeholder = "Facoltativo") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Composizione", composition, { composition = it }, Modifier.weight(1f)); SpectroTextField("Grammatura", weight, { weight = it }, Modifier.weight(1f)) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Variante", variant, { variant = it }, Modifier.weight(1f)); SpectroTextField("Lotto", lot, { lot = it }, Modifier.weight(1f)) } }
                if (ownerType == OwnerType.HOUSE) item {
                    SpectroCard(p = p) {
                        Text("Economia tessuto aziendale", fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp)); SpectroTextField("Costo acquisto €/mt", cost, { cost = it }, numeric = true)
                        Spacer(Modifier.height(8.dp)); SpectroTextField("Lavorazioni aggiuntive €/mt", processingCost, { processingCost = it }, numeric = true)
                        Spacer(Modifier.height(8.dp)); SpectroTextField("Prezzo vendita predefinito €/mt", salePrice, { salePrice = it }, numeric = true)
                        Spacer(Modifier.height(6.dp)); Text("Costo effettivo: ${euro(cost.toDoubleSafe() + processingCost.toDoubleSafe())}/mt", color = p.primary, fontWeight = FontWeight.Bold)
                    }
                }
                item { SpectroTextField("Soglia minima mt", minimum, { minimum = it }, numeric = true) }
                item { WheelDateField("Data ingresso", date, { date = it }) }
                item { SectionTitle("Colli", "+ Aggiungi") { rolls = rolls + NewRollDraft() } }
                items(rolls.size) { index ->
                    SpectroCard(p = p) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            SpectroTextField("Collo ${index + 1} - metri", rolls[index].meters, { value -> rolls = rolls.toMutableList().also { it[index] = it[index].copy(meters = value) } }, Modifier.weight(1f), numeric = true)
                            if (rolls.size > 1) IconButton(onClick = { rolls = rolls.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
                        }
                        Spacer(Modifier.height(8.dp))
                        AttachmentSourceButtons(onImported = { imported -> rolls = rolls.toMutableList().also { list -> list[index] = list[index].copy(images = (list[index].images + imported).distinct()) } })
                        if (rolls[index].images.isNotEmpty()) { Spacer(Modifier.height(8.dp)); ImageStrip(rolls[index].images) { removeIndex -> rolls = rolls.toMutableList().also { list -> list[index] = list[index].copy(images = list[index].images.filterIndexed { i, _ -> i != removeIndex }) } } }
                    }
                }
                item { Text("Totale carico: ${meters(rolls.sumOf { it.meters.toDoubleSafe() })}", fontWeight = FontWeight.Black, color = p.primary) }
            }
        },
        confirmButton = {
            Button(enabled = fabric.isNotBlank() && ownerName.isNotBlank() && rolls.any { it.meters.toDoubleSafe() > 0 }, onClick = {
                if (fabric.isNotBlank() && ownerName.isNotBlank() && rolls.any { it.meters.toDoubleSafe() > 0 }) {
                    vm.createInventoryWithRolls(
                        InventoryItem(ownerType = ownerType, ownerName = ownerName, fabricName = fabric.trim(), quantityMeters = 0.0, purchasePricePerMeter = cost.toDoubleSafe(), createdDate = date, minimumQuantityMeters = minimum.toDoubleSafe(), supplierName = supplier.trim(), composition = composition.trim(), weightGsm = weight.trim(), variant = variant.trim(), lotCode = lot.trim(), processingCostPerMeter = processingCost.toDoubleSafe(), defaultSalePricePerMeter = salePrice.toDoubleSafe()),
                        rolls.map { FabricRollInput(it.meters.toDoubleSafe(), it.images) }
                    ); onDismiss()
                }
            }) { Text("Salva carico") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryDetailSheet(vm: SpectroViewModel, p: SpectroPalette, item: InventoryItem, onDismiss: () -> Unit) {
    var addRoll by remember { mutableStateOf(false) }
    var adjust by remember { mutableStateOf<FabricRoll?>(null) }
    var editOpen by remember { mutableStateOf(false) }
    var archiveConfirm by remember { mutableStateOf(false) }
    var showDepleted by remember { mutableStateOf(false) }
    val current = vm.state.inventory.firstOrNull { it.id == item.id } ?: item
    val rolls = vm.activeRollsForInventory(current.id)
    val depletedRolls = vm.depletedRollsForInventory(current.id)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Text(current.fabricName.uppercase(), fontSize = 25.sp, fontWeight = FontWeight.Black, color = p.text)
                Text(current.ownerName, color = p.textMuted)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { editOpen = true }, Modifier.weight(1f)) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(6.dp)); Text("Modifica") }
                    if (current.archived) OutlinedButton(onClick = { vm.restoreInventory(current.id); onDismiss() }, Modifier.weight(1f)) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(6.dp)); Text("Ripristina") }
                    else OutlinedButton(onClick = { archiveConfirm = true }, Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.danger)) { Icon(Icons.Rounded.Archive, null); Spacer(Modifier.width(6.dp)); Text("Archivia") }
                }
            }
            item {
                SpectroCard(p = p) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Giacenza", meters(current.quantityMeters), p.primary, Modifier.weight(1f))
                        Metric("Colli", rolls.count { it.availableMeters > 0 }.toString(), modifier = Modifier.weight(1f))
                        Metric("Lotto", current.lotCode.ifBlank { "-" }, modifier = Modifier.weight(1f))
                    }
                }
            }
            if (current.ownerType == OwnerType.HOUSE && vm.can(com.spectro.app.auth.AppPermission.FINANCE_VIEW)) item {
                SpectroCard(p = p) {
                    Text("Economia tessuto", fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Acquisto", euro(current.purchasePricePerMeter) + "/mt", modifier = Modifier.weight(1f))
                        Metric("Lavorazioni", euro(current.processingCostPerMeter) + "/mt", modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Costo effettivo", euro(current.actualCostPerMeter) + "/mt", p.primary, Modifier.weight(1f))
                        Metric("Vendita", euro(current.defaultSalePricePerMeter) + "/mt", p.success, Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("Colli caricati") }
            if (rolls.isEmpty()) item { Text("Nessun collo attivo. I colli esauriti sono conservati nello storico sottostante.", color = p.textMuted) }
            items(rolls, key = { it.id }) { roll ->
                SpectroCard(p = p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(roll.rollCode, fontWeight = FontWeight.Black, color = p.text); Text("Iniziali ${meters(roll.initialMeters)}", color = p.textMuted, fontSize = 12.sp) }
                        Text(meters(roll.availableMeters), fontWeight = FontWeight.Black, color = if (roll.availableMeters <= 0) p.danger else p.primary)
                    }
                    if (roll.images.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(roll.images) }
                    val moves = vm.movementsForRoll(roll.id).take(4)
                    if (moves.isNotEmpty()) { Spacer(Modifier.height(8.dp)); moves.forEach { m -> Text("${m.dateTime.take(16).replace('T', ' ')}  ${if (m.meters >= 0) "+" else ""}${"%.1f".format(m.meters)} mt  ${m.orderRef}", fontSize = 11.sp, color = p.textMuted) } }
                    Spacer(Modifier.height(8.dp)); OutlinedButton(onClick = { adjust = roll }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Tune, null); Spacer(Modifier.width(6.dp)); Text("Rettifica / inventario") }
                }
            }
            if (depletedRolls.isNotEmpty()) item {
                OutlinedButton(onClick = { showDepleted = !showDepleted }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.History, null); Spacer(Modifier.width(6.dp)); Text(if (showDepleted) "Nascondi colli esauriti" else "Storico colli esauriti (${depletedRolls.size})")
                }
            }
            if (showDepleted) items(depletedRolls, key = { "depleted-${it.id}" }) { roll ->
                SpectroCard(p = p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(roll.rollCode, fontWeight = FontWeight.Black); Text("Esaurito • iniziali ${meters(roll.initialMeters)}", color = p.textMuted, fontSize = 12.sp) }
                        AssistChip(onClick = {}, label = { Text("0 mt") })
                    }
                    val moves = vm.movementsForRoll(roll.id)
                    if (moves.isNotEmpty()) { Spacer(Modifier.height(8.dp)); moves.take(6).forEach { movement -> Text("${movement.dateTime.take(16).replace('T',' ')}  ${movement.meters} mt  ${movement.orderRef}", color = p.textMuted, fontSize = 11.sp) } }
                }
            }
            if (!current.archived) item { OutlinedButton(onClick = { addRoll = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Aggiungi collo") } }
        }
    }
    if (addRoll) AddRollDialog(vm, current.id) { addRoll = false }
    adjust?.let { roll -> AdjustRollDialog(vm, roll) { adjust = null } }
    if (editOpen) EditInventoryDialog(vm, current) { editOpen = false }
    if (archiveConfirm) AlertDialog(
        onDismissRequest = { archiveConfirm = false },
        title = { Text("Archivia tessuto") },
        text = { Text("Scomparirà dal magazzino attuale, ma carichi, colli, consumi, immagini e costi resteranno nello Storico.") },
        confirmButton = { TextButton(onClick = { vm.deleteInventory(current.id); archiveConfirm = false; onDismiss() }) { Text("Archivia", color = p.danger) } },
        dismissButton = { TextButton(onClick = { archiveConfirm = false }) { Text("Annulla") } }
    )
}

@Composable
private fun EditInventoryDialog(vm: SpectroViewModel, item: InventoryItem, onDismiss: () -> Unit) {
    var ownerName by remember(item.id) { mutableStateOf(item.ownerName) }
    var fabricName by remember(item.id) { mutableStateOf(item.fabricName) }
    var supplier by remember(item.id) { mutableStateOf(item.supplierName) }
    var composition by remember(item.id) { mutableStateOf(item.composition) }
    var variant by remember(item.id) { mutableStateOf(item.variant) }
    var lot by remember(item.id) { mutableStateOf(item.lotCode) }
    var purchase by remember(item.id) { mutableStateOf(item.purchasePricePerMeter.toString()) }
    var processing by remember(item.id) { mutableStateOf(item.processingCostPerMeter.toString()) }
    var sale by remember(item.id) { mutableStateOf(item.defaultSalePricePerMeter.toString()) }
    var minimum by remember(item.id) { mutableStateOf(item.minimumQuantityMeters.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Modifica tessuto", fontWeight = FontWeight.Black) },
        text = { LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SpectroTextField("Proprietario", ownerName, { ownerName = it }) }
            item { SpectroTextField("Nome tessuto", fabricName, { fabricName = it }) }
            item { SpectroTextField("Fornitore", supplier, { supplier = it }) }
            item { SpectroTextField("Composizione", composition, { composition = it }) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Variante", variant, { variant = it }, Modifier.weight(1f)); SpectroTextField("Lotto", lot, { lot = it }, Modifier.weight(1f)) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Acquisto €/mt", purchase, { purchase = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Lavorazioni €/mt", processing, { processing = it }, Modifier.weight(1f), numeric = true) } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Vendita €/mt", sale, { sale = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Soglia mt", minimum, { minimum = it }, Modifier.weight(1f), numeric = true) } }
        } },
        confirmButton = { Button(onClick = {
            if (ownerName.isNotBlank() && fabricName.isNotBlank()) {
                vm.saveInventory(item.copy(ownerName = ownerName.trim(), fabricName = fabricName.trim(), supplierName = supplier.trim(), composition = composition.trim(), variant = variant.trim(), lotCode = lot.trim(), purchasePricePerMeter = purchase.toDoubleSafe(), processingCostPerMeter = processing.toDoubleSafe(), defaultSalePricePerMeter = sale.toDoubleSafe(), minimumQuantityMeters = minimum.toDoubleSafe()))
                onDismiss()
            }
        }) { Text("Salva") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun AddRollDialog(vm: SpectroViewModel, inventoryId: Long, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var meters by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Aggiungi collo") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            SpectroTextField("Codice collo", code, { code = it }, placeholder = "Automatico se vuoto")
            SpectroTextField("Metri", meters, { meters = it }, numeric = true)
            SpectroTextField("Nota", note, { note = it })
            AttachmentSourceButtons(onImported = { images = (images + it).distinct() })
            if (images.isNotEmpty()) ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } }
        }
    }, confirmButton = { Button(onClick = { if (meters.toDoubleSafe() > 0) { vm.addRoll(inventoryId, code, meters.toDoubleSafe(), note, images); onDismiss() } }) { Text("Aggiungi") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
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
