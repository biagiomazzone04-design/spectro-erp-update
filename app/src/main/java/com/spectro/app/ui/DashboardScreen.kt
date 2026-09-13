package com.spectro.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import java.time.LocalDate

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
            if (openIssues + overduePayments + lowStock + overdueWork > 0) {
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
                            AlertMini("Ordini", overdueWork, p.danger, Modifier.weight(1f))
                        }
                    }
                }
            }
            val activeOrders = vm.state.workOrders
                .filter { it.status !in listOf(WorkStatus.DELIVERED, WorkStatus.CLOSED, WorkStatus.CANCELLED) }
                .sortedWith(compareByDescending<WorkOrder> { it.priority == Priority.HIGH || it.status == WorkStatus.BLOCKED }
                    .thenBy { it.dueDate.ifBlank { "9999-12-31" } })
                .take(5)
            if (activeOrders.isNotEmpty()) {
                item { SectionTitle("Ordini attivi") }
                items(activeOrders, key = { "order-${it.id}" }) { order ->
                    DashboardOrderCard(order, p, onOpenOrder, onEditOrder)
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
            item { AnalyticsCard("Fatturato per cliente", vm.topClients(), p, true) }
            item { AnalyticsCard("Consumo tessuti", vm.topFabrics(), p, false) }
            if (vm.topClients().isEmpty() && vm.topFabrics().isEmpty()) {
                item { EmptyState(Icons.Rounded.Analytics, "Statistiche in attesa", "Inserisci alcune produzioni per generare grafici") }
            }
        }
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
private fun DashboardOrderCard(order: WorkOrder, p: SpectroPalette, onOpen: (WorkOrder) -> Unit, onEdit: (WorkOrder) -> Unit) {
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
                Text(order.orderCode.ifBlank { order.title }, fontWeight = FontWeight.Black, maxLines = 1)
                Text("${order.client} • ${workStatusLabel(order.status)}", color = p.textMuted, fontSize = 12.sp, maxLines = 1)
                if (order.dueDate.isNotBlank()) Text("Consegna ${order.dueDate}${if (overdue) " • SCADUTA" else ""}", color = if (overdue) p.danger else p.textMuted, fontSize = 11.sp, fontWeight = if (overdue) FontWeight.Black else FontWeight.Normal)
            }
            IconButton(onClick = { onEdit(order) }) { Icon(Icons.Rounded.Edit, "Modifica ordine", tint = p.primary) }
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
        Text(count.toString(), color = if (count > 0) color else MaterialTheme.colorScheme.onSurface.copy(alpha = .5f), fontWeight = FontWeight.Black, fontSize = 20.sp)
        Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .6f))
    }
}
