@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.AttachmentStore
import com.spectro.app.util.ReportExporter
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class ClientHubTab { CLIENTS, ISSUES, PAYMENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClientsScreen(vm: SpectroViewModel, p: SpectroPalette, onOpenOrder: (WorkOrder) -> Unit = {}) {
    var tab by remember { mutableStateOf(ClientHubTab.CLIENTS) }
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
            Text("Clienti & Controllo", fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("Rubrica, prove delle criticità e scadenze in un unico posto.", color = p.textMuted)
            Spacer(Modifier.height(14.dp))
            SegmentedChoice(listOf("CLIENTS" to "CLIENTI", "ISSUES" to "CRITICITÀ", "PAYMENTS" to "SCADENZE"), tab.name, p) { tab = ClientHubTab.valueOf(it) }
        }
        when (tab) {
            ClientHubTab.CLIENTS -> ClientDirectoryPane(vm, p, Modifier.weight(1f), onOpenOrder)
            ClientHubTab.ISSUES -> IssuesPane(vm, p, Modifier.weight(1f))
            ClientHubTab.PAYMENTS -> PaymentsPane(vm, p, Modifier.weight(1f))
        }
    }
}

@Composable
private fun ClientDirectoryPane(vm: SpectroViewModel, p: SpectroPalette, modifier: Modifier = Modifier, onOpenOrder: (WorkOrder) -> Unit = {}) {
    var editing by remember { mutableStateOf<Client?>(null) }
    var selected by remember { mutableStateOf<Client?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var issueClient by remember { mutableStateOf<Client?>(null) }
    var paymentClient by remember { mutableStateOf<Client?>(null) }

    fun openEditor(c: Client?) { editing = c; name = c?.name.orEmpty(); price = c?.defaultPrintPrice?.takeIf { it != 0.0 }?.toString().orEmpty(); showForm = true }

    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Rubrica clienti", fontWeight = FontWeight.Black, fontSize = 18.sp)
                FilledIconButton(onClick = { if (showForm) showForm = false else openEditor(null) }) { Icon(if (showForm) Icons.Rounded.Close else Icons.Rounded.PersonAdd, null) }
            }
        }
        item {
            AnimatedVisibility(showForm) {
                SpectroCard(p = p) {
                    Text(if (editing == null) "Nuovo cliente" else "Modifica cliente", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp)); SpectroTextField("Nome azienda / cliente", name, { name = it })
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Prezzo stampa base (€/mt)", price, { price = it }, numeric = true)
                    Spacer(Modifier.height(16.dp)); GradientButton("Salva cliente", p) {
                        if (name.isNotBlank()) { vm.saveClient(Client(editing?.id ?: 0L, name.trim(), price.toDoubleSafe())); showForm = false }
                    }
                    if (editing != null) TextButton(onClick = { vm.deleteClient(editing!!.id); showForm = false }, modifier = Modifier.align(Alignment.End)) { Text("Elimina", color = p.danger) }
                }
            }
        }
        if (vm.state.clients.isEmpty()) item { EmptyState(Icons.Rounded.People, "Rubrica vuota", "Aggiungi i clienti per listino, storico e prove") }
        items(vm.state.clients, key = { it.id }) { c ->
            val records = vm.state.production.filter { it.type == RecordType.PRODUCTION && (it.clientId == c.id || it.clientName.equals(c.name, true)) }
            val workOrders = vm.state.workOrders.filter { it.clientId == c.id || it.client.equals(c.name, true) }
            val issues = vm.state.clientIssues.filter { it.clientId == c.id || it.clientName.equals(c.name, true) }
            val orderCrits = vm.state.timelineEvents.filter { (it.clientId == c.id || it.clientName.equals(c.name, true)) && it.severity != TimelineSeverity.INFO }
            val open = issues.count { it.status !in listOf(IssueStatus.RESOLVED, IssueStatus.CLOSED) } + orderCrits.count { it.status == TimelineStatus.OPEN }
            val overdue = vm.state.payments.count { !it.paid && (it.clientId == c.id || it.clientName.equals(c.name, true)) && runCatching { LocalDate.parse(it.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false) }
            SpectroCard(p = p, onClick = { selected = c }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text(c.name, fontWeight = FontWeight.Black, fontSize = 18.sp); Text("Listino ${euro(c.defaultPrintPrice)}/mt", color = p.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp) }
                    if (open > 0) Badge(containerColor = p.warning) { Text(open.toString()) }
                    IconButton(onClick = { openEditor(c) }) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }
                }
                Spacer(Modifier.height(10.dp)); Row {
                    Metric("Ordini", workOrders.size.toString(), modifier = Modifier.weight(1f))
                    Metric("Criticità aperte", open.toString(), if (open > 0) p.warning else p.success, Modifier.weight(1f))
                    Metric("Scadute", overdue.toString(), if (overdue > 0) p.danger else p.textMuted, Modifier.weight(1f))
                }
            }
        }
    }

    selected?.let { c ->
        val records = vm.state.production.filter { it.type == RecordType.PRODUCTION && (it.clientId == c.id || it.clientName.equals(c.name, true)) }
        val issues = vm.state.clientIssues.filter { it.clientId == c.id || it.clientName.equals(c.name, true) }
        val orderCrits = vm.state.timelineEvents.filter { (it.clientId == c.id || it.clientName.equals(c.name, true)) && it.severity != TimelineSeverity.INFO }.sortedWith(compareByDescending<OrderTimelineEvent> { it.date }.thenByDescending { it.time })
        val clientOrders = vm.state.workOrders.filter { it.clientId == c.id || it.client.equals(c.name, true) }
        val activeClientOrders = clientOrders.filter { !it.status.isFinishedOrder() }.sortedByDescending { it.receivedDate }
        val historicClientOrders = clientOrders.filter { it.status.isArchivedOrder() }.sortedByDescending { it.effectiveDeliveryDate.ifBlank { it.receivedDate } }
        val clientPayments = vm.state.payments.filter { it.clientId == c.id || it.clientName.equals(c.name, true) }
        ModalBottomSheet(onDismissRequest = { selected = null }, containerColor = p.background) {
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 40.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(c.name, fontSize = 26.sp, fontWeight = FontWeight.Black); Text("Profilo operativo cliente", color = p.textMuted) }
                        IconButton(onClick = { selected = null; openEditor(c) }) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }
                    }
                }
                item {
                    SpectroCard(p = p) {
                        Row { Metric("Ordini", clientOrders.size.toString(), modifier = Modifier.weight(1f)); Metric("Fatturato", euro(records.sumOf { it.grossRevenue }), p.success, Modifier.weight(1f)) }
                        Spacer(Modifier.height(12.dp)); Row { Metric("Criticità", (issues.size + orderCrits.size).toString(), modifier = Modifier.weight(1f)); Metric("Ritardo documentato", "${issues.sumOf { it.delayDays }} gg", if (issues.sumOf { it.delayDays } > 0) p.warning else p.textMuted, Modifier.weight(1f)) }
                        Spacer(Modifier.height(12.dp)); Metric("Da incassare", euro(clientPayments.filter { !it.paid }.sumOf { it.amount }), if (clientPayments.any { !it.paid }) p.warning else p.success)
                    }
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(onClick = { selected = null; issueClient = c }, Modifier.weight(1f)) { Icon(Icons.Rounded.ReportProblem, null); Spacer(Modifier.width(6.dp)); Text("Criticità") }
                        OutlinedButton(onClick = { selected = null; paymentClient = c }, Modifier.weight(1f)) { Icon(Icons.Rounded.Payments, null); Spacer(Modifier.width(6.dp)); Text("Scadenza") }
                    }
                }
                item { SectionTitle("Ordini attivi") }
                if (activeClientOrders.isEmpty()) item { Text("Nessun ordine attivo per questo cliente.", color = p.textMuted) }
                items(activeClientOrders, key = { "active-order-${it.id}" }) { order ->
                    SpectroCard(p = p, onClick = { selected = null; onOpenOrder(order) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(order.title.ifBlank { order.orderCode }, fontWeight = FontWeight.Black, color = p.text)
                                Text("${workStatusLabel(order.status)} • ${order.receivedDate}${order.dueDate.takeIf { it.isNotBlank() }?.let { " • consegna $it" }.orEmpty()}", color = p.textMuted, fontSize = 11.sp)
                            }
                            Icon(Icons.Rounded.ChevronRight, null, tint = p.primary)
                        }
                    }
                }
                item { SectionTitle("Storico ordini evasi") }
                if (historicClientOrders.isEmpty()) item { Text("Nessun ordine completato nello storico.", color = p.textMuted) }
                items(historicClientOrders, key = { "historic-order-${it.id}" }) { order ->
                    val critCount = orderCrits.count { it.workOrderId == order.id }
                    val prod = order.productionRecordId?.let { id -> records.firstOrNull { it.id == id } }
                    SpectroCard(p = p, onClick = { selected = null; onOpenOrder(order) }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(order.title.ifBlank { order.orderCode }, fontWeight = FontWeight.Black, color = p.text)
                                Text("Evaso ${order.effectiveDeliveryDate.ifBlank { "-" }} • ${workStatusLabel(order.status)}", color = p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (order.orderCode.isNotBlank()) Text(order.orderCode, color = p.textMuted, fontSize = 10.sp)
                            }
                            Icon(Icons.Rounded.History, null, tint = p.success)
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Criticità", critCount.toString(), if (critCount > 0) p.warning else p.success, Modifier.weight(1f))
                            Metric("Carta", prod?.let { meters(it.printedPaperMeters) } ?: "-", modifier = Modifier.weight(1f))
                            Metric("Fatturato", prod?.let { euro(it.grossRevenue) } ?: "-", valueColor = p.success, modifier = Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(5.dp))
                        Text("Tocca per aprire ordine, produzione e timeline completa", color = p.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
                item { SectionTitle("Criticità degli ordini") }
                if (orderCrits.isEmpty()) item { Text("Nessuna criticità collegata agli ordini.", color = p.textMuted) }
                items(orderCrits, key = { "tl${it.id}" }) { event ->
                    val linked = clientOrders.firstOrNull { it.id == event.workOrderId }
                    SpectroCard(p = p, onClick = { if (linked != null) { selected = null; onOpenOrder(linked) } }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(event.title, fontWeight = FontWeight.Black, color = p.text)
                                Text("${event.date}${if (event.orderRef.isNotBlank()) " • ${event.orderRef}" else ""}", color = p.textMuted, fontSize = 11.sp)
                            }
                            AssistChip(onClick = {}, label = { Text(timelineSeverityLabel(event.severity), fontSize = 9.sp) })
                        }
                        if (event.description.isNotBlank()) { Spacer(Modifier.height(6.dp)); Text(localizeWorkStatusTokens(event.description), color = p.text, fontSize = 12.sp, maxLines = 4) }
                        Spacer(Modifier.height(6.dp)); Text("${timelineResponsibilityLabel(event.responsibility)} • ${if (event.status == TimelineStatus.OPEN) "Aperta" else "Risolta"}", color = if (event.status == TimelineStatus.OPEN) p.warning else p.success, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        if (linked != null) { Spacer(Modifier.height(5.dp)); Text("Tocca per aprire l'ordine e lo storico completo", color = p.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold) }
                    }
                }
                if (issues.isNotEmpty()) {
                    item { SectionTitle("Criticità CRM precedenti") }
                    items(issues.take(6), key = { "ci${it.id}" }) { issue ->
                        ListItem(headlineContent = { Text(issue.title, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${issue.openedDate} • ${issueCategoryLabel(issue.category)}") }, trailingContent = { Text("${issue.delayDays} gg", color = if (issue.delayDays > 0) p.warning else p.textMuted, fontWeight = FontWeight.Black) }, colors = ListItemDefaults.colors(containerColor = p.background))
                    }
                }
                item { SectionTitle("Ultime produzioni") }
                if (records.isEmpty()) item { Text("Nessuna produzione registrata.", color = p.textMuted) }
                items(records.take(5), key = { "pr${it.id}" }) { r ->
                    ListItem(headlineContent = { Text(r.orderRef.ifBlank { "Produzione ${r.date}" }, fontWeight = FontWeight.Bold) }, supportingContent = { Text("${r.date} • ${meters(r.printedPaperMeters)} carta") }, trailingContent = { Text(euro(r.grossRevenue), color = p.success, fontWeight = FontWeight.Black) }, colors = ListItemDefaults.colors(containerColor = p.background))
                }
            }
        }
    }
    if (issueClient != null) IssueEditorSheet(vm, p, null, issueClient) { issueClient = null }
    if (paymentClient != null) PaymentEditorSheet(vm, p, null, paymentClient) { paymentClient = null }
}

@Composable
private fun IssuesPane(vm: SpectroViewModel, p: SpectroPalette, modifier: Modifier = Modifier) {
    var query by remember { mutableStateOf("") }
    var onlyOpen by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<ClientIssue?>(null) }
    var detail by remember { mutableStateOf<ClientIssue?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val filtered = vm.state.clientIssues.filter { issue ->
        (!onlyOpen || issue.status !in listOf(IssueStatus.RESOLVED, IssueStatus.CLOSED)) && (query.isBlank() || issue.clientName.contains(query, true) || issue.orderRef.contains(query, true) || issue.title.contains(query, true) || issue.description.contains(query, true))
    }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, modifier = Modifier.weight(1f), placeholder = { Text("Cerca cliente, ordine, problema…") }, leadingIcon = { Icon(Icons.Rounded.Search, null) }, singleLine = true, shape = RoundedCornerShape(16.dp))
                Spacer(Modifier.width(10.dp)); FilledIconButton(onClick = { showNew = true }) { Icon(Icons.Rounded.Add, null) }
            }
            Spacer(Modifier.height(10.dp)); FilterChip(selected = onlyOpen, onClick = { onlyOpen = !onlyOpen }, label = { Text(if (onlyOpen) "Solo aperte" else "Tutte") }, leadingIcon = { Icon(Icons.Rounded.FilterAlt, null, Modifier.size(18.dp)) })
        }
        if (filtered.isEmpty()) item { EmptyState(Icons.Rounded.ReportProblem, "Nessuna criticità", "Registra problemi, prove e motivi dei ritardi") }
        items(filtered, key = { it.id }) { issue ->
            val accent = when (issue.severity) { IssueSeverity.CRITICAL -> p.danger; IssueSeverity.HIGH -> p.warning; IssueSeverity.MEDIUM -> p.primary; IssueSeverity.LOW -> p.success }
            SpectroCard(p = p, onClick = { detail = issue }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(5.dp).height(52.dp).background(accent, RoundedCornerShape(99.dp)))
                    Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(issue.title, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("${issue.clientName} • ${issue.openedDate}", color = p.textMuted, fontSize = 12.sp) }
                    AssistChip(onClick = {}, label = { Text(issueStatusLabel(issue.status), fontSize = 10.sp, fontWeight = FontWeight.Black) })
                }
                Spacer(Modifier.height(10.dp)); Text("${issueCategoryLabel(issue.category)} • Responsabilità: ${issueResponsibilityLabel(issue.responsibility)}", color = p.textMuted, fontSize = 12.sp)
                if (issue.delayDays > 0) { Spacer(Modifier.height(8.dp)); Text("Ritardo documentato: ${issue.delayDays} giorni", color = p.warning, fontWeight = FontWeight.Black) }
                if (issue.evidenceImages.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(issue.evidenceImages) }
                Spacer(Modifier.height(8.dp)); Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { IconButton(onClick = { editing = issue }) { Icon(Icons.Rounded.Edit, null, tint = p.primary) } }
            }
        }
    }
    if (showNew) IssueEditorSheet(vm, p, null, null) { showNew = false }
    editing?.let { IssueEditorSheet(vm, p, it, null) { editing = null } }
    detail?.let { IssueDetailSheet(vm, p, it, onDismiss = { detail = null }, onEdit = { editing = it; detail = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueEditorSheet(vm: SpectroViewModel, p: SpectroPalette, editing: ClientIssue?, initialClient: Client?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var clientId by remember(editing?.id, initialClient?.id) { mutableStateOf(editing?.clientId ?: initialClient?.id) }
    var clientName by remember(editing?.id, initialClient?.id) { mutableStateOf(editing?.clientName ?: initialClient?.name.orEmpty()) }
    var orderRef by remember(editing?.id) { mutableStateOf(editing?.orderRef.orEmpty()) }
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var category by remember(editing?.id) { mutableStateOf(editing?.category ?: IssueCategory.OTHER) }
    var responsibility by remember(editing?.id) { mutableStateOf(editing?.responsibility ?: IssueResponsibility.TO_VERIFY) }
    var severity by remember(editing?.id) { mutableStateOf(editing?.severity ?: IssueSeverity.MEDIUM) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: IssueStatus.OPEN) }
    var opened by remember(editing?.id) { mutableStateOf(editing?.openedDate ?: LocalDate.now().toString()) }
    var expected by remember(editing?.id) { mutableStateOf(editing?.expectedDeliveryDate.orEmpty()) }
    var revised by remember(editing?.id) { mutableStateOf(editing?.revisedDeliveryDate.orEmpty()) }
    var delay by remember(editing?.id) { mutableStateOf(editing?.delayDays?.takeIf { it > 0 }?.toString().orEmpty()) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var internalNotes by remember(editing?.id) { mutableStateOf(editing?.internalNotes.orEmpty()) }
    var images by remember(editing?.id) { mutableStateOf(editing?.evidenceImages ?: emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (editing == null) "Nuova criticità" else "Modifica criticità", fontSize = 26.sp, fontWeight = FontWeight.Black); Text("Documenta fatti, prove, responsabilità e impatto sulla consegna.", color = p.textMuted) }
            item { SimpleSelectField("Cliente", clientName.ifBlank { "Seleziona cliente" }, vm.state.clients.map { it to it.name }) { clientId = it.id; clientName = it.name } }
            item { SpectroTextField("Rif. ordine", orderRef, { orderRef = it }, placeholder = "Es. ORD-124 / Maglie estate") }
            item { SpectroTextField("Titolo problema", title, { title = it }, placeholder = "Es. File grafico ricevuto errato") }
            item { SimpleSelectField("Categoria", issueCategoryLabel(category), IssueCategory.entries.map { it to issueCategoryLabel(it) }) { category = it } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { SimpleSelectField("Responsabilità", issueResponsibilityLabel(responsibility), IssueResponsibility.entries.map { it to issueResponsibilityLabel(it) }, modifier = Modifier.weight(1f)) { responsibility = it }; SimpleSelectField("Gravità", issueSeverityLabel(severity), IssueSeverity.entries.map { it to issueSeverityLabel(it) }, modifier = Modifier.weight(1f)) { severity = it } } }
            item { SimpleSelectField("Stato", issueStatusLabel(status), IssueStatus.entries.map { it to issueStatusLabel(it) }) { status = it } }
            item { WheelDateField("Data apertura", opened, { opened = it }) }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { WheelDateField("Consegna iniziale", expected, { expected = it }, Modifier.weight(1f), allowEmpty = true); WheelDateField("Nuova consegna", revised, { revised = it }, Modifier.weight(1f), allowEmpty = true) } }
            item {
                LaunchedEffect(expected, revised) {
                    val d = runCatching { if (expected.isNotBlank() && revised.isNotBlank()) ChronoUnit.DAYS.between(LocalDate.parse(expected), LocalDate.parse(revised)).coerceAtLeast(0).toInt() else 0 }.getOrDefault(0)
                    if (d > 0) delay = d.toString()
                }
                SpectroTextField("Giorni di ritardo attribuiti", delay, { delay = it }, numeric = true)
            }
            item { SpectroTextField("Descrizione / fatti", description, { description = it }, placeholder = "Cosa è successo, cosa è stato richiesto, cosa mancava…", singleLine = false) }
            item { SpectroTextField("Note interne", internalNotes, { internalNotes = it }, placeholder = "Informazioni solo per uso interno", singleLine = false) }
            item { OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi foto / screenshot come prova") }; if (images.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } } }
            item { GradientButton("Salva criticità", p, icon = Icons.Rounded.VerifiedUser) { vm.saveClientIssue(ClientIssue(editing?.id ?: 0L, clientId, clientName.trim(), orderRef.trim(), title.trim(), category, responsibility, severity, status, opened, expected, revised, delay.toIntOrNull() ?: 0, description.trim(), internalNotes.trim(), images)); onDismiss() } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueDetailSheet(vm: SpectroViewModel, p: SpectroPalette, issue: ClientIssue, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    val updates = vm.state.issueUpdates.filter { it.issueId == issue.id }.sortedByDescending { it.date }
    var addUpdate by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 44.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(issue.title, fontSize = 25.sp, fontWeight = FontWeight.Black); Text("${issue.clientName} • ${issue.orderRef.ifBlank { "nessun riferimento" }}", color = p.textMuted) }; IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, tint = p.primary) } }
            }
            item {
                SpectroCard(p = p) {
                    Row { Metric("Stato", issueStatusLabel(issue.status), modifier = Modifier.weight(1f)); Metric("Gravità", issueSeverityLabel(issue.severity), modifier = Modifier.weight(1f)) }
                    Spacer(Modifier.height(12.dp)); Row { Metric("Responsabilità", issueResponsibilityLabel(issue.responsibility), modifier = Modifier.weight(1f)); Metric("Ritardo", "${issue.delayDays} gg", if (issue.delayDays > 0) p.warning else p.textMuted, Modifier.weight(1f)) }
                    if (issue.description.isNotBlank()) { Spacer(Modifier.height(14.dp)); Text(issue.description) }
                    if (issue.evidenceImages.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(issue.evidenceImages) }
                }
            }
            item { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { OutlinedButton(onClick = { ReportExporter.shareIssue(context, issue, updates) }, Modifier.weight(1f)) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("PDF dossier") }; OutlinedButton(onClick = { addUpdate = true }, Modifier.weight(1f)) { Icon(Icons.Rounded.AddComment, null); Spacer(Modifier.width(6.dp)); Text("Aggiorna") } } }
            item { SectionTitle("Cronologia") }
            if (updates.isEmpty()) item { Text("Nessun aggiornamento: la segnalazione iniziale è il primo evento.", color = p.textMuted) }
            items(updates, key = { it.id }) { u ->
                SpectroCard(p = p) { Text(u.date, color = p.primary, fontWeight = FontWeight.Black, fontSize = 12.sp); Spacer(Modifier.height(6.dp)); Text(u.note); if (u.images.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(u.images) } }
            }
            item { TextButton(onClick = { vm.deleteClientIssue(issue.id); onDismiss() }, modifier = Modifier.fillMaxWidth()) { Text("Elimina criticità e cronologia", color = p.danger) } }
        }
    }
    if (addUpdate) IssueUpdateSheet(vm, p, issue, onDismiss = { addUpdate = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IssueUpdateSheet(vm: SpectroViewModel, p: SpectroPalette, issue: ClientIssue, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var note by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Text("Aggiorna cronologia", fontSize = 24.sp, fontWeight = FontWeight.Black); Text(issue.title, color = p.textMuted)
            Spacer(Modifier.height(14.dp)); WheelDateField("Data", date, { date = it })
            Spacer(Modifier.height(12.dp)); SpectroTextField("Nota evento", note, { note = it }, placeholder = "Es. cliente ha inviato il nuovo file alle 16:20", singleLine = false)
            Spacer(Modifier.height(12.dp)); OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Aggiungi nuova prova") }
            if (images.isNotEmpty()) { Spacer(Modifier.height(10.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
            Spacer(Modifier.height(16.dp)); GradientButton("Aggiungi alla timeline", p) { vm.addIssueUpdate(IssueUpdate(issueId = issue.id, date = date, note = note.trim(), images = images)); onDismiss() }
        }
    }
}

@Composable
private fun PaymentsPane(vm: SpectroViewModel, p: SpectroPalette, modifier: Modifier = Modifier) {
    var unpaidOnly by remember { mutableStateOf(true) }
    var editing by remember { mutableStateOf<PaymentDeadline?>(null) }
    var showNew by remember { mutableStateOf(false) }
    val paymentItems = vm.state.payments.filter { !unpaidOnly || !it.paid }
    LazyColumn(modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Row(verticalAlignment = Alignment.CenterVertically) { FilterChip(unpaidOnly, { unpaidOnly = !unpaidOnly }, label = { Text(if (unpaidOnly) "Da incassare" else "Tutte") }); Spacer(Modifier.weight(1f)); FilledIconButton(onClick = { showNew = true }) { Icon(Icons.Rounded.Add, null) } } }
        item {
            val open = vm.state.payments.filter { !it.paid }.sumOf { it.amount }; val overdue = vm.overduePayments().sumOf { it.amount }
            SpectroCard(p = p) { Row { Metric("Da incassare", euro(open), p.warning, Modifier.weight(1f)); Metric("Scaduto", euro(overdue), if (overdue > 0) p.danger else p.success, Modifier.weight(1f)) } }
        }
        if (paymentItems.isEmpty()) item { EmptyState(Icons.Rounded.Payments, "Nessuna scadenza", "Registra importi e date di pagamento") }
        items(paymentItems, key = { it.id }) { item ->
            val overdue = !item.paid && runCatching { LocalDate.parse(item.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false)
            SpectroCard(p = p) {
                Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text(item.clientName, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("Scadenza ${item.dueDate}${if (item.orderRef.isNotBlank()) " • ${item.orderRef}" else ""}", color = if (overdue) p.danger else p.textMuted, fontSize = 12.sp) }; Text(euro(item.amount), fontWeight = FontWeight.Black, color = if (item.paid) p.success else if (overdue) p.danger else p.warning) }
                Spacer(Modifier.height(10.dp)); Row(verticalAlignment = Alignment.CenterVertically) { FilterChip(item.paid, { vm.togglePayment(item.id) }, label = { Text(if (item.paid) "PAGATO" else "SEGNA PAGATO") }, leadingIcon = { Icon(if (item.paid) Icons.Rounded.CheckCircle else Icons.Rounded.Schedule, null, Modifier.size(18.dp)) }); Spacer(Modifier.weight(1f)); IconButton(onClick = { editing = item }) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }; IconButton(onClick = { vm.deletePayment(item.id) }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) } }
            }
        }
    }
    if (showNew) PaymentEditorSheet(vm, p, null, null) { showNew = false }
    editing?.let { PaymentEditorSheet(vm, p, it, null) { editing = null } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentEditorSheet(vm: SpectroViewModel, p: SpectroPalette, editing: PaymentDeadline?, initialClient: Client?, onDismiss: () -> Unit) {
    var clientId by remember(editing?.id, initialClient?.id) { mutableStateOf(editing?.clientId ?: initialClient?.id) }
    var clientName by remember(editing?.id, initialClient?.id) { mutableStateOf(editing?.clientName ?: initialClient?.name.orEmpty()) }
    var orderRef by remember(editing?.id) { mutableStateOf(editing?.orderRef.orEmpty()) }
    var amount by remember(editing?.id) { mutableStateOf(editing?.amount?.takeIf { it > 0 }?.toString().orEmpty()) }
    var due by remember(editing?.id) { mutableStateOf(editing?.dueDate ?: LocalDate.now().plusDays(30).toString()) }
    var paid by remember(editing?.id) { mutableStateOf(editing?.paid ?: false) }
    var paidDate by remember(editing?.id) { mutableStateOf(editing?.paidDate.orEmpty()) }
    var method by remember(editing?.id) { mutableStateOf(editing?.method ?: PaymentMethod.UNSPECIFIED) }
    var notes by remember(editing?.id) { mutableStateOf(editing?.notes.orEmpty()) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(20.dp, 0.dp, 20.dp, 48.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text(if (editing == null) "Nuova scadenza" else "Modifica scadenza", fontSize = 25.sp, fontWeight = FontWeight.Black) }
            item { SimpleSelectField("Cliente", clientName.ifBlank { "Seleziona cliente" }, vm.state.clients.map { it to it.name }) { clientId = it.id; clientName = it.name } }
            item { SpectroTextField("Rif. ordine / fattura", orderRef, { orderRef = it }) }
            item { SpectroTextField("Importo €", amount, { amount = it }, numeric = true) }
            item { WheelDateField("Data scadenza", due, { due = it }) }
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Pagamento già ricevuto", Modifier.weight(1f), fontWeight = FontWeight.Bold); Switch(paid, { paid = it; if (it && paidDate.isBlank()) paidDate = LocalDate.now().toString() }) } }
            if (paid) item { WheelDateField("Data pagamento", paidDate, { paidDate = it }) }
            item { SimpleSelectField("Metodo", method.name.replace('_', ' '), PaymentMethod.entries.map { it to it.name.replace('_', ' ') }) { method = it } }
            item { SpectroTextField("Note", notes, { notes = it }, singleLine = false) }
            item { GradientButton("Salva scadenza", p) { vm.savePayment(PaymentDeadline(editing?.id ?: 0L, clientId, clientName.trim(), orderRef.trim(), amount.toDoubleSafe(), due, paid, if (paid) paidDate else "", method, notes.trim())); onDismiss() } }
        }
    }
}
