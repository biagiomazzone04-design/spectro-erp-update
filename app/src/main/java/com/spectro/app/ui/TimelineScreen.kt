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
import com.spectro.app.auth.AppPermission
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.AttachmentStore
import com.spectro.app.util.DraftStore
import com.spectro.app.util.TimelineDraft
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
    onStartProduction: (WorkOrder) -> Unit,
    onComplete: (WorkOrder) -> Unit,
    onReopen: (WorkOrder) -> Unit
) {
    var addOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val current = vm.state.workOrders.firstOrNull { it.id == order.id } ?: order
    val timeline = vm.timelineForOrder(current.id)
    val productions = vm.productionsForOrder(current.id)
    val fabricRows = orderFabricRows(vm, current)
    val context = LocalContext.current

    LazyColumn(
        Modifier.fillMaxSize().background(p.background),
        contentPadding = PaddingValues(20.dp, 12.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Indietro", tint = p.text) }
                Column(Modifier.weight(1f)) {
                    Text(current.title.ifBlank { current.orderCode }, fontSize = 24.sp, fontWeight = FontWeight.Black, color = p.text)
                    Text("${current.client} • ${workStatusLabel(current.status)}", color = p.textMuted)
                    if (current.orderCode.isNotBlank()) Text(current.orderCode, color = p.textMuted, fontSize = 10.sp)
                }
                if (vm.can(AppPermission.ORDERS_MANAGE)) {
                    IconButton(onClick = { onEdit(current) }) { Icon(Icons.Rounded.Edit, "Modifica", tint = p.primary) }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Elimina ordine", tint = p.danger) }
                }
            }
        }

        item {
            SpectroCard(p = p) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("Ordine", meters(current.orderedMeters), modifier = Modifier.weight(1f))
                    Metric("Consegna", current.dueDate.ifBlank { "-" }, modifier = Modifier.weight(1f))
                    Metric("Produzioni", productions.size.toString(), valueColor = if (productions.isNotEmpty()) p.primary else p.textMuted, modifier = Modifier.weight(1f))
                }
                if (current.materialReceivedDate.isNotBlank() || current.materialReceiptNotes.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("CONSEGNA MATERIALE", color = p.textMuted, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    Text(current.materialReceivedDate.ifBlank { "Data non indicata" }, color = p.primary, fontWeight = FontWeight.Black)
                    if (current.materialReceiptNotes.isNotBlank()) Text(current.materialReceiptNotes, color = p.textMuted, fontSize = 12.sp)
                }
                if (fabricRows.isNotEmpty()) {
                    Spacer(Modifier.height(14.dp))
                    Text("TESSUTI / METRAGGI", color = p.textMuted, fontWeight = FontWeight.Black, fontSize = 10.sp)
                    Spacer(Modifier.height(7.dp))
                    fabricRows.forEach { row ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(row.fabricName.uppercase(), color = p.text, fontWeight = FontWeight.Black, fontSize = 16.sp)
                            Text(if (row.meters > 0) meters(row.meters) else "-", color = p.primary, fontWeight = FontWeight.Black, fontSize = 16.sp)
                        }
                    }
                }
                if (current.designCode.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text("DISEGNO", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(current.designCode, color = p.text, fontWeight = FontWeight.Bold)
                }
                if (current.description.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text("NOTE ORDINE", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Text(current.description, color = p.text, fontSize = 14.sp)
                }
                if (current.images.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("IMMAGINI ORDINE", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    Spacer(Modifier.height(6.dp))
                    ImageStrip(current.images)
                }
                if (vm.can(AppPermission.REPORTS_EXPORT)) {
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { ReportExporter.shareWorkOrder(context, current) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(5.dp)); Text("Ordine")
                        }
                        OutlinedButton(onClick = { ReportExporter.shareOrderTimeline(context, current, timeline, productions) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.History, null); Spacer(Modifier.width(5.dp)); Text("Dossier")
                        }
                    }
                }

                when {
                    current.status.isArchivedOrder() -> {
                        Spacer(Modifier.height(10.dp))
                        SpectroCard(p = p) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Rounded.Inventory, null, tint = p.success)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("ORDINE ARCHIVIATO", color = p.success, fontWeight = FontWeight.Black)
                                    Text("Evaso ${current.effectiveDeliveryDate.ifBlank { "-" }} • tutti i dati restano consultabili", color = p.textMuted, fontSize = 10.sp)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            if (vm.can(AppPermission.ORDERS_MANAGE)) OutlinedButton(onClick = { onReopen(current) }, Modifier.fillMaxWidth()) {
                                Icon(Icons.Rounded.RestartAlt, null); Spacer(Modifier.width(6.dp)); Text("Riapri ordine")
                            }
                        }
                    }
                    productions.isEmpty() && vm.can(AppPermission.PRODUCTION_MANAGE) -> {
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { onStartProduction(current) }, Modifier.fillMaxWidth()) {
                            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Crea prima produzione")
                        }
                    }
                    else -> {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (vm.can(AppPermission.PRODUCTION_MANAGE)) OutlinedButton(onClick = { onStartProduction(current) }, Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("Produzione")
                            }
                            if (vm.can(AppPermission.ORDERS_MANAGE)) Button(
                                onClick = { onComplete(current) }, modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(containerColor = p.success, contentColor = androidx.compose.ui.graphics.Color.Black)
                            ) {
                                Icon(Icons.Rounded.TaskAlt, null); Spacer(Modifier.width(5.dp)); Text("Evadi")
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("Puoi aggiungere più produzioni allo stesso ordine. Quando lo evadi sparisce dagli attivi ma resta nello storico cliente con note, immagini e criticità.", color = p.textMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        if (productions.isNotEmpty()) {
            item {
                val totalPaper = productions.sumOf { it.printedPaperMeters }
                val totalFabric = productions.sumOf { it.fabricUsedMeters }
                val totalRevenue = productions.sumOf { it.grossRevenue }
                SpectroCard(p = p) {
                    Text("RIEPILOGO PRODUZIONE", color = p.primary, fontWeight = FontWeight.Black, fontSize = 13.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Lavorazioni", productions.size.toString(), modifier = Modifier.weight(1f))
                        Metric("Carta", meters(totalPaper), modifier = Modifier.weight(1f))
                        Metric("Tessuto", meters(totalFabric), modifier = Modifier.weight(1f))
                    }
                    if (totalRevenue > 0 && vm.can(AppPermission.FINANCE_VIEW)) {
                        Spacer(Modifier.height(8.dp)); Metric("Fatturato produzioni", euro(totalRevenue), p.success)
                    }
                }
            }
            item { SectionTitle("Produzioni collegate") }
            items(productions, key = { "order-prod-${it.id}" }) { prod ->
                SpectroCard(p = p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.PrecisionManufacturing, null, tint = p.primary)
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(prod.date, color = p.text, fontWeight = FontWeight.Black, fontSize = 15.sp)
                            Text(prod.orderRef.ifBlank { current.title }, color = p.textMuted, fontSize = 11.sp)
                        }
                        Text(meters(if (prod.fabricUsedMeters > 0) prod.fabricUsedMeters else prod.printedPaperMeters), color = p.primary, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(prod.fabricDisplayName.ifBlank { "TESSUTO" }.uppercase(), color = p.text, fontWeight = FontWeight.Black, fontSize = 16.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Carta", meters(prod.printedPaperMeters), modifier = Modifier.weight(1f))
                        Metric("Tessuto", meters(prod.fabricUsedMeters), modifier = Modifier.weight(1f))
                        if (vm.can(AppPermission.FINANCE_VIEW)) Metric("Utile", euro(prod.grossProfit), if (prod.grossProfit >= 0) p.success else p.danger, Modifier.weight(1f))
                    }
                    if (prod.technicalNotes.isNotBlank()) {
                        Spacer(Modifier.height(10.dp))
                        Text("NOTA PRODUZIONE", color = p.textMuted, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        Text(prod.technicalNotes, color = p.text)
                    }
                    if (prod.images.isNotEmpty()) {
                        Spacer(Modifier.height(10.dp)); ImageStrip(prod.images)
                    }
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("Timeline ordine", fontSize = 20.sp, fontWeight = FontWeight.Black, color = p.text, modifier = Modifier.weight(1f))
                if (vm.can(AppPermission.ISSUES_MANAGE)) FilledTonalButton(onClick = { addOpen = true }) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(5.dp)); Text("Aggiorna") }
            }
        }
        if (timeline.isEmpty()) item { EmptyState(Icons.Rounded.History, "Timeline vuota", "Aggiungi note, criticità, approvazioni o ritardi") }
        items(timeline, key = { it.id }) { event -> TimelineCard(vm, p, event) }
    }

    if (addOpen) AddTimelineEventDialog(vm, p, current) { addOpen = false }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminare definitivamente l'ordine?") },
            text = { Text("Verranno eliminati ordine, timeline e tutte le produzioni collegate. Usa questa funzione solo per errori: per togliere un ordine dagli attivi usa Completa/Evadi.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.deleteWorkOrder(current.id)
                    onBack()
                }) { Text("Elimina", color = p.danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
        )
    }
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
                Text(event.title, fontWeight = FontWeight.Black, fontSize = 17.sp, color = p.text)
            }
            AssistChip(onClick = {}, label = { Text(timelineCategoryLabel(event.category), fontSize = 9.sp) })
        }
        Spacer(Modifier.height(8.dp))
        Text(localizeWorkStatusTokens(event.description), color = p.text)
        Spacer(Modifier.height(8.dp))
        Text("Responsabilità: ${timelineResponsibilityLabel(event.responsibility)} • Gravità: ${timelineSeverityLabel(event.severity)}", color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        if (event.oldDueDate.isNotBlank() || event.newDueDate.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Consegna: ${event.oldDueDate.ifBlank { "-" }} → ${event.newDueDate.ifBlank { "-" }}", color = p.text, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
        if (event.images.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(event.images) }
        if (event.status == TimelineStatus.OPEN && event.severity != TimelineSeverity.INFO && vm.can(AppPermission.ISSUES_MANAGE)) {
            Spacer(Modifier.height(10.dp)); OutlinedButton(onClick = { vm.resolveTimelineEvent(event.id, "Risolta da timeline ordine") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.CheckCircle, null); Spacer(Modifier.width(6.dp)); Text("Segna come risolta") }
        }
        if (event.status == TimelineStatus.RESOLVED && event.resolutionNote.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text("Risolta: ${localizeWorkStatusTokens(event.resolutionNote)}", color = p.success, fontSize = 12.sp) }
    }
}

@Composable
private fun AddTimelineEventDialog(vm: SpectroViewModel, p: SpectroPalette, order: WorkOrder, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val draft = remember(order.id) { DraftStore.loadTimeline(context, order.id) }
    var date by remember(order.id) { mutableStateOf(draft?.date?.takeIf { it.isNotBlank() } ?: LocalDate.now().toString()) }
    var category by remember(order.id) { mutableStateOf(draft?.category?.let { runCatching { TimelineCategory.valueOf(it) }.getOrNull() } ?: TimelineCategory.NOTE) }
    var responsibility by remember(order.id) { mutableStateOf(draft?.responsibility?.let { runCatching { TimelineResponsibility.valueOf(it) }.getOrNull() } ?: TimelineResponsibility.NONE) }
    var severity by remember(order.id) { mutableStateOf(draft?.severity?.let { runCatching { TimelineSeverity.valueOf(it) }.getOrNull() } ?: TimelineSeverity.INFO) }
    var title by remember(order.id) { mutableStateOf(draft?.title.orEmpty()) }
    var description by remember(order.id) { mutableStateOf(draft?.description.orEmpty()) }
    var newDue by remember(order.id) { mutableStateOf(draft?.newDue.orEmpty()) }
    var images by remember(order.id) { mutableStateOf(draft?.images ?: emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }

    LaunchedEffect(date, category, responsibility, severity, title, description, newDue, images) {
        DraftStore.saveTimeline(context, order.id, TimelineDraft(date, category.name, responsibility.name, severity.name, title, description, newDue, images))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aggiorna ordine", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Bozza automatica attiva • testo, immagini e nuova consegna restano salvati se chiudi la finestra o l'app.", color = p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
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
                    vm.saveTimelineEvent(OrderTimelineEvent(workOrderId = order.id, clientId = order.clientId, clientName = order.client, orderRef = order.title.ifBlank { order.orderCode }, date = date, time = LocalTime.now().withSecond(0).withNano(0).toString(), category = category, responsibility = responsibility, severity = severity, status = if (severity == TimelineSeverity.INFO) TimelineStatus.RESOLVED else TimelineStatus.OPEN, title = title.trim(), description = description.trim(), oldDueDate = if (newDue.isNotBlank() && newDue != order.dueDate) order.dueDate else "", newDueDate = if (newDue != order.dueDate) newDue else "", images = images))
                    if (newDue.isNotBlank() && newDue != order.dueDate) vm.saveWorkOrder(order.copy(dueDate = newDue))
                    DraftStore.clearTimeline(context, order.id)
                    onDismiss()
                }
            }) { Text("Aggiungi") }
        },
        dismissButton = { Row { TextButton(onClick = { DraftStore.clearTimeline(context, order.id); onDismiss() }) { Text("Scarta bozza", color = p.danger) }; TextButton(onClick = onDismiss) { Text("Chiudi") } } }
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
