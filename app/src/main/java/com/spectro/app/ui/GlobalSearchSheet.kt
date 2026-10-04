package com.spectro.app.ui

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
    val productions = if (query.isBlank()) emptyList() else vm.state.production.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.designs.any { d -> d.contains(query, true) } || it.technicalNotes.contains(query, true) }.take(10)
    val works = if (query.isBlank()) emptyList() else vm.state.workOrders.filter { it.client.contains(query, true) || it.title.contains(query, true) || it.orderCode.contains(query, true) || it.designCode.contains(query, true) || it.description.contains(query, true) }.take(10)
    val clients = if (query.isBlank()) emptyList() else vm.state.clients.filter { c ->
        c.name.contains(query, true) || vm.state.inventory.any { it.ownerName.equals(c.name, true) && (it.fabricName.contains(query, true) || it.ownerName.contains(query, true)) } || vm.state.workOrders.any { (it.clientId == c.id || it.client.equals(c.name, true)) && (it.title.contains(query, true) || it.orderCode.contains(query, true)) }
    }.take(10)
    val inventory = if (query.isBlank()) emptyList() else vm.state.inventory.filter { i -> i.ownerName.contains(query, true) || i.fabricName.contains(query, true) || i.lotCode.contains(query, true) || vm.rollsForInventory(i.id).any { it.rollCode.contains(query, true) } }.take(10)
    val timeline = if (query.isBlank()) emptyList() else vm.state.timelineEvents.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.title.contains(query, true) || it.description.contains(query, true) }.take(10)
    val cotton = if (query.isBlank()) emptyList() else vm.state.cottonOrders.filter { it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.title.contains(query, true) || it.fabricName.contains(query, true) || it.notes.contains(query, true) }.take(10)
    val zentha = if (query.isBlank()) emptyList() else vm.state.zenthaOrders.filter { it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.notes.contains(query, true) }.take(10)

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
    onOpenCotton: (CottonOrder) -> Unit = {}
) {
    val context = LocalContext.current
    val works = vm.state.workOrders.filter { it.clientId == client.id || it.client.equals(client.name, true) }.sortedByDescending { it.receivedDate }
    val productions = vm.state.production.filter { it.type == RecordType.PRODUCTION && (it.clientId == client.id || it.clientName.equals(client.name, true)) }.sortedByDescending { it.date }
    val inventory = vm.state.inventory.filter { it.ownerType == OwnerType.CLIENT && it.ownerName.equals(client.name, true) }.sortedBy { it.fabricName.lowercase() }
    val cottonOrders = vm.state.cottonOrders.filter { it.clientId == client.id || it.clientName.equals(client.name, true) }.sortedByDescending { it.orderDate }
    val economicWorks = vm.state.economicWorks.filter { it.clientId == client.id || it.clientName.equals(client.name, true) }.sortedByDescending { it.date }
    val criticalities = vm.state.timelineEvents.filter { (it.clientId == client.id || it.clientName.equals(client.name, true)) && it.severity != TimelineSeverity.INFO }.sortedWith(compareByDescending<OrderTimelineEvent> { it.date }.thenByDescending { it.time })
    val revenue = productions.sumOf { it.grossRevenue } + cottonOrders.sumOf { it.grossRevenue } + economicWorks.filter { it.status != EconomicWorkStatus.CANCELLED }.sumOf { it.grossRevenue }
    val paperMeters = productions.sumOf { it.printedPaperMeters }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(client.name, fontSize = 28.sp, fontWeight = FontWeight.Black, color = p.text); Text("Profilo cliente completo", color = p.textMuted) }
            item {
                SpectroCard(p = p) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Ordini", (works.size + cottonOrders.size).toString(), modifier = Modifier.weight(1f))
                        Metric("Carta stampata", meters(paperMeters), modifier = Modifier.weight(1f))
                        Metric("Fatturato", euro(revenue), p.success, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Criticità", criticalities.size.toString(), if (criticalities.any { it.status == TimelineStatus.OPEN }) p.warning else p.textMuted, Modifier.weight(1f))
                        Metric("Aperte", criticalities.count { it.status == TimelineStatus.OPEN }.toString(), if (criticalities.any { it.status == TimelineStatus.OPEN }) p.danger else p.success, Modifier.weight(1f))
                        Metric("Altri ricavi", euro(economicWorks.filter { it.status != EconomicWorkStatus.CANCELLED }.sumOf { it.grossRevenue }), modifier = Modifier.weight(1f))
                    }
                }
            }
            item { SectionTitle("Criticità cliente") }
            if (criticalities.isEmpty()) item { Text("Nessuna criticità registrata negli ordini di questo cliente.", color = p.textMuted) }
            items(criticalities, key = { "crit${it.id}" }) { event ->
                val linked = works.firstOrNull { it.id == event.workOrderId }
                SpectroCard(p = p, onClick = { if (linked != null) onOpenOrder(linked) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(event.title, fontWeight = FontWeight.Black, color = p.text); Text("${event.date} • ${event.orderRef}", color = p.textMuted, fontSize = 11.sp) }
                        AssistChip(onClick = {}, label = { Text(timelineSeverityLabel(event.severity), fontSize = 9.sp) })
                    }
                    if (event.description.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(localizeWorkStatusTokens(event.description), color = p.text, fontSize = 12.sp, maxLines = 4) }
                    Spacer(Modifier.height(5.dp)); Text("${timelineResponsibilityLabel(event.responsibility)} • ${if (event.status == TimelineStatus.OPEN) "Aperta" else "Risolta"}", color = if (event.status == TimelineStatus.OPEN) p.warning else p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            item { SectionTitle("Magazzino cliente") }
            if (inventory.isEmpty()) item { Text("Nessun tessuto presente in magazzino.", color = p.textMuted) }
            items(inventory, key = { "inv${it.id}" }) { i ->
                SpectroCard(p = p, onClick = { onOpenInventory(i) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(i.fabricName.uppercase(), fontWeight = FontWeight.Black, color = p.text); Text("${vm.rollsForInventory(i.id).count { r -> r.availableMeters > 0 }} colli", color = p.textMuted, fontSize = 11.sp) }
                        Text(meters(i.quantityMeters), color = p.primary, fontWeight = FontWeight.Black); Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                    }
                }
            }
            item { SectionTitle("Ordini in corso e pregressi") }
            if (works.isEmpty()) item { Text("Nessun ordine registrato.", color = p.textMuted) }
            items(works, key = { "wo${it.id}" }) { w ->
                SpectroCard(p = p, onClick = { onOpenOrder(w) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(w.title.ifBlank { w.orderCode }, fontWeight = FontWeight.Black, color = p.text)
                            Text("${workStatusLabel(w.status)} • ${w.receivedDate}${if (w.orderCode.isNotBlank()) " • ${w.orderCode}" else ""}", color = p.textMuted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { ReportExporter.shareWorkOrder(context, w) }) { Icon(Icons.Rounded.PictureAsPdf, "Esporta PDF", tint = p.primary) }
                        Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                    }
                }
            }
            item { SectionTitle("Ordini cotone") }
            if (cottonOrders.isEmpty()) item { Text("Nessun ordine cotone registrato.", color = p.textMuted) }
            items(cottonOrders, key = { "cot${it.id}" }) { c ->
                SpectroCard(p = p, onClick = { onOpenCotton(c) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(c.title.ifBlank { c.orderCode.ifBlank { "Ordine cotone" } }, fontWeight = FontWeight.Black, color = p.text)
                            Text("${workStatusLabel(c.status)} • ${c.fabricName} • ${c.orderDate}", color = p.textMuted, fontSize = 11.sp)
                        }
                        IconButton(onClick = { ReportExporter.shareCottonOrder(context, c) }) { Icon(Icons.Rounded.PictureAsPdf, "Esporta PDF", tint = p.primary) }
                        Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                    }
                }
            }
            item { SectionTitle("Produzioni") }
            if (productions.isEmpty()) item { Text("Nessuna produzione registrata.", color = p.textMuted) }
            items(productions.take(20), key = { "prod${it.id}" }) { r ->
                SpectroCard(p = p, onClick = { onOpenProduction(r) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(r.orderRef.ifBlank { "Produzione ${r.date}" }, fontWeight = FontWeight.Black, color = p.text); Text("${r.date} • ${meters(r.printedPaperMeters)} carta", color = p.textMuted, fontSize = 11.sp) }
                        Text(euro(r.grossRevenue), color = p.success, fontWeight = FontWeight.Black); Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted)
                    }
                }
            }
        }
    }
}
