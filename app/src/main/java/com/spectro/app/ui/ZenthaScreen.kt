package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
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
    val unitCost: String = "",
    val skuSnapshot: String = "",
    val productDescriptionSnapshot: String = "",
    val variant: String = "",
    val color: String = "",
    val sizeBreakdown: String = "",
    val personalization: String = "",
    val productionNotes: String = ""
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
                    Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) { Metric("Costo", euro(product.standardCost), modifier = Modifier.weight(1f)); Metric("Listino", euro(product.standardPrice), modifier = Modifier.weight(1f)); Metric("Margine", euro(product.unitMargin), valueColor = p.success, modifier = Modifier.weight(1f)) }
                    Spacer(Modifier.height(7.dp)); Text("${product.priceListName} • ricarico ${"%.0f".format(product.markupPercent)}% • margine ${"%.1f".format(product.marginPercent)}%", color = p.textMuted, fontSize = 11.sp)
                    if (product.hasCostBreakdown) Text(product.costComponents().joinToString(" • ") { "${it.first} ${euro(it.second)}" }, color = p.textMuted, fontSize = 10.sp)
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
    var fabric by remember(editing?.id) { mutableStateOf(editing?.fabricCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var rib by remember(editing?.id) { mutableStateOf(editing?.ribCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var lining by remember(editing?.id) { mutableStateOf(editing?.liningCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var print by remember(editing?.id) { mutableStateOf(editing?.printCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var accessories by remember(editing?.id) { mutableStateOf(editing?.accessoryCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var cutting by remember(editing?.id) { mutableStateOf(editing?.cuttingCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var sewing by remember(editing?.id) { mutableStateOf(editing?.sewingCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var label by remember(editing?.id) { mutableStateOf(editing?.labelCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var packaging by remember(editing?.id) { mutableStateOf(editing?.packagingCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var other by remember(editing?.id) { mutableStateOf(editing?.otherCost?.takeIf { it > 0 }?.toString().orEmpty()) }
    var priceList by remember(editing?.id) { mutableStateOf(editing?.priceListName ?: "Listino standard") }
    var markup by remember(editing?.id) { mutableStateOf(editing?.markupPercent?.takeIf { it > 0 }?.toString().orEmpty()) }
    val detailedTotal = listOf(fabric, rib, lining, print, accessories, cutting, sewing, label, packaging, other).sumOf { it.toDoubleSafe() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editing == null) "Nuovo prodotto Zentha" else "Modifica prodotto", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 660.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { SpectroTextField("Nome articolo", name, { name = it }, placeholder = "Es. Maglia uomo GM Double") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Codice articolo", sku, { sku = it }, Modifier.weight(1.2f)); SpectroTextField("Categoria", category, { category = it }, Modifier.weight(1f)) } }
                item { SpectroTextField("Descrizione / tessuto", description, { description = it }, singleLine = false) }
                item { SectionTitle("Analisi costo unitario") }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Tessuto €", fabric, { fabric = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Costa €", rib, { rib = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Fodera / inserti €", lining, { lining = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Stampa €", print, { print = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Accessori €", accessories, { accessories = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Taglio €", cutting, { cutting = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Cucito / confezione €", sewing, { sewing = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Etichetta €", label, { label = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Imballo €", packaging, { packaging = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Altri costi €", other, { other = it }, Modifier.weight(1f), numeric = true) } }
                item {
                    SpectroCard(p = p) {
                        Text("Costo analitico", color = p.textMuted, fontSize = 11.sp)
                        Text(euro(detailedTotal), fontWeight = FontWeight.Black, fontSize = 21.sp, color = p.warning)
                        if (detailedTotal <= 0.0) Text("Se non compili il dettaglio verrà usato il costo totale manuale.", color = p.textMuted, fontSize = 10.sp)
                    }
                }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Costo totale €", cost, { cost = it }, Modifier.weight(1f), numeric = true); SpectroTextField("Prezzo listino €", price, { price = it }, Modifier.weight(1f), numeric = true) } }
                item { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { SpectroTextField("Nome listino", priceList, { priceList = it }, Modifier.weight(1.4f)); SpectroTextField("Ricarico %", markup, { markup = it }, Modifier.weight(.8f), numeric = true) } }
                if (!editing?.sourceReference.isNullOrBlank()) item { Text("Fonte: ${editing.sourceReference}", color = p.textMuted, fontSize = 10.sp) }
                item { Row(verticalAlignment = Alignment.CenterVertically) { Text("Prodotto attivo", Modifier.weight(1f)); Switch(active, { active = it }) } }
            }
        },
        confirmButton = { Button(onClick = {
            if (name.isNotBlank()) {
                vm.saveZenthaProduct(
                    ZenthaProduct(
                        id = editing?.id ?: 0,
                        name = name.trim(),
                        sku = sku.trim(),
                        category = category.trim(),
                        standardCost = cost.toDoubleSafe(),
                        standardPrice = price.toDoubleSafe(),
                        description = description.trim(),
                        imagePath = editing?.imagePath.orEmpty(),
                        active = active,
                        fabricCost = fabric.toDoubleSafe(),
                        ribCost = rib.toDoubleSafe(),
                        liningCost = lining.toDoubleSafe(),
                        printCost = print.toDoubleSafe(),
                        accessoryCost = accessories.toDoubleSafe(),
                        cuttingCost = cutting.toDoubleSafe(),
                        sewingCost = sewing.toDoubleSafe(),
                        labelCost = label.toDoubleSafe(),
                        packagingCost = packaging.toDoubleSafe(),
                        otherCost = other.toDoubleSafe(),
                        priceListName = priceList.trim().ifBlank { "Listino standard" },
                        markupPercent = markup.toDoubleSafe(),
                        sourceReference = editing?.sourceReference.orEmpty()
                    )
                )
                onDismiss()
            }
        }) { Text("Salva") } },
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
                    .map {
                        ZenthaItemDraft(
                            productId = it.productId,
                            productName = it.productName,
                            quantity = it.quantity.toString(),
                            unitPrice = it.unitPrice.toString(),
                            unitCost = it.unitCost.toString(),
                            skuSnapshot = it.skuSnapshot,
                            productDescriptionSnapshot = it.productDescriptionSnapshot,
                            variant = it.variant,
                            color = it.color,
                            sizeBreakdown = it.sizeBreakdown,
                            personalization = it.personalization,
                            productionNotes = it.productionNotes
                        )
                    }
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
                            if (prod != null) drafts = drafts.toMutableList().also {
                                it[index] = d.copy(
                                    productId = prod.id,
                                    productName = prod.name,
                                    unitPrice = prod.standardPrice.toString(),
                                    unitCost = prod.standardCost.toString(),
                                    skuSnapshot = prod.sku,
                                    productDescriptionSnapshot = prod.description
                                )
                            }
                        }
                        if (d.skuSnapshot.isNotBlank() || d.productDescriptionSnapshot.isNotBlank()) {
                            Spacer(Modifier.height(5.dp)); Text(listOf(d.skuSnapshot, d.productDescriptionSnapshot).filter { it.isNotBlank() }.joinToString(" • "), color = p.textMuted, fontSize = 10.sp)
                        }
                        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SpectroTextField("Q.tà", d.quantity, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(quantity = v) } }, Modifier.weight(.7f), numeric = true); SpectroTextField("Prezzo", d.unitPrice, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(unitPrice = v) } }, Modifier.weight(1f), numeric = true); SpectroTextField("Costo", d.unitCost, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(unitCost = v) } }, Modifier.weight(1f), numeric = true) }
                        Spacer(Modifier.height(8.dp)); Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) { SpectroTextField("Variante / vestibilità", d.variant, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(variant = v) } }, Modifier.weight(1f)); SpectroTextField("Colore", d.color, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(color = v) } }, Modifier.weight(1f)) }
                        Spacer(Modifier.height(8.dp)); SpectroTextField("Taglie e quantità", d.sizeBreakdown, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(sizeBreakdown = v) } }, placeholder = "Es. S×4, M×8, L×6, XL×2", singleLine = false)
                        Spacer(Modifier.height(8.dp)); SpectroTextField("Personalizzazioni", d.personalization, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(personalization = v) } }, placeholder = "Nomi, numeri, sponsor, loghi e grafiche", singleLine = false)
                        Spacer(Modifier.height(8.dp)); SpectroTextField("Note produzione", d.productionNotes, { v -> drafts = drafts.toMutableList().also { it[index] = d.copy(productionNotes = v) } }, placeholder = "Indicazioni tecniche, cuciture e confezionamento", singleLine = false)
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
                val items = drafts.filter { it.productName.isNotBlank() && it.quantity.toDoubleSafe() > 0 }.map {
                    ZenthaOrderItem(
                        orderId = editing?.id ?: 0,
                        productId = it.productId,
                        productName = it.productName,
                        quantity = it.quantity.toDoubleSafe().toInt(),
                        unitPrice = it.unitPrice.toDoubleSafe(),
                        unitCost = it.unitCost.toDoubleSafe(),
                        skuSnapshot = it.skuSnapshot,
                        productDescriptionSnapshot = it.productDescriptionSnapshot,
                        variant = it.variant.trim(),
                        color = it.color.trim(),
                        sizeBreakdown = it.sizeBreakdown.trim(),
                        personalization = it.personalization.trim(),
                        productionNotes = it.productionNotes.trim()
                    )
                }
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
    var exportOpen by remember { mutableStateOf(false) }
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
                items(orderItems, key = { it.id }) { i -> SpectroCard(p = p) {
                    Text(listOf(i.skuSnapshot, i.productName).filter { it.isNotBlank() }.joinToString(" • ") + " × ${i.quantity}", fontWeight = FontWeight.Black)
                    Text("Vendita ${euro(i.revenue)} • Costo ${euro(i.cost)} • Margine ${euro(i.margin)}", color = p.textMuted, fontSize = 12.sp)
                    listOf(
                        "Variante" to i.variant,
                        "Colore" to i.color,
                        "Taglie" to i.sizeBreakdown,
                        "Personalizzazioni" to i.personalization,
                        "Produzione" to i.productionNotes
                    ).filter { it.second.isNotBlank() }.forEach { (label, value) -> Text("$label: $value", color = p.textMuted, fontSize = 11.sp) }
                } }
                if (extras.isNotEmpty()) item { Text("Costi extra: ${extras.joinToString { "${it.label} ${euro(it.amount)}" }}", color = p.textMuted) }
                item { SpectroCard(p = p) { Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { Metric("Vendite", euro(t.revenue), modifier = Modifier.weight(1f)); Metric("Utile", euro(t.profit), valueColor = p.success, modifier = Modifier.weight(1f)); Metric("Residuo", euro(t.outstanding), valueColor = if (t.outstanding > 0) p.warning else p.success, modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(8.dp)); Text("Divisione utile: A ${euro(t.shareA)} • B ${euro(t.shareB)}", fontWeight = FontWeight.Bold) } }
                item { SectionTitle("Pagamenti") }
                items(payments, key = { it.id }) { pay -> Row(verticalAlignment = Alignment.CenterVertically) { Column(Modifier.weight(1f)) { Text("${pay.date} • ${euro(pay.amount)}", fontWeight = FontWeight.Bold); Text(pay.method.name, color = p.textMuted, fontSize = 11.sp) }; IconButton(onClick = { vm.deleteZenthaPayment(pay.id) }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) } } }
                item { OutlinedButton(onClick = { paymentOpen = true }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Payments, null); Spacer(Modifier.width(6.dp)); Text("Registra pagamento") } }
                item {
                    OutlinedButton(
                        onClick = { exportOpen = true },
                        modifier = Modifier.fillMaxWidth()
                    ) { Icon(Icons.Rounded.PictureAsPdf, null); Spacer(Modifier.width(6.dp)); Text("Esporta ordine…") }
                }
                if (order.attachments.isNotEmpty()) item { Text("Allegati ordine: ${order.attachments.size}", fontWeight = FontWeight.Bold) }
            }
        },
        confirmButton = { Button(onClick = onEdit) { Text("Modifica") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Chiudi") } }
    )
    if (paymentOpen) ZenthaPaymentDialog(vm, order.id) { paymentOpen = false }
    if (exportOpen) ZenthaExportDialog(
        onDismiss = { exportOpen = false },
        onExport = { profile ->
            ReportExporter.shareZenthaOrder(context, order, orderItems, extras, payments, vm.state.zenthaProducts, profile)
            exportOpen = false
        }
    )
}

@Composable
private fun ZenthaExportDialog(onDismiss: () -> Unit, onExport: (ZenthaExportProfile) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Esporta ordine Zentha", fontWeight = FontWeight.Black) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Scegli il documento in base al destinatario. I contenuti economici riservati non vengono inclusi nella scheda produzione.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                ExportProfileButton(
                    title = "Contabilità / fatturazione",
                    description = "Articoli, codici, tessuti, quantità, prezzi di vendita, totali e pagamenti.",
                    icon = Icons.AutoMirrored.Rounded.ReceiptLong
                ) { onExport(ZenthaExportProfile.ACCOUNTING) }
                ExportProfileButton(
                    title = "Produzione",
                    description = "Articoli, quantità, taglie, colori, nomi, numeri e personalizzazioni. Nessun prezzo.",
                    icon = Icons.Rounded.Factory
                ) { onExport(ZenthaExportProfile.PRODUCTION) }
                ExportProfileButton(
                    title = "Completo analitico",
                    description = "Dossier interno con ricavi, costi, margini, componenti di costo e incassi.",
                    icon = Icons.Rounded.Analytics
                ) { onExport(ZenthaExportProfile.COMPLETE) }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}

@Composable
private fun ExportProfileButton(title: String, description: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
        Icon(icon, null); Spacer(Modifier.width(10.dp)); Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
            Text(title, fontWeight = FontWeight.Black)
            Text(description, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
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
