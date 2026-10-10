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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.auth.AppPermission
import com.spectro.app.ui.theme.SpectroPalette
import java.time.LocalDate

@Composable
fun WorkOrdersScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    onEdit: (WorkOrder) -> Unit,
    onOpen: (WorkOrder) -> Unit,
    onStartProduction: (WorkOrder) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var activeOnly by remember { mutableStateOf(true) }
    var deleteTarget by remember { mutableStateOf<WorkOrder?>(null) }
    val finished = WorkStatus.entries.filter { it.isFinishedOrder() }.toSet()
    val orders = vm.state.workOrders.filter { item ->
        (!activeOnly || item.status !in finished) &&
            (query.isBlank() || item.client.contains(query, true) || item.title.contains(query, true) || item.orderCode.contains(query, true) || item.designCode.contains(query, true) || item.description.contains(query, true))
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Text("Ordini", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Dal processo alla produzione, fino all'evasione.", color = p.textMuted)
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = { Text("Cliente, ordine, disegno…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
            Spacer(Modifier.height(8.dp))
            FilterChip(selected = activeOnly, onClick = { activeOnly = !activeOnly }, label = { Text(if (activeOnly) "Solo attivi" else "Tutti") }, leadingIcon = { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(18.dp)) })
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("In processo", vm.state.workOrders.count { it.status in listOf(WorkStatus.RECEIVED, WorkStatus.IN_PROCESS) }.toString(), modifier = Modifier.weight(1f))
                Metric("Produzione", vm.state.workOrders.count { it.status in listOf(WorkStatus.IN_PRODUCTION, WorkStatus.IN_PROGRESS, WorkStatus.PRINTED, WorkStatus.TRANSFERRED) }.toString(), modifier = Modifier.weight(1f))
                Metric("Bloccati", vm.state.workOrders.count { it.status == WorkStatus.BLOCKED }.toString(), valueColor = p.danger, modifier = Modifier.weight(1f))
            }
        }
        if (orders.isEmpty()) item { EmptyState(Icons.Rounded.Assignment, "Nessun ordine", "Usa + per inserire un ordine in processo") }
        items(orders, key = { it.id }) { item ->
            val productions = vm.productionsForOrder(item.id)
            val fabrics = orderFabricRows(vm, item)
            val overdue = item.status !in finished && item.dueDate.isNotBlank() && runCatching { LocalDate.parse(item.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false)
            val accent = when {
                item.status in finished -> p.success
                item.status == WorkStatus.BLOCKED || overdue -> p.danger
                item.status in listOf(WorkStatus.WAITING_CLIENT, WorkStatus.WAITING_MATERIAL, WorkStatus.ON_HOLD) -> p.secondary
                item.priority == Priority.HIGH -> p.danger
                else -> p.primary
            }
            SpectroCard(p = p, onClick = { onOpen(item) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).height(62.dp).background(accent, RoundedCornerShape(99.dp)))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(item.title.ifBlank { item.orderCode }, fontWeight = FontWeight.Black, fontSize = 18.sp, color = p.text, maxLines = 1)
                        Text("${item.client} • ${workStatusLabel(item.status)}", color = p.textMuted, fontSize = 12.sp, maxLines = 1)
                        if (item.orderCode.isNotBlank()) Text(item.orderCode, color = p.textMuted, fontSize = 10.sp)
                    }
                    AssistChip(onClick = {}, label = { Text(workStatusLabel(item.status).uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Black) })
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
                if (item.description.isNotBlank()) {
                    Spacer(Modifier.height(7.dp)); Text(item.description, color = p.textMuted, fontSize = 11.sp, maxLines = 2)
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Produzioni", productions.size.toString(), modifier = Modifier.weight(1f))
                    Metric("Consegna", item.dueDate.ifBlank { "-" }, valueColor = if (overdue) p.danger else p.text, modifier = Modifier.weight(1f))
                    Metric("Timeline", vm.timelineForOrder(item.id).size.toString(), modifier = Modifier.weight(1f))
                }
                if (overdue) { Spacer(Modifier.height(8.dp)); Text("CONSEGNA SCADUTA", color = p.danger, fontWeight = FontWeight.Black, fontSize = 11.sp) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (vm.can(AppPermission.ORDERS_MANAGE)) OutlinedButton(onClick = { onEdit(item) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(5.dp)); Text("Modifica") }
                    if (item.status !in finished && vm.can(AppPermission.PRODUCTION_MANAGE)) {
                        Button(onClick = { onStartProduction(item) }, modifier = Modifier.weight(1f)) {
                            Icon(if (productions.isEmpty()) Icons.Rounded.PlayArrow else Icons.Rounded.Add, null)
                            Spacer(Modifier.width(5.dp)); Text(if (productions.isEmpty()) "Produzione" else "+ Produz.")
                        }
                        if (productions.isNotEmpty() && vm.can(AppPermission.ORDERS_MANAGE)) {
                            IconButton(onClick = { vm.completeWorkOrder(item.id) }) { Icon(Icons.Rounded.TaskAlt, "Completa ed evadi", tint = p.success) }
                        }
                    } else {
                        OutlinedButton(onClick = { onOpen(item) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(5.dp)); Text("Storico") }
                    }
                    if (vm.can(AppPermission.ORDERS_MANAGE)) IconButton(onClick = { deleteTarget = item }) { Icon(Icons.Rounded.Delete, "Elimina ordine", tint = p.danger) }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminare definitivamente l'ordine?") },
            text = { Text("${target.title.ifBlank { target.orderCode }} verrà eliminato insieme allo storico collegato. L'eventuale consumo di tessuto verrà ripristinato automaticamente.") },
            confirmButton = { TextButton(onClick = { vm.deleteWorkOrder(target.id); deleteTarget = null }) { Text("Elimina", color = p.danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annulla") } }
        )
    }
}
