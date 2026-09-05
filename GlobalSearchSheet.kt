package com.spectro.app.ui

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
import com.spectro.app.data.IssueStatus
import com.spectro.app.ui.theme.SpectroPalette

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlobalSearchSheet(vm: SpectroViewModel, p: SpectroPalette, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val query = q.trim()
    val productions = if (query.isBlank()) emptyList() else vm.state.production.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.designs.any { d -> d.contains(query, true) } || it.technicalNotes.contains(query, true) }.take(8)
    val works = if (query.isBlank()) emptyList() else vm.state.workOrders.filter { it.client.contains(query, true) || it.title.contains(query, true) || it.description.contains(query, true) }.take(8)
    val clients = if (query.isBlank()) emptyList() else vm.state.clients.filter { it.name.contains(query, true) }.take(8)
    val issues = if (query.isBlank()) emptyList() else vm.state.clientIssues.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.title.contains(query, true) || it.description.contains(query, true) }.take(8)
    val payments = if (query.isBlank()) emptyList() else vm.state.payments.filter { it.clientName.contains(query, true) || it.orderRef.contains(query, true) || it.notes.contains(query, true) }.take(8)

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Ricerca globale", fontSize = 26.sp, fontWeight = FontWeight.Black); Text("Trova subito cliente, ordine, disegno, criticità o scadenza.", color = p.textMuted) }
            item { OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Scrivi almeno una parola…") }, singleLine = true) }
            if (query.isBlank()) item { Text("La ricerca passa attraverso tutto l'archivio locale.", color = p.textMuted) }
            if (query.isNotBlank() && productions.isEmpty() && works.isEmpty() && clients.isEmpty() && issues.isEmpty() && payments.isEmpty()) item { Text("Nessun risultato.", color = p.textMuted) }
            if (clients.isNotEmpty()) { item { SectionTitle("Clienti") }; clients.forEach { c -> item { SearchRow(c.name, "Rubrica • listino ${euro(c.defaultPrintPrice)}/mt", p) } } }
            if (productions.isNotEmpty()) { item { SectionTitle("Produzione") }; productions.forEach { r -> item { SearchRow(r.orderRef.ifBlank { r.clientName }, "${r.date} • ${r.clientName} • ${r.designs.joinToString()}", p) } } }
            if (works.isNotEmpty()) { item { SectionTitle("Ordini / lavorazioni") }; works.forEach { w -> item { SearchRow(w.title, "${w.client} • ${workStatusLabel(w.status)} • ${w.dueDate}", p) } } }
            if (issues.isNotEmpty()) { item { SectionTitle("Criticità") }; issues.forEach { i -> item { SearchRow(i.title, "${i.clientName} • ${issueStatusLabel(i.status)}${if (i.status !in listOf(IssueStatus.RESOLVED, IssueStatus.CLOSED)) " • APERTA" else ""}", p) } } }
            if (payments.isNotEmpty()) { item { SectionTitle("Scadenze") }; payments.forEach { pay -> item { SearchRow(pay.clientName, "${euro(pay.amount)} • ${pay.dueDate} • ${if (pay.paid) "Pagato" else "Da incassare"}", p) } } }
        }
    }
}

@Composable
private fun SearchRow(title: String, subtitle: String, p: SpectroPalette) {
    SpectroCard(p = p) { Text(title, fontWeight = FontWeight.Black); Spacer(Modifier.height(4.dp)); Text(subtitle, color = p.textMuted, fontSize = 12.sp) }
}
