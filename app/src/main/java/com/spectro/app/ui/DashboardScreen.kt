package com.spectro.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.ReportExporter
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DashboardScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    onRecord: (ProductionRecord) -> Unit,
    onOpenOrder: (WorkOrder) -> Unit = {},
    onEditOrder: (WorkOrder) -> Unit = {}
) {
    var mode by remember { mutableStateOf("day") }
    var query by remember { mutableStateOf("") }
    var selectedDate by remember { mutableStateOf(LocalDate.now().toString()) }
    var deleteTarget by remember { mutableStateOf<WorkOrder?>(null) }
    var statsMonth by remember { mutableStateOf(YearMonth.now()) }
    var statsSection by remember { mutableStateOf("SUMMARY") }
    val context = LocalContext.current
    val currentMonthStats = vm.monthlyStats(YearMonth.now())
    val days = vm.dashboardDays(query)
    val day = days.firstOrNull { it.date == selectedDate }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            SegmentedChoice(
                options = listOf("day" to "GIORNALIERO", "stats" to "STATISTICHE"),
                selected = mode,
                p = p,
                onSelected = { mode = it }
            )
        }

        item {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Cerca cliente, ordine o disegno") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )
        }

        if (mode == "day") {
            val openIssues = vm.openIssues().size
            val overduePayments = vm.overduePayments().size
            val lowStock = vm.lowStock().size
            val overdueWork = vm.overdueWork().size
            val openTasks = vm.openTasks().size
            if (openIssues + overduePayments + lowStock + overdueWork + openTasks > 0) {
                item {
                    SpectroCard(p = p) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.NotificationsActive, null, tint = p.warning)
                            Spacer(Modifier.width(10.dp))
                            Column { Text("Da controllare", fontWeight = FontWeight.Black, fontSize = 17.sp); Text("Alert operativi automatici", color = p.textMuted, fontSize = 12.sp) }
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AlertMini("Criticità", openIssues, p.warning, Modifier.weight(1f))
                            AlertMini("Scaduti", overduePayments, p.danger, Modifier.weight(1f))
                            AlertMini("Scorte", lowStock, p.warning, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AlertMini("Ordini", overdueWork, p.danger, Modifier.weight(1f))
                            AlertMini("Attività", openTasks, p.primary, Modifier.weight(1f))
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }
            val activeOrders = vm.state.workOrders
.filter { !it.status.isFinishedOrder() }
                .sortedWith(compareByDescending<WorkOrder> { it.priority == Priority.HIGH || it.status == WorkStatus.BLOCKED }
                    .thenBy { it.dueDate.ifBlank { "9999-12-31" } })
                .take(5)
            if (activeOrders.isNotEmpty()) {
                item { SectionTitle("Ordini attivi") }
                items(activeOrders, key = { "order-${it.id}" }) { order ->
                    DashboardOrderCard(order, p, onOpenOrder, onEditOrder, onComplete = { vm.completeWorkOrder(it.id) }, onDelete = { deleteTarget = it })
                }
            }
            item {
                SpectroCard(p = p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Produzione mese corrente", fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text(YearMonth.now().toString(), color = p.textMuted, fontSize = 11.sp)
                        }
                        Text(meters(currentMonthStats.printedPaperMeters), color = p.primary, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Carta stampata", meters(currentMonthStats.printedPaperMeters), modifier = Modifier.weight(1f))
                        Metric("Cotone", meters(currentMonthStats.cottonMeters), modifier = Modifier.weight(1f))
                        Metric("Utile mese", euro(currentMonthStats.netProfit), valueColor = if (currentMonthStats.netProfit >= 0) p.success else p.danger, modifier = Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Metric("Altri ricavi", euro(currentMonthStats.otherRevenue), valueColor = p.primary, modifier = Modifier.weight(1f))
                        Metric("Ordini evasi", currentMonthStats.completedOrderCount.toString(), valueColor = p.success, modifier = Modifier.weight(1f))
                        Metric("Criticità", currentMonthStats.criticalityCount.toString(), valueColor = if (currentMonthStats.criticalityCount > 0) p.warning else p.success, modifier = Modifier.weight(1f))
                    }
                    if (currentMonthStats.clients.any { it.paperMeters > 0 }) {
                        Spacer(Modifier.height(12.dp))
                        Text("Metri carta per cliente", color = p.textMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Spacer(Modifier.height(7.dp))
                        currentMonthStats.clients.filter { it.paperMeters > 0 }.take(5).forEach { row ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(row.clientName, color = p.text, fontSize = 12.sp)
                                Text(meters(row.paperMeters), color = p.textMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
            item {
                DayHero(day = day, selectedDate = selectedDate, p = p,
                    onDateSelected = { selectedDate = it },
                    onPrevious = { selectedDate = LocalDate.parse(selectedDate).minusDays(1).toString() },
                    onNext = { selectedDate = LocalDate.parse(selectedDate).plusDays(1).toString() }
                )
            }
            item { SectionTitle("Registrazioni del giorno") }
            if (day == null || day.records.isEmpty()) {
                item { EmptyState(Icons.Rounded.ReceiptLong, "Nessuna registrazione", "Aggiungi la prima produzione con il pulsante +") }
            } else {
                items(day.records, key = { it.id }) { record -> RecordCard(record, p, onRecord) }
            }
        } else {
            val stats = vm.monthlyStats(statsMonth)
            item {
                SpectroCard(p = p) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { statsMonth = statsMonth.minusMonths(1) }) { Icon(Icons.Rounded.ChevronLeft, "Mese precedente") }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("STATISTICHE MENSILI", fontSize = 10.sp, fontWeight = FontWeight.Black, color = p.textMuted)
                            Text(statsMonth.toString(), fontSize = 21.sp, fontWeight = FontWeight.Black)
                        }
                        IconButton(onClick = { statsMonth = statsMonth.plusMonths(1) }) { Icon(Icons.Rounded.ChevronRight, "Mese successivo") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val prefix = statsMonth.toString()
                            ReportExporter.shareMonthlyStatistics(
                                context = context,
                                stats = stats,
                                productions = vm.state.production.filter { it.date.startsWith(prefix) },
                                cottonOrders = vm.state.cottonOrders.filter { it.orderDate.startsWith(prefix) },
                                economicWorks = vm.state.economicWorks.filter { it.date.startsWith(prefix) && it.status != EconomicWorkStatus.CANCELLED },
                                timelineEvents = vm.state.timelineEvents.filter { it.date.startsWith(prefix) },
                                clientIssues = vm.state.clientIssues.filter { it.openedDate.startsWith(prefix) },
                                workOrders = vm.state.workOrders.filter { it.effectiveDeliveryDate.startsWith(prefix) && it.status.isArchivedOrder() }
                            )
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(7.dp)); Text("Esporta report mensile PDF") }
                }
            }
            item {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "SUMMARY" to "Riepilogo",
                        "REVENUE" to "Fatturato",
                        "CLIENTS" to "Clienti",
                        "METERS" to "Metri",
                        "PROFIT" to "Utile"
                    ).forEach { (key, label) ->
                        FilterChip(selected = statsSection == key, onClick = { statsSection = key }, label = { Text(label) })
                    }
                }
            }
            when (statsSection) {
                "REVENUE" -> {
                    item { AnalyticsCard("Fatturato per cliente", vm.monthlyRevenueByClient(statsMonth), p, true) }
                    item {
                        SpectroCard(p = p) {
                            SectionTitle("Composizione fatturato")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Sublimazione", euro(stats.productionRevenue), modifier = Modifier.weight(1f))
                                Metric("Cotone", euro(stats.cottonRevenue), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Altri lavori", euro(stats.otherRevenue), valueColor = p.primary, modifier = Modifier.weight(1f))
                                Metric("Totale", euro(stats.totalRevenue), valueColor = p.success, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                "CLIENTS" -> {
                    if (stats.clients.isEmpty()) item { EmptyState(Icons.Rounded.People, "Nessun dato cliente", "Nessuna produzione nel mese selezionato") }
                    items(stats.clients, key = { "stat-client-${it.clientName}" }) { row ->
                        SpectroCard(p = p) {
                            Text(row.clientName, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Fatturato", euro(row.revenue), modifier = Modifier.weight(1f))
                                Metric("Carta", meters(row.paperMeters), modifier = Modifier.weight(1f))
                                Metric("Utile", euro(row.profit), valueColor = if (row.profit >= 0) p.success else p.danger, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(7.dp))
                            Text(
                                "${row.records} registrazioni • Criticità ${row.criticalities}" +
                                    (if (row.otherRevenue > 0) " • Altri ricavi ${euro(row.otherRevenue)}" else "") +
                                    (if (row.cottonMeters > 0) " • Cotone ${meters(row.cottonMeters)}" else ""),
                                color = p.textMuted,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                "METERS" -> {
                    item { AnalyticsCard("Metri carta stampati per cliente", vm.monthlyPaperMetersByClient(statsMonth), p, false) }
                    item {
                        SpectroCard(p = p) {
                            SectionTitle("Metri del mese")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Carta stampata", meters(stats.printedPaperMeters), p.primary, Modifier.weight(1f))
                                Metric("Cotone", meters(stats.cottonMeters), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Text("${stats.productionCount} produzioni sublimatiche • ${stats.cottonOrderCount} ordini cotone", color = p.textMuted, fontSize = 12.sp)
                        }
                    }
                }
                "PROFIT" -> {
                    item {
                        SpectroCard(p = p) {
                            SectionTitle("Redditività")
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Ricavi", euro(stats.totalRevenue), modifier = Modifier.weight(1f))
                                Metric("Costi", euro(stats.totalCosts), modifier = Modifier.weight(1f))
                                Metric("Utile netto", euro(stats.netProfit), valueColor = if (stats.netProfit >= 0) p.success else p.danger, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(12.dp))
                            Text("Materie sublimazione ${euro(stats.materialCosts)}", color = p.textMuted)
                            Text("Costi cotone ${euro(stats.cottonCosts)}", color = p.textMuted)
                            Text("Costi altri lavori ${euro(stats.otherCosts)}", color = p.textMuted)
                            Text("Costi fissi imputati ${euro(stats.fixedCosts)}", color = p.textMuted)
                        }
                    }
                }
                else -> {
                    item {
                        SpectroCard(p = p) {
                            Text("Riepilogo ${stats.month}", fontWeight = FontWeight.Black, fontSize = 18.sp)
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Fatturato", euro(stats.totalRevenue), modifier = Modifier.weight(1f))
                                Metric("Carta", meters(stats.printedPaperMeters), modifier = Modifier.weight(1f))
                                Metric("Utile", euro(stats.netProfit), valueColor = if (stats.netProfit >= 0) p.success else p.danger, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Produzioni", stats.productionCount.toString(), modifier = Modifier.weight(1f))
                                Metric("Cotone", stats.cottonOrderCount.toString(), modifier = Modifier.weight(1f))
                                Metric("Altri lavori", stats.economicWorkCount.toString(), modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Metric("Clienti", stats.clients.size.toString(), modifier = Modifier.weight(1f))
                                Metric("Criticità", stats.criticalityCount.toString(), valueColor = if (stats.criticalityCount > 0) p.warning else p.success, modifier = Modifier.weight(1f))
                                Metric("Altri ricavi", euro(stats.otherRevenue), valueColor = p.primary, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                    item { AnalyticsCard("Fatturato per cliente", vm.monthlyRevenueByClient(statsMonth), p, true) }
                    item { AnalyticsCard("Carta stampata per cliente", vm.monthlyPaperMetersByClient(statsMonth), p, false) }
                }
            }
            if (stats.productionCount == 0 && stats.cottonOrderCount == 0 && stats.economicWorkCount == 0) {
                item { EmptyState(Icons.Rounded.Analytics, "Statistiche in attesa", "Nessun dato nel mese selezionato") }
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("Eliminare definitivamente l'ordine?") },
            text = { Text("${target.title.ifBlank { target.orderCode }} verrà eliminato insieme alla timeline. Se esiste una produzione collegata, verrà rimossa e il magazzino verrà riallineato.") },
            confirmButton = { TextButton(onClick = { vm.deleteWorkOrder(target.id); deleteTarget = null }) { Text("Elimina", color = p.danger) } },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Annulla") } }
        )
    }
}

@Composable
private fun DayHero(day: DashboardDay?, selectedDate: String, p: SpectroPalette, onDateSelected: (String) -> Unit, onPrevious: () -> Unit, onNext: () -> Unit) {
    SpectroCard(p = p) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onPrevious) { Text("‹", fontSize = 28.sp, fontWeight = FontWeight.Black) }
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("GIORNO SELEZIONATO", fontSize = 10.sp, fontWeight = FontWeight.Black, color = p.textMuted)
                Spacer(Modifier.height(5.dp))
                WheelDateField("", selectedDate, onDateSelected, modifier = Modifier.fillMaxWidth())
            }
            TextButton(onClick = onNext) { Text("›", fontSize = 28.sp, fontWeight = FontWeight.Black) }
        }
        Spacer(Modifier.height(16.dp))
        Box(
            Modifier.fillMaxWidth().background(p.subtleBrush, RoundedCornerShape(22.dp)).padding(22.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val profit = day?.netProfit ?: 0.0
                Text(
                    if (profit >= 0) "+${euro(profit)}" else euro(profit),
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Black,
                    color = if (profit >= 0) p.success else p.danger
                )
                Text("Utile netto", color = p.textMuted, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("Fatturato", euro(day?.revenue ?: 0.0), modifier = Modifier.weight(1f))
            Metric("Materie", euro(day?.materialCosts ?: 0.0), modifier = Modifier.weight(1f))
            Metric("Fissi", euro(day?.fixedCosts ?: 0.0), modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun RecordCard(record: ProductionRecord, p: SpectroPalette, onRecord: (ProductionRecord) -> Unit) {
    SpectroCard(p = p, onClick = { onRecord(record) }) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).background(if (record.type == RecordType.NEGATIVE) p.danger.copy(alpha = .18f) else p.primary.copy(alpha = .14f), RoundedCornerShape(15.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ReceiptLong, null, tint = if (record.type == RecordType.NEGATIVE) p.danger else p.primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(record.clientName.ifBlank { "Registrazione" }, fontWeight = FontWeight.Black, maxLines = 1)
                Text(record.orderRef.ifBlank { if (record.type == RecordType.NEGATIVE) "Evento negativo" else record.fabricDisplayName }, color = p.textMuted, maxLines = 1)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    if (record.type == RecordType.NEGATIVE) "-${euro(record.materialCostTotal)}" else "+${euro(record.grossRevenue)}",
                    fontWeight = FontWeight.Black,
                    color = if (record.type == RecordType.NEGATIVE) p.danger else p.success
                )
                if (record.type == RecordType.PRODUCTION) Text("Utile ${euro(record.grossProfit)}", fontSize = 11.sp, color = p.textMuted)
            }
        }
    }
}

@Composable
private fun DashboardOrderCard(order: WorkOrder, p: SpectroPalette, onOpen: (WorkOrder) -> Unit, onEdit: (WorkOrder) -> Unit, onComplete: (WorkOrder) -> Unit, onDelete: (WorkOrder) -> Unit) {
    val overdue = order.dueDate.isNotBlank() && runCatching { LocalDate.parse(order.dueDate).isBefore(LocalDate.now()) }.getOrDefault(false)
    val accent = when {
        order.status == WorkStatus.BLOCKED || overdue -> p.danger
        order.status in listOf(WorkStatus.WAITING_CLIENT, WorkStatus.WAITING_MATERIAL, WorkStatus.ON_HOLD) -> p.warning
        order.status in listOf(WorkStatus.IN_PRODUCTION, WorkStatus.IN_PROGRESS, WorkStatus.PRINTED, WorkStatus.TRANSFERRED) -> p.success
        else -> p.primary
    }
    SpectroCard(p = p, onClick = { onOpen(order) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(5.dp).height(58.dp).background(accent, RoundedCornerShape(99.dp)))
            Spacer(Modifier.width(11.dp))
            Column(Modifier.weight(1f)) {
                Text(order.title.ifBlank { order.orderCode }, fontWeight = FontWeight.Black, maxLines = 1)
                Text("${order.client} • ${workStatusLabel(order.status)}", color = p.textMuted, fontSize = 12.sp, maxLines = 1)
                if (order.orderCode.isNotBlank()) Text(order.orderCode, color = p.textMuted, fontSize = 10.sp, maxLines = 1)
                if (order.dueDate.isNotBlank()) Text("Consegna ${order.dueDate}${if (overdue) " • SCADUTA" else ""}", color = if (overdue) p.danger else p.textMuted, fontSize = 11.sp, fontWeight = if (overdue) FontWeight.Black else FontWeight.Normal)
            }
            if (order.productionRecordId != null && !order.status.isFinishedOrder()) {
                IconButton(onClick = { onComplete(order) }) { Icon(Icons.Rounded.TaskAlt, "Completa ed evadi", tint = p.success) }
            }
            IconButton(onClick = { onEdit(order) }) { Icon(Icons.Rounded.Edit, "Modifica ordine", tint = p.primary) }
            IconButton(onClick = { onDelete(order) }) { Icon(Icons.Rounded.Delete, "Elimina ordine", tint = p.danger) }
        }
    }
}

@Composable
private fun AnalyticsCard(title: String, slices: List<AnalyticsSlice>, p: SpectroPalette, money: Boolean) {
    SpectroCard(p = p) {
        Text(title, fontWeight = FontWeight.Black, fontSize = 17.sp)
        Spacer(Modifier.height(18.dp))
        if (slices.isEmpty()) {
            Text("Dati insufficienti", color = p.textMuted)
        } else {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Donut(slices, p, Modifier.size(132.dp))
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    slices.forEachIndexed { i, s ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(9.dp).background(chartColor(i, p), RoundedCornerShape(4.dp)))
                            Spacer(Modifier.width(8.dp))
                            Text(s.label, Modifier.weight(1f), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis, fontSize = 12.sp)
                            Text(if (money) euro(s.value) else meters(s.value), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = p.textMuted)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(slices: List<AnalyticsSlice>, p: SpectroPalette, modifier: Modifier = Modifier) {
    val total = slices.sumOf { it.value }.coerceAtLeast(.0001)
    Canvas(modifier) {
        var start = -90f
        slices.forEachIndexed { i, s ->
            val sweep = (s.value / total * 360.0).toFloat()
            drawArc(chartColor(i, p), start, sweep, false, style = Stroke(width = size.minDimension * .20f))
            start += sweep
        }
    }
}

private fun chartColor(i: Int, p: SpectroPalette): Color = listOf(p.primary, p.secondary, p.tertiary, p.success, p.warning, Color(0xFF60A5FA))[i % 6]


@Composable
private fun AlertMini(label: String, count: Int, color: Color, modifier: Modifier = Modifier) {
    Column(modifier.background(color.copy(alpha = .10f), RoundedCornerShape(14.dp)).padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), color = if (count > 0) color else MaterialTheme.colorScheme.onSurface.copy(alpha = .78f), fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .82f))
    }
}
