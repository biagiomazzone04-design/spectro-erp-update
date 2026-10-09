package com.spectro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.ReportExporter

@Composable
fun ClientHistoryCard(entry: ClientHistoryEntry, vm: SpectroViewModel, p: SpectroPalette,
    onOrder: (WorkOrder) -> Unit, onProduction: (ProductionRecord) -> Unit, onCotton: (CottonOrder) -> Unit) {
    val context = LocalContext.current
    val open: () -> Unit = { when(entry) {
        is ClientHistoryEntry.Order -> onOrder(entry.order)
        is ClientHistoryEntry.Legacy -> onProduction(entry.production)
        is ClientHistoryEntry.Cotton -> onCotton(entry.order)
    } }
    SpectroCard(p = p, onClick = open) {
        when(entry) {
            is ClientHistoryEntry.Order -> {
                val w = entry.order
                Text(w.title.ifBlank { w.orderCode }, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("${workStatusLabel(w.status)} • ${entry.date}", color = p.textMuted)
                Text(w.orderCode, color = p.textMuted, fontSize = 11.sp)
                orderFabricRows(vm, w).forEach { row -> Text("${row.fabricName.uppercase()} • ${meters(row.meters).uppercase()}", fontWeight = FontWeight.Black) }
                val events = vm.timelineForOrder(w.id)
                Text("${entry.productions.size} produzioni • ${events.count { it.severity != TimelineSeverity.INFO }} criticità", color = p.primary)
                if(w.description.isNotBlank()) Text(w.description, maxLines = 3)
                entry.productions.map { it.technicalNotes }.filter { it.isNotBlank() }.distinct().take(2).forEach { Text(it, maxLines = 3) }
                ImageStrip((w.images + entry.productions.flatMap { it.images }).distinct())
                TextButton(onClick = { ReportExporter.shareOrderTimeline(context, w, events, entry.productions) }) { Text("PDF dossier completo") }
            }
            is ClientHistoryEntry.Legacy -> {
                val r = entry.production
                Text("Produzione storica", color = p.primary, fontWeight = FontWeight.Black)
                Text(r.orderRef.ifBlank { "Produzione del ${r.date}" }, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text(r.date, color = p.textMuted)
                Text("${r.fabricDisplayName.takeUnless { it.isBlank() || it == "Nessuno" } ?: r.manualFabricName} • ${meters(r.fabricUsedMeters)}".uppercase(), fontWeight = FontWeight.Black)
                Text("Carta ${meters(r.printedPaperMeters)} • Disegni ${r.designs.joinToString().ifBlank { "—" }}")
                if(r.technicalNotes.isNotBlank()) Text(r.technicalNotes, maxLines = 4)
                Text("Ricavo ${euro(r.grossRevenue)} • Utile ${euro(r.grossProfit)}", color = p.textMuted)
                ImageStrip(r.images)
                TextButton(onClick = { ReportExporter.share(context, r) }) { Text("PDF produzione storica") }
            }
            is ClientHistoryEntry.Cotton -> {
                val c = entry.order
                Text(c.title.ifBlank { c.orderCode }, fontWeight = FontWeight.Black, fontSize = 19.sp)
                Text("Cotone • ${c.orderDate} • ${workStatusLabel(c.status)}", color = p.textMuted)
                Text("${c.fabricName} • ${meters(if(c.actualProducedMeters > 0) c.actualProducedMeters else c.orderedMeters)}".uppercase(), fontWeight = FontWeight.Black)
                Text(c.notes, maxLines = 3)
                ImageStrip(c.images)
                TextButton(onClick = { ReportExporter.shareCottonOrder(context, c) }) { Text("PDF Cotone") }
            }
        }
        OutlinedButton(onClick = open, modifier = Modifier.fillMaxWidth()) { Text("Apri dettaglio") }
    }
}
