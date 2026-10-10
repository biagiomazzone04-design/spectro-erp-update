package com.spectro.app.ui

import androidx.activity.compose.BackHandler
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
import com.spectro.app.auth.AppPermission
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.ReportExporter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(
    vm: SpectroViewModel,
    p: SpectroPalette,
    onDismiss: () -> Unit,
    onOpenClient: (Client) -> Unit = {},
    onOpenOrder: (WorkOrder) -> Unit = {},
    onOpenProduction: (ProductionRecord) -> Unit = {},
    onOpenCotton: (CottonOrder) -> Unit = {},
    onOpenZentha: (ZenthaOrder) -> Unit = {},
    onOpenInventory: (InventoryItem) -> Unit = {}
) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val productions = if (query.isBlank() || !vm.can(AppPermission.PRODUCTION_VIEW)) emptyList() else vm.state.production.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.designs.any { d -> d.contains(query, true) } || it.technicalNotes.contains(query, true) }.take(10)
    val works = if (query.isBlank() || !vm.can(AppPermission.ORDERS_VIEW)) emptyList() else vm.state.workOrders.filter { it.client.contains(query, true) || it.title.contains(query, true) || it.orderCode.contains(query, true) || it.designCode.contains(query, true) || it.description.contains(query, true) }.take(10)
    val clients = if (query.isBlank() || !vm.can(AppPermission.CLIENTS_VIEW)) emptyList() else vm.state.clients.filter { c ->
        c.name.contains(query, true) || (vm.can(AppPermission.INVENTORY_VIEW) && vm.state.inventory.any { it.ownerName.equals(c.name, true) && (it.fabricName.contains(query, true) || it.ownerName.contains(query, true)) }) || (vm.can(AppPermission.ORDERS_VIEW) && vm.state.workOrders.any { (it.clientId == c.id || it.client.equals(c.name, true)) && (it.title.contains(query, true) || it.orderCode.contains(query, true)) })
    }.take(10)
    val inventory = if (query.isBlank() || !vm.can(AppPermission.INVENTORY_VIEW)) emptyList() else vm.state.inventory.filter { i -> i.ownerName.contains(query, true) || i.fabricName.contains(query, true) || i.lotCode.contains(query, true) || vm.rollsForInventory(i.id).any { it.rollCode.contains(query, true) } }.take(10)
    val timeline = if (query.isBlank() || !vm.can(AppPermission.ISSUES_VIEW)) emptyList() else vm.state.timelineEvents.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.title.contains(query, true) || it.description.contains(query, true) }.take(10)
    val cotton = if (query.isBlank() || !vm.can(AppPermission.COTTON_VIEW)) emptyList() else vm.state.cottonOrders.filter { it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.title.contains(query, true) || it.fabricName.contains(query, true) || it.notes.contains(query, true) }.take(10)
    val zentha = if (query.isBlank() || !vm.can(AppPermission.ZENTHA_VIEW)) emptyList() else vm.state.zenthaOrders.filter { it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.notes.contains(query, true) }.take(10)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Ricerca globale", fontSize = 26.sp, fontWeight = FontWeight.Black, color = p.text); Text("Ogni risultato è apribile: cliente, ordine, tessuto, collo, produzione, cotone o Zentha.", color = p.textMuted) }
            item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Cerca in tutto Spectro…") }, singleLine = true) }
            val empty = productions.isEmpty() && works.isEmpty() && clients.isEmpty() && inventory.isEmpty() && timeline.isEmpty() && cotton.isEmpty() && zentha.isEmpty()
            if (query.isBlank()) item { Text("Scrivi un cliente, un nome ordine, un tessuto, un codice o una nota.", color = p.textMuted) }
            if (query.isNotBlank() && empty) item { Text("Nessun risultato.", color = p.textMuted) }
            if (clients.isNotEmpty()) { item { SectionTitle("Clienti") }; items(clients, key = { "c${it.id}" }) { c -> SearchRow(c.name, "Cliente • apri profilo, magazzino e ordini", p, Icons.Rounded.Person) { onDismiss(); onOpenClient(c) } } }
            if (works.isNotEmpty()) { item { SectionTitle("Ordini sublimazione") }; items(works, key = { "w${it.id}" }) { w -> SearchRow(w.title.ifBlank { w.orderCode }, "${w.client} • ${workStatusLabel(w.status)}${if (w.orderCode.isNotBlank()) " • ${w.orderCode}" else ""}", p, Icons.Rounded.Assignment) { onDismiss(); onOpenOrder(w) } } }
            if (inventory.isNotEmpty()) { item { SectionTitle("Magazzino") }; items(inventory, key = { "i${it.id}" }) { i -> SearchRow(i.fabricName.uppercase(), "${i.ownerName} • ${meters(i.quantityMeters)} • ${vm.rollsForInventory(i.id).size} colli", p, Icons.Rounded.Inventory2) { onDismiss(); onOpenInventory(i) } } }
            if (timeline.isNotEmpty()) { item { SectionTitle("Timeline / criticità") }; items(timeline, key = { "t${it.id}" }) { e -> SearchRow(e.title, "${e.clientName} • ${e.orderRef} • ${e.date}", p, Icons.Rounded.History) { vm.state.workOrders.firstOrNull { it.id == e.workOrderId }?.let { w -> onDismiss(); onOpenOrder(w) } } } }
            if (cotton.isNotEmpty()) { item { SectionTitle("Cotone") }; items(cotton, key = { "co${it.id}" }) { c -> SearchRow(c.title.ifBlank { c.orderCode.ifBlank { "Ordine cotone" } }, "${c.clientName} • ${c.fabricName} • ${meters(c.orderedMeters)}", p, Icons.Rounded.Texture) { onDismiss(); onOpenCotton(c) } } }
            if (zentha.isNotEmpty()) { item { SectionTitle("Zentha") }; items(zentha, key = { "z${it.id}" }) { z -> SearchRow(z.orderCode.ifBlank { "Ordine Zentha" }, "${z.clientName} • ${zenthaStatusLabel(z.status)}", p, Icons.Rounded.SportsVolleyball) { onDismiss(); onOpenZentha(z) } } }
            if (productions.isNotEmpty()) { item { SectionTitle("Produzione") }; items(productions, key = { "p${it.id}" }) { r -> SearchRow(r.orderRef.ifBlank { r.clientName }, "${r.date} • ${r.clientName} • ${meters(r.printedPaperMeters)} carta", p, Icons.Rounded.Factory) { onDismiss(); onOpenProduction(r) } } }
        }
    }
}

@Composable
private fun SearchRow(title: String, subtitle: String, p: SpectroPalette, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    SpectroCard(p = p, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = p.primary, modifier = Modifier.size(24.dp)); Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black, color = p.text); Spacer(Modifier.height(3.dp)); Text(subtitle, color = p.textMuted, fontSize = 12.sp) }
            Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
        }
    }
}

enum class ClientProfileSection { HOME, INVENTORY, ORDERS, ISSUES, STATS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchClientProfileSheet(
    vm: SpectroViewModel,
    p: SpectroPalette,
    client: Client,
    onDismiss: () -> Unit,
    onOpenOrder: (WorkOrder) -> Unit,
    onOpenProduction: (ProductionRecord) -> Unit,
    onOpenInventory: (InventoryItem) -> Unit,
    onOpenCotton: (CottonOrder) -> Unit = {},
    initialSection: ClientProfileSection = ClientProfileSection.HOME,
    onSectionChange: (ClientProfileSection) -> Unit = {}
) {
    val context = LocalContext.current
    var section by remember(client.id) { mutableStateOf(initialSection) }
    var expandedFabric by remember(client.id) { mutableStateOf<String?>(null) }

    val works = if (vm.can(AppPermission.ORDERS_VIEW)) vm.state.workOrders
        .filter { it.clientId == client.id || it.client.equals(client.name, true) }
        .sortedWith(compareByDescending<WorkOrder> { if (!it.status.isFinishedOrder()) 1 else 0 }.thenByDescending { it.receivedDate }) else emptyList()
    val productions = if (vm.can(AppPermission.PRODUCTION_VIEW)) vm.state.production
        .filter { it.type == RecordType.PRODUCTION && (it.clientId == client.id || it.clientName.equals(client.name, true)) }
        .sortedWith(compareByDescending<ProductionRecord> { it.date }.thenByDescending { it.id }) else emptyList()
    val inventoryAll = if (vm.can(AppPermission.INVENTORY_VIEW)) vm.state.inventory
        .filter { it.ownerType == OwnerType.CLIENT && it.ownerName.equals(client.name, true) }
        .sortedWith(compareBy<InventoryItem> { it.fabricName.lowercase() }.thenByDescending { it.createdDate }) else emptyList()
    val inventoryActive = inventoryAll.filter { it.quantityMeters > .0001 }
    val inventoryHistory = inventoryAll.filter { it.quantityMeters <= .0001 }
    val cottonOrders = if (vm.can(AppPermission.COTTON_VIEW)) vm.state.cottonOrders
        .filter { it.clientId == client.id || it.clientName.equals(client.name, true) }
        .sortedByDescending { it.orderDate } else emptyList()
    val economicWorks = if (vm.can(AppPermission.FINANCE_VIEW)) vm.state.economicWorks
        .filter { it.clientId == client.id || it.clientName.equals(client.name, true) }
        .sortedByDescending { it.date } else emptyList()
    val criticalities = if (vm.can(AppPermission.ISSUES_VIEW)) vm.state.timelineEvents
        .filter { (it.clientId == client.id || it.clientName.equals(client.name, true)) && it.severity != TimelineSeverity.INFO }
        .sortedWith(compareByDescending<OrderTimelineEvent> { it.date }.thenByDescending { it.time }) else emptyList()
    val revenue = productions.sumOf { it.grossRevenue } + cottonOrders.sumOf { it.grossRevenue } + economicWorks.filter { it.status != EconomicWorkStatus.CANCELLED }.sumOf { it.grossRevenue }
    val paperMeters = productions.sumOf { it.printedPaperMeters }
    val fabricMeters = productions.sumOf { it.fabricUsedMeters }
    val totalStock = inventoryActive.sumOf { it.quantityMeters }

    LaunchedEffect(section) { onSectionChange(section) }
    BackHandler(enabled = section != ClientProfileSection.HOME) { section = ClientProfileSection.HOME }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (section != ClientProfileSection.HOME) {
                        IconButton(onClick = { section = ClientProfileSection.HOME }) {
                            Icon(Icons.Rounded.ArrowBack, "Torna al cliente", tint = p.text)
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(client.name, fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.text)
                        Text(
                            when (section) {
                                ClientProfileSection.HOME -> "Profilo operativo cliente"
                                ClientProfileSection.INVENTORY -> "Magazzino cliente"
                                ClientProfileSection.ORDERS -> "Ordini in corso e pregressi"
                                ClientProfileSection.ISSUES -> "Criticità e storico problemi"
                                ClientProfileSection.STATS -> "Statistiche cliente"
                            },
                            color = p.textMuted
                        )
                    }
                }
            }

            when (section) {
                ClientProfileSection.HOME -> {
                    item {
                        SpectroCard(p = p) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Attivi", works.count { !it.status.isFinishedOrder() }.toString(), modifier = Modifier.weight(1f))
                                Metric("Evasi", works.count { it.status.isArchivedOrder() }.toString(), p.success, Modifier.weight(1f))
                                Metric("Magazzino", meters(totalStock), p.primary, Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Criticità", criticalities.size.toString(), if (criticalities.any { it.status == TimelineStatus.OPEN }) p.secondary else p.success, Modifier.weight(1f))
                                Metric("Carta", meters(paperMeters), modifier = Modifier.weight(1f))
                                if (vm.can(AppPermission.FINANCE_VIEW)) Metric("Fatturato", euro(revenue), p.success, Modifier.weight(1f))
                            }
                        }
                    }
                    if (vm.can(AppPermission.INVENTORY_VIEW)) item {
                        ClientAreaCard("MAGAZZINO", "${inventoryActive.map { it.fabricName.uppercase() }.distinct().size} tessuti attivi • ${meters(totalStock)}", Icons.Rounded.Inventory2, p, p.primary) { section = ClientProfileSection.INVENTORY }
                    }
                    if (vm.can(AppPermission.ORDERS_VIEW)) item {
                        ClientAreaCard("ORDINI", "${works.size} ordini • ${works.count { it.status.isArchivedOrder() }} evasi", Icons.Rounded.Assignment, p, p.primary) { section = ClientProfileSection.ORDERS }
                    }
                    if (vm.can(AppPermission.ISSUES_VIEW)) item {
                        ClientAreaCard("CRITICITÀ", "${criticalities.size} registrate • ${criticalities.count { it.status == TimelineStatus.OPEN }} aperte", Icons.Rounded.ReportProblem, p, p.secondary) { section = ClientProfileSection.ISSUES }
                    }
                    if (vm.can(AppPermission.FINANCE_VIEW)) item {
                        ClientAreaCard("STATISTICHE", "Produzione, metri, fatturato e storico cliente", Icons.Rounded.Analytics, p, p.primary) { section = ClientProfileSection.STATS }
                    }
                }

                ClientProfileSection.INVENTORY -> {
                    item {
                        SpectroCard(p = p) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Tessuti attivi", inventoryActive.map { it.fabricName.uppercase() }.distinct().size.toString(), modifier = Modifier.weight(1f))
                                Metric("Metri a terra", meters(totalStock), p.primary, Modifier.weight(1f))
                                Metric("Esauriti", inventoryHistory.size.toString(), p.textMuted, Modifier.weight(1f))
                            }
                        }
                    }
                    val fabricGroups = inventoryActive.groupBy { it.fabricName.trim().uppercase() }.toList().sortedBy { it.first }
                    if (fabricGroups.isEmpty()) item { Text("Nessun tessuto attivo per questo cliente.", color = p.textMuted) }
                    items(fabricGroups, key = { "fabric-${it.first}" }) { (fabricName, rows) ->
                        SpectroCard(p = p, onClick = { expandedFabric = if (expandedFabric == fabricName) null else fabricName }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(fabricName, fontWeight = FontWeight.Black, fontSize = 18.sp, color = p.text)
                                    Text("${rows.size} carichi • ${rows.sumOf { vm.rollsForInventory(it.id).count { roll -> roll.availableMeters > .0001 } }} colli disponibili", color = p.textMuted, fontSize = 11.sp)
                                }
                                Text(meters(rows.sumOf { it.quantityMeters }), color = p.primary, fontWeight = FontWeight.Black, fontSize = 17.sp)
                                Icon(if (expandedFabric == fabricName) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, tint = p.textMuted)
                            }
                            if (expandedFabric == fabricName) {
                                Spacer(Modifier.height(10.dp))
                                rows.sortedByDescending { it.createdDate }.forEach { row ->
                                    HorizontalDivider(color = p.border)
                                    Row(
                                        Modifier.fillMaxWidth().padding(vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text("CARICO ${row.createdDate}", color = p.text, fontWeight = FontWeight.Black, fontSize = 12.sp)
                                            Text(buildString {
                                                if (row.lotCode.isNotBlank()) append("Lotto ${row.lotCode}")
                                                if (row.variant.isNotBlank()) { if (isNotEmpty()) append(" • "); append(row.variant) }
                                            }.ifBlank { "Apri dettaglio e colli" }, color = p.textMuted, fontSize = 10.sp)
                                        }
                                        Text(meters(row.quantityMeters), color = p.primary, fontWeight = FontWeight.Black)
                                        IconButton(onClick = { onOpenInventory(row) }) { Icon(Icons.Rounded.ChevronRight, "Apri", tint = p.primary) }
                                    }
                                }
                            }
                        }
                    }
                    if (inventoryHistory.isNotEmpty()) {
                        item { SectionTitle("Storico tessuti esauriti") }
                        items(inventoryHistory.take(20), key = { "hist-inv-${it.id}" }) { row ->
                            SpectroCard(p = p, onClick = { onOpenInventory(row) }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(row.fabricName.uppercase(), color = p.text, fontWeight = FontWeight.Black)
                                        Text("Caricato ${row.createdDate}${if (row.lotCode.isNotBlank()) " • lotto ${row.lotCode}" else ""}", color = p.textMuted, fontSize = 11.sp)
                                    }
                                    Text("ESAURITO", color = p.textMuted, fontWeight = FontWeight.Black, fontSize = 10.sp)
                                    Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                                }
                            }
                        }
                    }
                }

                ClientProfileSection.ORDERS -> {
                    if (works.isEmpty() && cottonOrders.isEmpty()) item { Text("Nessun ordine registrato.", color = p.textMuted) }
                    if (works.isNotEmpty()) item { SectionTitle("Sublimazione") }
                    items(works, key = { "wo${it.id}" }) { w ->
                        val fabrics = orderFabricRows(vm, w)
                        val productionRows = vm.productionsForOrder(w.id)
                        val productionNotes = productionRows.mapNotNull { it.technicalNotes.trim().takeIf(String::isNotBlank) }.distinct()
                        val previewImages = (w.images + productionRows.flatMap { it.images }).distinct()
                        SpectroCard(p = p, onClick = { onOpenOrder(w) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(w.title.ifBlank { w.orderCode }, fontWeight = FontWeight.Black, fontSize = 18.sp, color = p.text)
                                    Text("${workStatusLabel(w.status)} • ${w.receivedDate}", color = if (w.status.isArchivedOrder()) p.success else p.textMuted, fontSize = 11.sp)
                                    if (w.orderCode.isNotBlank()) Text(w.orderCode, color = p.textMuted, fontSize = 9.sp)
                                }
                                IconButton(onClick = { ReportExporter.shareWorkOrder(context, w) }) { Icon(Icons.Rounded.PictureAsPdf, "PDF", tint = p.primary) }
                                Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                            }
                            if (fabrics.isNotEmpty()) {
                                Spacer(Modifier.height(10.dp))
                                fabrics.forEach { row ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(row.fabricName.uppercase(), color = p.text, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                        Text(if (row.meters > 0) meters(row.meters) else "-", color = p.primary, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    }
                                }
                            }
                            if (w.description.isNotBlank()) {
                                Spacer(Modifier.height(8.dp)); Text(w.description, color = p.textMuted, fontSize = 11.sp, maxLines = 3)
                            }
                            if (productionNotes.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("NOTE PRODUZIONE", color = p.textMuted, fontSize = 9.sp, fontWeight = FontWeight.Black)
                                productionNotes.take(2).forEach { note -> Text(note, color = p.text, fontSize = 11.sp, maxLines = 2) }
                            }
                            if (previewImages.isNotEmpty()) { Spacer(Modifier.height(8.dp)); ImageStrip(previewImages) }
                            if (productionRows.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                Text("${productionRows.size} produzioni collegate • carta ${meters(productionRows.sumOf { it.printedPaperMeters })} • tessuto ${meters(productionRows.sumOf { it.fabricUsedMeters })}", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    if (cottonOrders.isNotEmpty()) item { SectionTitle("Cotone") }
                    items(cottonOrders, key = { "cot${it.id}" }) { c ->
                        SpectroCard(p = p, onClick = { onOpenCotton(c) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(c.title.ifBlank { c.orderCode.ifBlank { "Ordine cotone" } }, fontWeight = FontWeight.Black, color = p.text)
                                    Text(c.fabricName.uppercase(), color = p.text, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                    Text("${meters(if (c.actualProducedMeters > 0) c.actualProducedMeters else c.orderedMeters)} • ${workStatusLabel(c.status)}", color = p.textMuted, fontSize = 11.sp)
                                }
                                IconButton(onClick = { ReportExporter.shareCottonOrder(context, c) }) { Icon(Icons.Rounded.PictureAsPdf, "PDF", tint = p.primary) }
                                Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                            }
                        }
                    }
                }

                ClientProfileSection.ISSUES -> {
                    item {
                        SpectroCard(p = p) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Totali", criticalities.size.toString(), modifier = Modifier.weight(1f))
                                Metric("Aperte", criticalities.count { it.status == TimelineStatus.OPEN }.toString(), p.secondary, Modifier.weight(1f))
                                Metric("Risolte", criticalities.count { it.status == TimelineStatus.RESOLVED }.toString(), p.success, Modifier.weight(1f))
                            }
                        }
                    }
                    if (criticalities.isEmpty()) item { Text("Nessuna criticità registrata negli ordini di questo cliente.", color = p.textMuted) }
                    items(criticalities, key = { "crit${it.id}" }) { event ->
                        val linked = works.firstOrNull { it.id == event.workOrderId }
                        SpectroCard(p = p, onClick = { if (linked != null) onOpenOrder(linked) }) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(event.title, fontWeight = FontWeight.Black, color = p.text)
                                    Text("${event.date} • ${event.orderRef}", color = p.textMuted, fontSize = 11.sp)
                                }
                                AssistChip(onClick = {}, label = { Text(timelineSeverityLabel(event.severity), fontSize = 9.sp) })
                            }
                            if (event.description.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(localizeWorkStatusTokens(event.description), color = p.text, fontSize = 12.sp, maxLines = 5) }
                            if (event.images.isNotEmpty()) { Spacer(Modifier.height(8.dp)); ImageStrip(event.images) }
                            Spacer(Modifier.height(5.dp)); Text("${timelineResponsibilityLabel(event.responsibility)} • ${if (event.status == TimelineStatus.OPEN) "Aperta" else "Risolta"}", color = if (event.status == TimelineStatus.OPEN) p.secondary else p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                ClientProfileSection.STATS -> {
                    item {
                        SpectroCard(p = p) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Ordini", works.size.toString(), modifier = Modifier.weight(1f))
                                Metric("Produzioni", productions.size.toString(), modifier = Modifier.weight(1f))
                                Metric("Criticità", criticalities.size.toString(), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Carta", meters(paperMeters), p.primary, Modifier.weight(1f))
                                Metric("Tessuto", meters(fabricMeters), modifier = Modifier.weight(1f))
                                Metric("Fatturato", euro(revenue), p.success, Modifier.weight(1f))
                            }
                        }
                    }
                    val byFabric = productions
                        .filter { it.fabricDisplayName.isNotBlank() && !it.fabricDisplayName.equals("Nessuno", true) }
                        .groupBy { it.fabricDisplayName.substringBefore(" (").trim().uppercase() }
                        .mapValues { (_, rows) -> rows.sumOf { it.fabricUsedMeters } }
                        .toList().sortedByDescending { it.second }
                    if (byFabric.isNotEmpty()) {
                        item { SectionTitle("Tessuti lavorati") }
                        items(byFabric, key = { "stat-fabric-${it.first}" }) { (name, value) ->
                            SpectroCard(p = p) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(name, color = p.text, fontWeight = FontWeight.Black)
                                    Text(meters(value), color = p.primary, fontWeight = FontWeight.Black)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ClientAreaCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    p: SpectroPalette,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    SpectroCard(p = p, onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = MaterialTheme.shapes.medium, color = accent.copy(alpha = .14f)) {
                Icon(icon, null, tint = accent, modifier = Modifier.padding(12.dp).size(26.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = p.text, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text(subtitle, color = p.textMuted, fontSize = 11.sp)
            }
            Icon(Icons.Rounded.ChevronRight, null, tint = accent)
        }
    }
}
