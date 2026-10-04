package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

private data class ZenthaItemDraft(
    val productId: Long? = null,
    val productName: String = "",
    val quantity: String = "1",
    val unitPrice: String = "",
    val unitCost: String = ""
)

private data class ZenthaExtraDraft(val label: String = "", val amount: String = "")

@Composable
fun ZenthaScreen(
    vm: SpectroViewModel,
    p: SpectroPalette,
    initialOpenOrderId: Long? = null,
    onOpenConsumed: () -> Unit = {}
) {
    var mode by remember { mutableStateOf("ORDERS") }
    var query by remember { mutableStateOf("") }
    var newProduct by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<ZenthaProduct?>(null) }
    var newOrder by remember { mutableStateOf(false) }
    var editingOrder by remember { mutableStateOf<ZenthaOrder?>(null) }
    var detailOrder by remember { mutableStateOf<ZenthaOrder?>(null) }

    LaunchedEffect(initialOpenOrderId, vm.state.zenthaOrders) {
        if (initialOpenOrderId != null) {
            vm.state.zenthaOrders.firstOrNull { it.id == initialOpenOrderId }?.let { detailOrder = it }
            onOpenConsumed()
        }
    }

    val orders = vm.state.zenthaOrders.filter { query.isBlank() || it.clientName.contains(query, true) || it.orderCode.contains(query, true) || it.notes.contains(query, true) }
    val products = vm.state.zenthaProducts.filter { query.isBlank() || it.name.contains(query, true) || it.sku.contains(query, true) || it.category.contains(query, true) }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text("ZENTHA", fontSize = 28.sp, fontWeight = FontWeight.Black); Text("Ordini sportivi, catalogo, margini e pagamenti.", color = p.textMuted) }
                FilledIconButton(onClick = { if (mode == "ORDERS") newOrder = true else newProduct = true }) { Icon(Icons.Rounded.Add, null) }
            }
            Spacer(Modifier.height(12.dp)); SegmentedChoice(listOf("ORDERS" to "ORDINI", "PRODUCTS" to "PRODOTTI"), mode, p) { mode = it }
            Spacer(Modifier.height(10.dp)); OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth(), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text(if (mode == "ORDERS") "Cliente o ordine…" else "Prodotto, SKU o categoria…") }, singleLine = true, shape = RoundedCornerShape(16.dp))
        }
        if (mode == "ORDERS") {
            item {
                val totals = orders.map { vm.zenthaTotals(it.id) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Metric("Ordini", orders.size.toString(), modifier = Modifier.weight(1f))
                    Metric("Vendite", euro(totals.sumOf { it.revenue }), modifier = Modifier.weight(1f))
                    Metric("Utile", euro(totals.sumOf { it.profit }), valueColor = p.success, modifier = Modifier.weight(1f))
                }
            }
            if (orders.isEmpty()) item { EmptyState(Icons.Rounded.SportsVolleyball, "Nessun ordine Zentha", "Premi + per creare il primo ordine") }
            items(orders, key = { it.id }) { order ->
                val t = vm.zenthaTotals(order.id)
                SpectroCard(p = p, onClick = { detailOrder = order }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(order.orderCode.ifBlank { "Ordine Zentha" }, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("${order.clientName} • ${order.orderDate} • consegna ${order.dueDate.ifBlank { "-" }}", color = p.textMuted, fontSize = 12.sp) }
                        AssistChip(onClick = {}, label = { Text(zenthaStatusLabel(order.status), fontSize = 9.sp) })
                    }
                    Spacer(Modifier.height(10.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Vendite", euro(t.revenue), modifier = Modifier.weight(1f)); Metric("Utile", euro(t.profit), valueColor = if (t.profit >= 0) p.success else p.danger, modifier = Modifier.weight(1f)); Metric("Da incassare", euro(t.outstanding), valueColor = if (t.outstanding > 0) p.warning else p.success, modifier = Modifier.weight(1f)) }
                }
            }
        } else {
            if (products.isEmpty()) item { EmptyState(Icons.Rounded.Inventory, "Catalogo vuoto", "Premi + per aggiungere Canotte, Kit, T-shirt e altri prodotti") }
            items(products, key = { it.id }) { product ->
                SpectroCard(p = p, onClick = { editingProduct = product }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) { Text(product.name, fontWeight = FontWeight.Black); Text(listOf(product.sku, product.category).filter { it.isNotBlank() }.joinToString(" • "), color = p.textMuted, fontSize = 12.sp) }
                        if (!product.active) Text("DISATTIVO", color = p.warning, fontSize = 10.sp, fontWeight = FontWeight.Black)
                    }
                    Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Costo", euro(product.standardCost), modifier = Modifier.weight(1f)); Metric("Prezzo", euro(product.standardPrice), modifier = Modifier.weight(1f)); Metric("Margine", euro(product.standardPrice - product.standardCost), valueColor = p.success, modifier = Modifier.weight(1f)) }
                }
            }
        }
    }

    if (newProduct) ZenthaProductDialog(vm, p, null) { newProduct = false }
    editingProduct?.let { ZenthaProductDialog(vm, p, it) { editingProduct = null } }
    if (newOrder) ZenthaOrderDialog(vm, p, null) { newOrder = false }
    editingOrder?.let { ZenthaOrderDialog(vm, p, it) { editingOrder = null } }
    detailOrder?.let { order -> ZenthaOrderDetailDialog(vm, p, order, onDismiss = { detailOrder = null }, onEdit = { detailOrder = null; editingOrder = order }) }
}

@Composable
private fun ZenthaProductDialog(vm: SpectroViewModel, p: SpectroPalette, editing: ZenthaProduct?, onDismiss: () -> Unit) {
    var name by remember(editing?.id) { mutableStateOf(editing?.name.orEmpty()) }
    var sku by remember(editing?.id) { mutableStateOf(editing?.sku.orEmpty()) }
    var category by remember(editing?.id) { mutableStateOf(editing?.category.orEmpty()) }
    var cost by remember(editing?.id) { mutableStateOf(editing?.standardCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var price by remember(editing?.id) { mutableStateOf(editing?.standardPrice?.takeIf { it > 0 }?.toString().orEmpty()) }
    var description by remember(editing?.id) { mutableStateOf(editing?.description.orEmpty()) }
    var active by remember(editing?.id) { mutableStateOf(editing?.active ?: true) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuovo prodotto Zentha" else "Modifica prodotto", fontWeight = FontWeight.Black) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { SpectroTextField("Nome articolo", name, { name = it }, placeholder = "Es. Canotta Uomo Double"); SpectroTextField("SKU", sku, { sku = it }); SpectroTextField("Categoria", category, { category = it }, placeholder = "Canotta, T-shirt, Kit…"); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Costo €", cost, { cost = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Prezzo €", price, { price = it }, Modifier.weight(1f), numeric = true) }; SpectroTextField("Descrizione", description, { description = it }, singleLine = false); Row(verticalAlignment = Alignment.CenterVertically) { Text("Prodotto attivo", Modifier.weight(1f)); Switch(active, { active = it }) } } },
        confirmButton = { Button(onClick = { if (name.isNotBlank()) { vm.saveZenthaProduct(ZenthaProduct(editing?.id ?: 0, name.trim(), sku.trim(), category.trim(), cost.toDoubleSafe(), price.toDoubleSafe(), description.trim(), editing?.imagePath.orEmpty(), active)); onDismiss() } }) { Text("Salva") } },
        dismissButton = { Row { if (editing != null) TextButton(onClick = { vm.deleteZenthaProduct(editing.id); onDismiss() }) { Text("Elimina", color = p.danger) }; TextButton(onClick = onDismiss) { Text("Annulla") } } }
    )
}

@Composable
private fun ZenthaOrderDialog(vm: SpectroViewModel, p: SpectroPalette, editing: ZenthaOrder?, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var clientId by remember(editing?.id) { mutableStateOf(editing?.clientId) }
    var clientName by remember(editing?.id) { mutableStateOf(editing?.clientName.orEmpty()) }
    var date by remember(editing?.id) { mutableStateOf(editing?.orderDate ?: LocalDate.now().toString()) }
    var due by remember(editing?.id) { mutableStateOf(editing?.dueDate.orEmpty()) }
    var status by remember(editing?.id) { mutableStateOf(editing?.status ?: ZenthaOrderStatus.CONFIRMED) }
    var notes by remember(editing?.id) { mutableStateOf(editing?.notes.orEmpty()) }
    var attachments by remember(editing?.id) { mutableStateOf<List<String>>(editing?.attachments ?: emptyList()) }
    var splitA by remember(editing?.id) { mutableStateOf((editing?.splitPercentA ?: vm.state.settings.zenthaSplitA).toString()) }
    var splitB by remember(editing?.id) { mutableStateOf((editing?.splitPercentB ?: vm.state.settings.zenthaSplitB).toString()) }
    var drafts by remember(editing?.id, vm.state.zenthaItems) {
        mutableStateOf<List<ZenthaItemDraft>>(
            editing?.let { o ->
                vm.state.zenthaItems
                    .filter { it.orderId == o.id }
                    .map { ZenthaItemDraft(it.productId, it.productName, it.quantity.toString(), it.unitPrice.toString(), it.unitCost.toString()) }
                    .takeIf { it.isNotEmpty() }
            } ?: listOf(ZenthaItemDraft())
        )
    }
    var extras by remember(editing?.id, vm.state.zenthaExtraCosts) {
        mutableStateOf<List<ZenthaExtraDraft>>(
            editing?.let { o ->
                vm.state.zenthaExtraCosts
                    .filter { it.orderId == o.id }
                    .map { ZenthaExtraDraft(it.label, it.amount.toString()) }
            } ?: emptyList()
        )
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris -> attachments = attachments + uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() } }

    val revenue = drafts.sumOf { it.quantity.toDoubleSafe() * it.unitPrice.toDoubleSafe() }
    val costs = drafts.sumOf { it.quantity.toDoubleSafe() * it.unitCost.toDoubleSafe() }
    val extraTotal = extras.sumOf { it.amount.toDoubleSafe() }
    val profit = revenue - costs - extraTotal

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuovo ordine Zentha" else "Modifica ${editing.orderCode}", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 680.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SimpleSelectField("Cliente", clientName.ifBlank { "Seleziona cliente" }, vm.state.clients.map { it.id to it.name }) { id -> vm.state.clients.firstOrNull { it.id == id }?.let { clientId = it.id; clientName = it.name } } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { WheelDateField("Data ordine", date, { date = it }, Modifier.weight(1f)); WheelDateField("Consegna", due, { due = it }, Modifier.weight(1f), allowEmpty = true) } }
                item { SimpleSelectField("Stato", zenthaStatusLabel(status), ZenthaOrderStatus.entries.map { it to zenthaStatusLabel(it) }) { status = it } }
                item { SectionTitle("Prodotti", "+ Aggiungi") { drafts = drafts + ZenthaItemDraft() } }
                items(drafts.size) { index ->
                    val d = drafts[index]
                    SpectroCard(p = p) {
                        SimpleSelectField("Prodotto", d.productName.ifBlank { "Seleziona prodotto" }, vm.state.zenthaProducts.filter { it.active }.map { it.id to it.name }) { id ->
                            val prod = vm.state.zenthaProducts.firstOrNull { it.id == id }
                            if (prod != null) drafts = drafts.toMutableList().also { it[index] = d.copy(productId = prod.id, productName = prod.name, unitPrice = prod.standardPrice.toString(), unitCost = prod.standardCost.toString()) }
                        }
                        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SpectroTextField("Q.tà", d.quantity, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(quantity = v) } }, Modifier.weight(.7f), numeric = true); SpectroTextField("Prezzo", d.unitPrice, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(unitPrice = v) } }, Modifier.weight(1f), numeric = true); SpectroTextField("Costo", d.unitCost, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(unitCost = v) } }, Modifier.weight(1f), numeric = true) }
                        if (drafts.size > 1) TextButton(onClick = { drafts = drafts.filterIndexed { i, _ -> i != index } }) { Text("Rimuovi prodotto", color = p.danger) }
                    }
                }
                item { SectionTitle("Costi extra", "+ Aggiungi") { extras = extras + ZenthaExtraDraft() } }
                items(extras.size) { index -> val e = extras[index]; Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) { SpectroTextField("Voce", e.label, { v -> extras = extras.toMutableList().also { it[index] = e.copy(label = v) } }, Modifier.weight(1.4f)); SpectroTextField("Importo €", e.amount, { v -> extras = extras.toMutableList().also { it[index] = e.copy(amount = v) } }, Modifier.weight(1f), numeric = true); IconButton(onClick = { extras = extras.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) } } }
                item { OutlinedButton(onClick = { picker.launch("*/*") }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AttachFile, null); Spacer(Modifier.width(6.dp)); Text("Allega PDF o immagine ordine") } }
                if (attachments.isNotEmpty()) item { Text("${attachments.size} allegati salvati", color = p.textMuted, fontWeight = FontWeight.Bold) }
                item { SpectroTextField("Note", notes, { notes = it }, singleLine = false) }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Quota A %", splitA, { splitA = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Quota B %", splitB, { splitB = it }, Modifier.weight(1f), numeric = true) } }
                item { SpectroCard(p = p) { Text("Riepilogo", fontWeight = FontWeight.Black); Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Vendite", euro(revenue), modifier = Modifier.weight(1f)); Metric("Costi", euro(costs + extraTotal), modifier = Modifier.weight(1f)); Metric("Utile", euro(profit), valueColor = if (profit >= 0) p.success else p.danger, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(6.dp)); Text("Quota A ${euro(profit * splitA.toDoubleSafe() / 100)} • Quota B ${euro(profit * splitB.toDoubleSafe() / 100)}", color = p.textMuted) } }
            }
        },
        confirmButton = {
            Button(onClick = {
                val a = splitA.toDoubleSafe(); val b = splitB.toDoubleSafe()
                val items = drafts.filter { it.productName.isNotBlank() && it.quantity.toDoubleSafe() > 0 }.map { ZenthaOrderItem(orderId = editing?.id ?: 0, productId = it.productId, productName = it.productName, quantity = it.quantity.toDoubleSafe().toInt(), unitPrice = it.unitPrice.toDoubleSafe(), unitCost = it.unitCost.toDoubleSafe()) }
                val extraRows = extras.filter { it.label.isNotBlank() && it.amount.toDoubleSafe() > 0 }.map { ZenthaExtraCost(orderId = editing?.id ?: 0, label = it.label.trim(), amount = it.amount.toDoubleSafe()) }
                if (clientName.isNotBlank() && items.isNotEmpty() && kotlin.math.abs(a + b - 100.0) < .01) {
                    vm.saveZenthaOrder(ZenthaOrder(editing?.id ?: 0, editing?.orderCode.orEmpty(), clientId, clientName, date, due, status, notes.trim(), attachments, a, b), items, extraRows); onDismiss()
                }
            }) { Text("Salva ordine") }
        },
        dismissButton = { Row { if (editing != null) TextButton(onClick = { vm.deleteZenthaOrder(editing.id); onDismiss() }) { Text("Elimina", color = p.danger) }; TextButton(onClick = onDismiss) { Text("Annulla") } } }
    )
}

@Composable
private fun ZenthaOrderDetailDialog(vm: SpectroViewModel, p: SpectroPalette, order: ZenthaOrder, onDismiss: () -> Unit, onEdit: () -> Unit) {
    val context = LocalContext.current
    var paymentOpen by remember { mutableStateOf(false) }
    val orderItems = vm.state.zenthaItems.filter { it.orderId == order.id }
    val extras = vm.state.zenthaExtraCosts.filter { it.orderId == order.id }
    val payments = vm.state.zenthaPayments.filter { it.orderId == order.id }
    val t = vm.zenthaTotals(order.id)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(order.orderCode, fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("${order.clientName} • consegna ${order.dueDate.ifBlank { "-" }}", color = p.textMuted) }
                items(orderItems, key = { it.id }) { i -> SpectroCard(p = p) { Text("${i.productName} × ${i.quantity}", fontWeight = FontWeight.Black); Text("Vendita ${euro(i.revenue)} • Costo ${euro(i.cost)} • Margine ${euro(i.margin)}", color = p.textMuted, fontSize = 12.sp) } }
                if (extras.isNotEmpty()) item { Text("Costi extra: ${extras.joinToString { "${it.label} ${euro(it.amount)}" }}", color = p.textMuted) }
                item { SpectroCard(p = p) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Vendite", euro(t.revenue), modifier = Modifier.weight(1f)); Metric("Utile", euro(t.profit), valueColor = p.success, modifier = Modifier.weight(1f)); Metric("Residuo", euro(t.outstanding), valueColor = if (t.outstanding > 0) p.warning else p.success, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp)); Text("Divisione utile: A ${euro(t.shareA)} • B ${euro(t.shareB)}", fontWeight = FontWeight.Bold) } }
                item { SectionTitle("Pagamenti") }
                items(payments, key = { it.id }) { pay -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${pay.date} • ${euro(pay.amount)}", fontWeight = FontWeight.Bold); Text(pay.method.name, color = p.textMuted, fontSize = 11.sp) }; IconButton(onClick = { vm.deleteZenthaPayment(pay.id) }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) } } }
                item { OutlinedButton(onClick = { paymentOpen = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Payments, null); Spacer(Modifier.width(6.dp)); Text("Registra pagamento") } }
                item {
                    OutlinedButton(
                        onClick = { ReportExporter.shareZenthaOrder(context, order, orderItems, extras, payments) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Esporta ordine PDF") }
                }
                if (order.attachments.isNotEmpty()) item { Text("Allegati ordine: ${order.attachments.size}", fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text("Modifica") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
    if (paymentOpen) ZenthaPaymentDialog(vm, order.id) { paymentOpen = false }
}

@Composable
private fun ZenthaPaymentDialog(vm: SpectroViewModel, orderId: Long, onDismiss: () -> Unit) {
    var date by remember { mutableStateOf(LocalDate.now().toString()) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf(PaymentMethod.BANK_TRANSFER) }
    var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Registra pagamento") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) { WheelDateField("Data", date, { date = it }); SpectroTextField("Importo €", amount, { amount = it }, numeric = true); SimpleSelectField("Metodo", method.name, PaymentMethod.entries.filter { it != PaymentMethod.UNSPECIFIED }.map { it to it.name }) { method = it }; SpectroTextField("Note", notes, { notes = it }) } }, confirmButton = { Button(onClick = { if (amount.toDoubleSafe() > 0) { vm.addZenthaPayment(ZenthaPayment(orderId = orderId, date = date, amount = amount.toDoubleSafe(), method = method, notes = notes.trim())); onDismiss() } }) { Text("Registra") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } })
}

fun zenthaStatusLabel(v: ZenthaOrderStatus): String = when (v) {
    ZenthaOrderStatus.DRAFT -> "Bozza"
    ZenthaOrderStatus.CONFIRMED -> "Confermato"
    ZenthaOrderStatus.GRAPHICS -> "Grafica"
    ZenthaOrderStatus.WAITING_APPROVAL -> "Attesa approvazione"
    ZenthaOrderStatus.PRODUCTION -> "Produzione"
    ZenthaOrderStatus.READY -> "Pronto"
    ZenthaOrderStatus.DELIVERED -> "Consegnato"
    ZenthaOrderStatus.CLOSED -> "Chiuso"
    ZenthaOrderStatus.CANCELLED -> "Annullato"
}
