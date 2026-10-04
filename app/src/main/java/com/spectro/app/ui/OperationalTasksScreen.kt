package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import com.spectro.app.util.AttachmentStore
import java.time.LocalDate

@Composable
fun OperationalTasksScreen(vm: SpectroViewModel, p: SpectroPalette, onOpenOrder: (WorkOrder) -> Unit = {}) {
    var editing by remember { mutableStateOf<OperationalTask?>(null) }
    var addOpen by remember { mutableStateOf(false) }
    var showAll by remember { mutableStateOf(false) }
    val closed = setOf(OperationalTaskStatus.DONE, OperationalTaskStatus.CANCELLED)
    val tasks = vm.state.operationalTasks.filter { showAll || it.status !in closed }.sortedWith(compareByDescending<OperationalTask> { it.priority == Priority.HIGH }.thenBy { it.dueDate.ifBlank { "9999-12-31" } })

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("Attività", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Campioni, ricerche grafiche e lavori da ricordare prima della produzione.", color = p.textMuted) }
                FilledIconButton(onClick = { addOpen = true }) { Icon(Icons.Rounded.AddTask, "Nuova attività") }
            }
            Spacer(Modifier.height(10.dp)); FilterChip(selected = showAll, onClick = { showAll = !showAll }, label = { Text(if (showAll) "Tutte" else "Da fare") })
        }
        item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Da fare", vm.openTasks().size.toString(), modifier = Modifier.weight(1f)); Metric("Urgenti", vm.openTasks().count { it.priority == Priority.HIGH }.toString(), if (vm.openTasks().any { it.priority == Priority.HIGH }) p.warning else p.textMuted, Modifier.weight(1f)); Metric("Totali", vm.state.operationalTasks.size.toString(), modifier = Modifier.weight(1f)) } }
        if (tasks.isEmpty()) item { EmptyState(Icons.Rounded.TaskAlt, "Nessuna attività", "Aggiungi un campione, una ricerca grafica o un'altra attività operativa") }
        items(tasks, key = { it.id }) { task ->
            SpectroCard(p = p, onClick = { editing = task }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(when (task.type) { OperationalTaskType.SAMPLE -> Icons.Rounded.Science; OperationalTaskType.GRAPHICS_SEARCH -> Icons.Rounded.Palette; OperationalTaskType.CLIENT_REQUEST -> Icons.Rounded.Person; OperationalTaskType.PREPARATION -> Icons.Rounded.Build; OperationalTaskType.OTHER -> Icons.Rounded.TaskAlt }, null, tint = if (task.priority == Priority.HIGH) p.warning else p.primary)
                    Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f)) { Text(task.title, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("${operationalTaskTypeLabel(task.type)}${task.clientName.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}", color = p.textMuted, fontSize = 11.sp); if (task.dueDate.isNotBlank()) Text("Entro ${task.dueDate}", color = p.textMuted, fontSize = 11.sp) }
                    AssistChip(onClick = {}, label = { Text(operationalTaskStatusLabel(task.status), fontSize = 9.sp) })
                }
                if (task.description.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(task.description, maxLines = 3) }
                if (task.workOrderId != null) { Spacer(Modifier.height(8.dp)); TextButton(onClick = { vm.state.workOrders.firstOrNull { it.id == task.workOrderId }?.let(onOpenOrder) }) { Icon(Icons.Rounded.Assignment, null); Spacer(Modifier.width(5.dp)); Text("Apri ordine collegato") } }
            }
        }
    }
    if (addOpen) OperationalTaskDialog(vm, p, null) { addOpen = false }
    editing?.let { OperationalTaskDialog(vm, p, it) { editing = null } }
}

@Composable
private fun OperationalTaskDialog(vm: SpectroViewModel, p: SpectroPalette, editing: OperationalTask?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var created by remember(editing?.id) { mutableStateOf(editing?.createdDate ?: LocalDate.now().toString()) }
    var due by remember(editing?.id) { mutableStateOf(editing?.dueDate.orEmpty()) }
    var clientId by remember(editing?.id) { mutableStateOf(editing?.clientId) }
    var clientName by remember(editing?.id) { mutableStateOf(editing?.clientName.orEmpty()) }
    var workId by remember(editing?.id) { mutableStateOf(editing?.workOrderId) }
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var type by remember(editing?.id) { mutableStateOf(editing?.type ?: OperationalTaskType.OTHER) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var priority by remember(editing?.id) { mutableStateOf(editing?.priority ?: Priority.NORMAL) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: OperationalTaskStatus.TODO) }
    var images by remember(editing?.id) { mutableStateOf(editing?.images ?: emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }
    val model = OperationalTask(editing?.id ?: 0L, created, due, clientId, clientName.trim(), workId, title.trim(), type, description.trim(), priority, status, images)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuova attività" else "Modifica attività", fontWeight = FontWeight.Black) },
        text = { LazyColumn(Modifier.heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { SimpleSelectField("Tipo", operationalTaskTypeLabel(type), OperationalTaskType.entries.map { it to operationalTaskTypeLabel(it) }) { type = it } }
            item { SpectroTextField("Titolo", title, { title = it }, placeholder = "Es. Fare campione per Sunrise") }
            item { SimpleSelectField("Cliente", clientName.ifBlank { "Nessun cliente" }, listOf(0L to "Nessun cliente") + vm.state.clients.map { it.id to it.name }) { id -> if (id == 0L) { clientId = null; clientName = "" } else vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; clientName = it.name } } }
            item { SimpleSelectField("Ordine collegato", workId?.let { id -> vm.state.workOrders.firstOrNull { it.id == id }?.let { it.title.ifBlank { it.orderCode } } } ?: "Nessun ordine", listOf(0L to "Nessun ordine") + vm.state.workOrders.filter { clientId == null || it.clientId == clientId || it.client.equals(clientName, true) }.map { it.id to "${it.title.ifBlank { it.orderCode }} • ${it.client}" }) { id -> workId = id.takeIf { it != 0L } } }
            item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WheelDateField("Creata", created, { created = it }, Modifier.weight(1f)); WheelDateField("Scadenza", due, { due = it }, Modifier.weight(1f), allowEmpty = true) } }
            item { SegmentedChoice(listOf("NORMAL" to "NORMALE", "HIGH" to "URGENTE"), priority.name, p) { priority = Priority.valueOf(it) } }
            item { SimpleSelectField("Stato", operationalTaskStatusLabel(status), OperationalTaskStatus.entries.map { it to operationalTaskStatusLabel(it) }) { status = it } }
            item { SpectroTextField("Descrizione / istruzioni", description, { description = it }, singleLine = false) }
            item { OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Allega immagini") } }
            if (images.isNotEmpty()) item { ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
        } },
        confirmButton = { Button(onClick = { if (model.title.isNotBlank()) { vm.saveOperationalTask(model); onDismiss() } }) { Text("Salva") } },
        dismissButton = { Row { if (editing != null) TextButton(onClick = { vm.deleteOperationalTask(editing.id); onDismiss() }) { Text("Elimina", color = p.danger) }; TextButton(onClick = onDismiss) { Text("Chiudi") } } }
    )
}
