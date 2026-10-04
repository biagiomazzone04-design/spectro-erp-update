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
import com.spectro.app.util.ReportExporter
import java.time.LocalDate

private data class MaterialDraft(val name: String = "", val quantity: String = "", val unitCost: String = "")

@Composable
fun EconomicWorkScreen(vm: SpectroViewModel, p: SpectroPalette) {
    var editing by remember { mutableStateOf<EconomicWork?>(null) }
    var newOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val rows = vm.state.economicWorks.filter {
        query.isBlank() || it.clientName.contains(query, true) || it.title.contains(query, true) || it.notes.contains(query, true) || it.materials.any { m -> m.name.contains(query, true) }
    }.sortedByDescending { it.date }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Altri ricavi", fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text("Lavori economici, trasferimenti, materiali e ricavi non legati a una stampa Spectro.", color = p.textMuted)
                }
                FilledIconButton(onClick = { newOpen = true }) { Icon(Icons.Rounded.Add, "Nuovo lavoro") }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Cliente, lavoro o materiale…") }, singleLine = true)
        }
        item {
            val active = rows.filter { it.status != EconomicWorkStatus.CANCELLED }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric("Fatturato", euro(active.sumOf { it.grossRevenue }), modifier = Modifier.weight(1f))
                Metric("Costi", euro(active.sumOf { it.totalCosts }), modifier = Modifier.weight(1f))
                Metric("Utile", euro(active.sumOf { it.netProfit }), if (active.sumOf { it.netProfit } >= 0) p.success else p.danger, Modifier.weight(1f))
            }
        }
        if (rows.isEmpty()) item { EmptyState(Icons.Rounded.Payments, "Nessun lavoro economico", "Aggiungi trasferimenti, lavorazioni conto terzi o altri introiti") }
        items(rows, key = { it.id }) { row ->
            SpectroCard(p = p, onClick = { editing = row }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(row.title, fontWeight = FontWeight.Black, fontSize = 17.sp)
                        Text("${row.clientName.ifBlank { "Senza cliente" }} • ${row.date}", color = p.textMuted, fontSize = 12.sp)
                    }
                    AssistChip(onClick = {}, label = { Text(economicWorkStatusLabel(row.status), fontSize = 9.sp) })
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Metric("Lordo", euro(row.grossRevenue), modifier = Modifier.weight(1f))
                    Metric("Costi", euro(row.totalCosts), modifier = Modifier.weight(1f))
                    Metric("Utile", euro(row.netProfit), if (row.netProfit >= 0) p.success else p.danger, Modifier.weight(1f))
                }
                if (row.materials.isNotEmpty()) { Spacer(Modifier.height(7.dp)); Text(row.materials.joinToString(" • ") { "${it.name}: ${meters(it.quantity)}" }, color = p.textMuted, fontSize = 11.sp, maxLines = 2) }
            }
        }
    }

    if (newOpen) EconomicWorkDialog(vm, p, null) { newOpen = false }
    editing?.let { EconomicWorkDialog(vm, p, it) { editing = null } }
}

@Composable
private fun EconomicWorkDialog(vm: SpectroViewModel, p: SpectroPalette, editing: EconomicWork?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var date by remember(editing?.id) { mutableStateOf(editing?.date ?: LocalDate.now().toString()) }
    var clientId by remember(editing?.id) { mutableStateOf(editing?.clientId) }
    var clientName by remember(editing?.id) { mutableStateOf(editing?.clientName.orEmpty()) }
    var title by remember(editing?.id) { mutableStateOf(editing?.title.orEmpty()) }
    var quantity by remember(editing?.id) { mutableStateOf(editing?.quantity?.takeIf { it > 0 }?.toString().orEmpty()) }
    var unitPrice by remember(editing?.id) { mutableStateOf(editing?.unitPrice?.takeIf { it > 0 }?.toString().orEmpty()) }
    var manualGross by remember(editing?.id) { mutableStateOf(editing?.manualGrossRevenue?.takeIf { it > 0 }?.toString().orEmpty()) }
    var otherCosts by remember(editing?.id) { mutableStateOf(editing?.otherCosts?.takeIf { it > 0 }?.toString().orEmpty()) }
    var notes by remember(editing?.id) { mutableStateOf(editing?.notes.orEmpty()) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: EconomicWorkStatus.OPEN) }
    var images by remember(editing?.id) { mutableStateOf(editing?.images ?: emptyList()) }
    var materials by remember(editing?.id) { mutableStateOf<List<MaterialDraft>>(editing?.materials?.map { MaterialDraft(it.name, it.quantity.toString(), it.unitCost.toString()) } ?: emptyList()) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        images = images + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() }
    }
    val model = EconomicWork(
        id = editing?.id ?: 0L, date = date, clientId = clientId, clientName = clientName.trim(), title = title.trim(),
        quantity = quantity.toDoubleSafe(), unitPrice = unitPrice.toDoubleSafe(), manualGrossRevenue = manualGross.toDoubleSafe(),
        materials = materials.mapNotNull { m -> m.name.trim().takeIf { it.isNotBlank() }?.let { EconomicMaterialUsage(it, m.quantity.toDoubleSafe(), m.unitCost.toDoubleSafe()) } },
        otherCosts = otherCosts.toDoubleSafe(), notes = notes.trim(), images = images, status = status
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuovo lavoro economico" else "Modifica lavoro", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 650.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { WheelDateField("Data lavoro", date, { date = it }) }
                item { SimpleSelectField("Cliente", clientName.ifBlank { "Seleziona cliente (facoltativo)" }, listOf(0L to "Nessun cliente") + vm.state.clients.map { it.id to it.name }) { id -> if (id == 0L) { clientId = null; clientName = "" } else vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; clientName = it.name } } }
                item { SpectroTextField("Nome lavoro", title, { title = it }, placeholder = "Es. Trasferiti 300 mt per Agostino") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Quantità / mt", quantity, { quantity = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Prezzo unitario €", unitPrice, { unitPrice = it }, Modifier.weight(1f), numeric = true) } }
                item { SpectroTextField("Fatturato lordo manuale €", manualGross, { manualGross = it }, numeric = true, placeholder = "Lascia vuoto per quantità × prezzo") }
                item { SectionTitle("Materiali utilizzati", "+ Materiale") { materials = materials + MaterialDraft() } }
                if (materials.isEmpty()) item { Text("Nessun materiale imputato. Puoi aggiungere carta protezione, cotone o qualsiasi altro materiale.", color = p.textMuted, fontSize = 11.sp) }
                items(materials.size) { idx ->
                    val m = materials[idx]
                    SpectroCard(p = p) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Materiale ${idx + 1}", fontWeight = FontWeight.Black, modifier = Modifier.weight(1f))
                            IconButton(onClick = { materials = materials.filterIndexed { i, _ -> i != idx } }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
                        }
                        SpectroTextField("Nome", m.name, { v -> materials = materials.toMutableList().also { it[idx] = m.copy(name = v) } }, placeholder = "Carta protezione")
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SpectroTextField("Quantità", m.quantity, { v -> materials = materials.toMutableList().also { it[idx] = m.copy(quantity = v) } }, Modifier.weight(1f), numeric = true)
                            SpectroTextField("Costo €/unità", m.unitCost, { v -> materials = materials.toMutableList().also { it[idx] = m.copy(unitCost = v) } }, Modifier.weight(1f), numeric = true)
                        }
                        Spacer(Modifier.height(5.dp)); Text("Costo: ${euro(m.quantity.toDoubleSafe() * m.unitCost.toDoubleSafe())}", color = p.textMuted, fontSize = 11.sp)
                    }
                }
                item { OutlinedButton(onClick = { materials = materials + MaterialDraft("Carta protezione", "", vm.state.settings.protectionPaperCost.toString()) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Add, null); Spacer(Modifier.width(6.dp)); Text("Aggiungi carta protezione") } }
                item { SpectroTextField("Altri costi €", otherCosts, { otherCosts = it }, numeric = true) }
                item { SimpleSelectField("Stato", economicWorkStatusLabel(status), EconomicWorkStatus.entries.map { it to economicWorkStatusLabel(it) }) { status = it } }
                item { SpectroTextField("Note", notes, { notes = it }, singleLine = false) }
                item { OutlinedButton(onClick = { picker.launch("image/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(6.dp)); Text("Allega immagini") } }
                if (images.isNotEmpty()) item { ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
                item {
                    SpectroCard(p = p) {
                        Text("Riepilogo economico", fontWeight = FontWeight.Black)
                        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Metric("Lordo", euro(model.grossRevenue), modifier = Modifier.weight(1f))
                            Metric("Costi", euro(model.totalCosts), modifier = Modifier.weight(1f))
                            Metric("Utile", euro(model.netProfit), if (model.netProfit >= 0) p.success else p.danger, Modifier.weight(1f))
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { if (model.title.isNotBlank() && model.grossRevenue >= 0) { vm.saveEconomicWork(model); onDismiss() } }) { Text("Salva") } },
        dismissButton = {
            Row {
                if (editing != null) {
                    TextButton(onClick = { ReportExporter.shareEconomicWork(context, model); }) { Text("PDF") }
                    TextButton(onClick = { vm.deleteEconomicWork(editing.id); onDismiss() }) { Text("Elimina", color = p.danger) }
                }
                TextButton(onClick = onDismiss) { Text("Chiudi") }
            }
        }
    )
}
