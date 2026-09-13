package com.spectro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(
    vm: SpectroViewModel,
    p: SpectroPalette,
    onDismiss: () -> Unit,
    onOpenOrder: (WorkOrder) -> Unit = {},
    onOpenCotton: () -> Unit = {},
    onOpenZentha: () -> Unit = {},
    onOpenInventory: () -> Unit = {}
) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val productions = if (query.isBlank()) emptyList() else vm.state.production.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.designs.any { d -> d.contains(query, true) } || it.technicalNotes.contains(query, true) }.take(8)
    val works = if (query.isBlank()) emptyList() else vm.state.workOrders.filter { it.client.contains(query, true) || it.title.contains(query, true) || it.orderCode.contains(query, true) || it.designCode.contains(query, true) || it.description.contains(query, true) }.take(8)
    val clients = if (query.isBlank()) emptyList() else vm.state.clients.filter { it.name.contains(query, true) }.take(8)
    val inventory = if (query.isBlank()) emptyList() else vm.state.inventory.filter { i -> i.ownerName.contains(query, true) || i.fabricName.contains(query, true) || i.lotCode.contains(query, true) || vm.rollsForInventory(i.id).any { it.rollCode.contains(query, true) } }.take(8)
    val timeline = if (query.isBlank()) emptyList() else vm.state.timelineEvents.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.title.contains(query, true) || it.description.contains(query, true) }.take(8)
    val cotton = if (query.isBlank()) emptyList() else vm.state.cottonOrders.filter { it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.fabricName.contains(query, true) || it.notes.contains(query, true) }.take(8)
    val zentha = if (query.isBlank()) emptyList() else vm.state.zenthaOrders.filter { it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.notes.contains(query, true) }.take(8)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Ricerca globale", fontSize = 26.sp, fontWeight = FontWeight.Black); Text("Cliente, ordine, disegno, tessuto, collo, criticità, cotone o Zentha.", color = p.textMuted) }
            item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Cerca in tutto Spectro…") }, singleLine = true) }
            val empty = productions.isEmpty() && works.isEmpty() && clients.isEmpty() && inventory.isEmpty() && timeline.isEmpty() && cotton.isEmpty() && zentha.isEmpty()
            if (query.isBlank()) item { Text("La ricerca passa attraverso tutto l'archivio locale.", color = p.textMuted) }
            if (query.isNotBlank() && empty) item { Text("Nessun risultato.", color = p.textMuted) }
            if (clients.isNotEmpty()) { item { SectionTitle("Clienti") }; clients.forEach { c -> item { SearchRow(c.name, "Cliente • listino ${euro(c.defaultPrintPrice)}/mt", p) } } }
            if (works.isNotEmpty()) { item { SectionTitle("Ordini") }; works.forEach { w -> item { SearchRow(w.orderCode.ifBlank { w.title }, "${w.client} • ${workStatusLabel(w.status)} • ${w.dueDate}", p) { onDismiss(); onOpenOrder(w) } } } }
            if (inventory.isNotEmpty()) { item { SectionTitle("Magazzino") }; inventory.forEach { i -> item { SearchRow(i.fabricName, "${i.ownerName} • ${meters(i.quantityMeters)} • ${vm.rollsForInventory(i.id).size} colli", p) { onDismiss(); onOpenInventory() } } } }
            if (timeline.isNotEmpty()) { item { SectionTitle("Timeline / criticità") }; timeline.forEach { e -> item { SearchRow(e.title, "${e.clientName} • ${e.orderRef} • ${e.date}", p) { vm.state.workOrders.firstOrNull { it.id == e.workOrderId }?.let { w -> onDismiss(); onOpenOrder(w) } } } } }
            if (cotton.isNotEmpty()) { item { SectionTitle("Cotone") }; cotton.forEach { c -> item { SearchRow(c.orderCode, "${c.clientName} • ${c.fabricName} • ${meters(c.orderedMeters)}", p) { onDismiss(); onOpenCotton() } } } }
            if (zentha.isNotEmpty()) { item { SectionTitle("Zentha") }; zentha.forEach { z -> item { SearchRow(z.orderCode, "${z.clientName} • ${zenthaStatusLabel(z.status)}", p) { onDismiss(); onOpenZentha() } } } }
            if (productions.isNotEmpty()) { item { SectionTitle("Produzione") }; productions.forEach { r -> item { SearchRow(r.orderRef.ifBlank { r.clientName }, "${r.date} • ${r.clientName} • ${r.designs.joinToString()}", p) } } }
        }
    }
}

@Composable
private fun SearchRow(title: String, subtitle: String, p: SpectroPalette, onClick: (() -> Unit)? = null) {
    SpectroCard(p = p, onClick = onClick) { Text(title, fontWeight = FontWeight.Black); Spacer(Modifier.height(4.dp)); Text(subtitle, color = p.textMuted, fontSize = 12.sp) }
}
