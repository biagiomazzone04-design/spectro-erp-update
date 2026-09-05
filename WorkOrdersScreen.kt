package com.spectro.app.ui

import androidx.compose.foundation.background
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
import com.spectro.app.util.ReportExporter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun WorkOrdersScreen(vm: SpectroViewModel, p: SpectroPalette, onEdit: (WorkOrder) -> Unit) {
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    val context = LocalContext.current
    val items = vm.state.workOrders.filter { item ->
        val completed = item.status in listOf(WorkStatus.COMPLETED, WorkStatus.DELIVERED)
        (!activeOnly || !completed) && (query.isBlank() || item.client.contains(query, true) || item.title.contains(query, true) || item.description.contains(query, true))
    }
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text("Ordini & Lavorazioni", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Stato reale dell'ordine, blocchi, urgenze e consegne.", color = p.textMuted)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cerca cliente o ordine…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
            FilterChip(selected = activeOnly, onClick = { activeOnly = !activeOnly }, label = { Text(if (activeOnly) "Solo attivi" else "Tutti") }, leadingIcon = { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(18.dp)) })
        }
        if (items.isEmpty()) item { EmptyState(Icons.Rounded.Assignment, "Nessuna lavorazione", "Usa + e scegli Appunto") }
        items(items, key = { it.id }) { item ->
            val days = runCatching { ChronoUnit.DAYS.between(LocalDate.parse(item.receivedDate), LocalDate.now()) }.getOrDefault(0)
            val completed = item.status in listOf(WorkStatus.COMPLETED, WorkStatus.DELIVERED)
            val overdue = !completed && item.dueDate.isNotBlank() && runCatching { LocalDate.parse(item.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false)
            val accent = when {
                completed -> p.success
                item.status == WorkStatus.BLOCKED || overdue -> p.danger
                item.status in listOf(WorkStatus.WAITING_CLIENT, WorkStatus.WAITING_MATERIAL, WorkStatus.ON_HOLD) || item.blocker != Blocker.NONE -> p.warning
                item.priority == Priority.HIGH -> p.danger
                else -> p.primary
            }
            SpectroCard(p = p) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).height(58.dp).background(accent, RoundedCornerShape(99.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(item.title, fontWeight = FontWeight.Black, fontSize = 17.sp, maxLines = 1, modifier = Modifier.weight(1f))
                            if (item.priority == Priority.HIGH && !completed) Icon(Icons.Rounded.LocalFireDepartment, null, tint = p.danger)
                        }
                        Text("${item.client} • ${item.receivedDate}", color = p.textMuted, fontSize = 12.sp)
                    }
                    AssistChip(onClick = {}, label = { Text(workStatusLabel(item.status).uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Black) })
                }
                if (item.dueDate.isNotBlank()) {
                    Spacer(Modifier.height(10.dp)); Text("Consegna: ${item.dueDate}${if (overdue) " • SCADUTA" else ""}", color = if (overdue) p.danger else p.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                if (item.blocker != Blocker.NONE && !completed) {
                    Spacer(Modifier.height(8.dp)); Text(when (item.blocker) { Blocker.WAITING_CLIENT -> "BLOCCO: attesa cliente/grafica"; Blocker.WAITING_MATERIAL -> "BLOCCO: attesa tessuto"; else -> "" }, color = p.warning, fontWeight = FontWeight.Black, fontSize = 12.sp)
                }
                if (days >= 5 && !completed && item.blocker == Blocker.NONE) { Spacer(Modifier.height(8.dp)); Text("⚠ In coda da $days giorni", color = p.warning, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                if (item.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(item.description, color = p.text, fontSize = 13.sp) }
                if (item.images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(item.images) }
                Spacer(Modifier.height(12.dp))
                SimpleSelectField("Aggiorna stato", workStatusLabel(item.status), WorkStatus.entries.map { it to workStatusLabel(it) }) { vm.setWorkStatus(item.id, it) }
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = { ReportExporter.shareWorkOrder(context, item) }) { Icon(Icons.Rounded.PictureAsPdf, null, tint = p.textMuted) }
                    IconButton(onClick = { vm.deleteWorkOrder(item.id) }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
                    IconButton(onClick = { onEdit(item) }) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }
                }
            }
        }
    }
}
