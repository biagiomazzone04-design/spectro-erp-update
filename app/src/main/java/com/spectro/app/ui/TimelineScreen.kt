package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.spectro.app.util.AttachmentStore
import com.spectro.app.util.ReportExporter
import java.time.LocalDate
import java.time.LocalTime

@Composable
fun OrderTimelineScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    order: WorkOrder,
    onBack: () -> Unit,
    onEdit: (WorkOrder) -> Unit,
    onStartProduction: (WorkOrder) -> Unit
) {
    var addOpen by remember { mutableStateOf(false) }
    val current = vm.state.workOrders.firstOrNull { it.id == order.id } ?: order
    val timeline = vm.timelineForOrder(current.id)
    val context = LocalContext.current

    LazyColumn(Modifier.fillMaxSize().background(p.background), contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Indietro") }
                Column(Modifier.weight(1f)) {
                    Text(current.orderCode.ifBlank { current.title }, fontSize = 24.sp, fontWeight = FontWeight.Black)
                    Text("${current.client} • ${workStatusLabel(current.status)}", color = p.textMuted)
                }
                IconButton(onClick = { onEdit(current) }) { Icon(Icons.Rounded.Edit, "Modifica") }
            }
        }
        item {
            SpectroCard(p = p) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Ordine", meters(current.orderedMeters), modifier = Modifier.weight(1f))
                    Metric("Consegna", current.dueDate.ifBlank { "-" }, modifier = Modifier.weight(1f))
                    Metric("Disegno", current.designCode.ifBlank { "-" }, modifier = Modifier.weight(1f))
                }
                if (current.description.isNotBlank()) { Spacer(Modifier.height(10.dp)); Text(current.description) }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { ReportExporter.shareWorkOrder(context, current) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(5.dp)); Text("Ordine") }
                    OutlinedButton(onClick = { ReportExporter.shareOrderTimeline(context, current, timeline) }, modifier = Modifier.weight(1f)) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(5.dp)); Text("Storico") }
                }
                if (current.productionRecordId == null && current.status !in listOf(WorkStatus.CANCELLED, WorkStatus.DELIVERED, WorkStatus.CLOSED)) {
                    Spacer(Modifier.height(8.dp)); Button(onClick = { onStartProduction(current) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Crea ordine produttivo da questo ordine") }
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Timeline ordine", fontSize = 20.sp, fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                FilledTonalButton(onClick = { addOpen = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("Aggiorna") }
            }
        }
        if (timeline.isEmpty()) item { EmptyState(Icons.Rounded.History, "Timeline vuota", "Aggiungi note, criticità, approvazioni o ritardi") }
        items(timeline, key = { it.id }) { event -> TimelineCard(vm, p, event) }
    }

    if (addOpen) AddTimelineEventDialog(vm, p, current) { addOpen = false }
}

@Composable
private fun TimelineCard(vm: SpectroViewModel, p: SpectroPalette, event: OrderTimelineEvent) {
    val accent = when (event.severity) {
        TimelineSeverity.INFO -> p.primary
        TimelineSeverity.LOW -> p.success
        TimelineSeverity.MEDIUM -> p.warning
        TimelineSeverity.HIGH, TimelineSeverity.BLOCKING -> p.danger
    }
    SpectroCard(p = p) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${event.date}${if (event.time.isNotBlank()) " • ${event.time}" else ""}", fontSize = 11.sp, color = p.textMuted, fontWeight = FontWeight.Bold)
                Text(event.title, fontWeight = FontWeight.Black, fontSize = 17.sp)
            }
            AssistChip(onClick = {}, label = { Text(timelineCategoryLabel(event.category), fontSize = 9.sp) })
        }
        Spacer(Modifier.height(8.dp))
        Text(event.description)
        Spacer(Modifier.height(8.dp))
        Text("Responsabilità: ${timelineResponsibilityLabel(event.responsibility)} • Gravità: ${timelineSeverityLabel(event.severity)}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (event.oldDueDate.isNotBlank() || event.newDueDate.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Consegna: ${event.oldDueDate.ifBlank { "-" }} → ${event.newDueDate.ifBlank { "-" }}", fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        if (event.images.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(event.images) }
        if (event.status == TimelineStatus.OPEN && event.severity != TimelineSeverity.INFO) {
            Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { vm.resolveTimelineEvent(event.id, "Risolta da timeline ordine") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Segna come risolta") }
        }
        if (event.status == TimelineStatus.RESOLVED && event.resolutionNote.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Risolta: ${event.resolutionNote}", color = p.success, fontSize = 12.sp) }
    }
}

@Composable
private fun AddTimelineEventDialog(vm: SpectroViewModel, p: SpectroPalette, order: WorkOrder, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var category by remember { mutableStateOf(TimelineCategory.NOTE) }
    var responsibility by remember { mutableStateOf(TimelineResponsibility.NONE) }
    var severity by remember { mutableStateOf(TimelineSeverity.INFO) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var newDue by remember { mutableStateOf("") }
    var images by remember { mutableStateOf(emptyList<String>()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiorna ordine", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WheelDateField("Data", date, { date = it }) }
                item { SimpleSelectField("Categoria", timelineCategoryLabel(category), TimelineCategory.entries.map { it to timelineCategoryLabel(it) }) { category = it } }
                item { SimpleSelectField("Responsabilità", timelineResponsibilityLabel(responsibility), TimelineResponsibility.entries.map { it to timelineResponsibilityLabel(it) }) { responsibility = it } }
                item { SimpleSelectField("Gravità", timelineSeverityLabel(severity), TimelineSeverity.entries.map { it to timelineSeverityLabel(it) }) { severity = it } }
                item { SpectroTextField("Titolo", title, { title = it }, placeholder = "Es. File DXF differente") }
                item { SpectroTextField("Descrizione dettagliata", description, { description = it }, singleLine = false) }
                item { WheelDateField("Nuova consegna (se varia)", newDue, { newDue = it }, allowEmpty = true) }
                item { OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Allega immagini") } }
                if (images.isNotEmpty()) item { ImageStrip(images) { i -> images = images.filterIndexed { idx, _ -> idx != i } } }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (title.isNotBlank() && (description.isNotBlank() || images.isNotEmpty())) {
                    vm.saveTimelineEvent(OrderTimelineEvent(workOrderId = order.id, clientId = order.clientId, clientName = order.client, orderRef = order.orderCode.ifBlank { order.title }, date = date, time = LocalTime.now().withSecond(0).withNano(0).toString(), category = category, responsibility = responsibility, severity = severity, status = if (severity == TimelineSeverity.INFO) TimelineStatus.RESOLVED else TimelineStatus.OPEN, title = title.trim(), description = description.trim(), oldDueDate = if (newDue.isNotBlank() && newDue != order.dueDate) order.dueDate else "", newDueDate = if (newDue != order.dueDate) newDue else "", images = images))
                    if (newDue.isNotBlank() && newDue != order.dueDate) vm.saveWorkOrder(order.copy(dueDate = newDue))
                    onDismiss()
                }
            }) { Text("Aggiungi") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
fun IssuesCenterScreen(vm: SpectroViewModel, p: SpectroPalette, onOpenOrder: (WorkOrder) -> Unit) {
    var filter by remember { mutableStateOf("OPEN") }
    val all = vm.state.timelineEvents.filter { it.severity != TimelineSeverity.INFO }
    val events = all.filter { when (filter) { "OPEN" -> it.status == TimelineStatus.OPEN; "CLIENT" -> it.responsibility == TimelineResponsibility.CLIENT; "INTERNAL" -> it.responsibility == TimelineResponsibility.INTERNAL; "BLOCKING" -> it.severity == TimelineSeverity.BLOCKING; else -> true } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("Centro criticità", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Problemi, ritardi e responsabilità collegati agli ordini.", color = p.textMuted); Spacer(Modifier.height(12.dp)); SegmentedChoice(listOf("OPEN" to "APERTE", "CLIENT" to "CLIENTE", "INTERNAL" to "INTERNE", "ALL" to "TUTTE"), filter, p) { filter = it } }
        item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Aperte", all.count { it.status == TimelineStatus.OPEN }.toString(), modifier = Modifier.weight(1f)); Metric("Bloccanti", all.count { it.status == TimelineStatus.OPEN && it.severity == TimelineSeverity.BLOCKING }.toString(), valueColor = p.danger, modifier = Modifier.weight(1f)); Metric("Cliente", all.count { it.status == TimelineStatus.OPEN && it.responsibility == TimelineResponsibility.CLIENT }.toString(), modifier = Modifier.weight(1f)) } }
        if (events.isEmpty()) item { EmptyState(Icons.Rounded.Verified, "Nessuna criticità", "Non risultano criticità per il filtro selezionato") }
        items(events, key = { it.id }) { e ->
            val order = vm.state.workOrders.firstOrNull { it.id == e.workOrderId }
            SpectroCard(p = p, onClick = { if (order != null) onOpenOrder(order) }) {
                Text(e.title, fontWeight = FontWeight.Black, fontSize = 17.sp)
                Text("${e.clientName} • ${e.orderRef} • ${e.date}", color = p.textMuted, fontSize = 12.sp)
                Spacer(Modifier.height(7.dp)); Text(e.description, maxLines = 3)
                Spacer(Modifier.height(7.dp)); Text("${timelineResponsibilityLabel(e.responsibility)} • ${timelineSeverityLabel(e.severity)}", color = if (e.severity in listOf(TimelineSeverity.HIGH, TimelineSeverity.BLOCKING)) p.danger else p.warning, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }
    }
}
