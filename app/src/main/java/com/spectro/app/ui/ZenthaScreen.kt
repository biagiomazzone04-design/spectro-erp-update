@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.spectro.app.ui

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import java.time.LocalDateTime

internal fun String.zMoney(): Double = if(isBlank()) 0.0 else replace(',', '.').toDoubleOrNull() ?: Double.NaN
internal fun String.zInt(): Int = toIntOrNull() ?: -1
fun zenthaStatusLabel(v: ZenthaOrderStatus) = v.label()

@Composable
internal fun ZenthaEditor(title: String, vm: SpectroViewModel, onDismiss: () -> Unit, onSave: (() -> Unit)? = null, content: LazyListScope.() -> Unit) {
    AlertDialog(onDismissRequest = { if(!vm.state.saving) onDismiss() }, title = { Text(title, fontWeight = FontWeight.Black) },
        text = { LazyColumn(Modifier.fillMaxWidth().heightIn(max = 620.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            vm.state.message?.let { message -> item { Text(message) } }
            content()
        } },
        confirmButton = { if(onSave != null) Button(enabled = !vm.state.saving, onClick = onSave) { Text(if(vm.state.saving) "Salvataggio…" else "Salva") } },
        dismissButton = { TextButton(enabled = !vm.state.saving, onClick = onDismiss) { Text("Chiudi") } })
}

@Composable
internal fun ZenthaAttachments(paths: List<String>, mimeType: String = "*/*", onChange: ((List<String>) -> Unit)? = null) {
    val context = LocalContext.current
    var error by remember { mutableStateOf<String?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        runCatching { uris.map { AttachmentStore.importToPrivateStorage(context, it) } }.onSuccess { onChange?.invoke(paths + it) }.onFailure { error = it.message }
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        paths.forEachIndexed { i, path ->
            Row {
                TextButton(onClick = { runCatching {
                    val uri = AttachmentStore.shareUri(context, path)
                    val type = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(java.io.File(path).extension.lowercase()) ?: "application/octet-stream"
                    context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, type).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                }.onFailure { error = "Nessuna applicazione disponibile per aprire l'allegato." } }, modifier = Modifier.weight(1f)) { Text("Allegato ${i+1} • ${java.io.File(path).name}", maxLines = 2) }
                if(onChange != null) TextButton(onClick = { onChange(paths.filterIndexed { index, _ -> index != i }) }) { Text("Rimuovi") }
            }
        }
        if(onChange != null) OutlinedButton(onClick = { picker.launch(mimeType) }) { Text("Aggiungi foto, mockup o documento") }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
fun ZenthaScreen(vm: SpectroViewModel, p: SpectroPalette, initialOpenOrderId: Long? = null, onOpenConsumed: () -> Unit = {}) {
    var section by remember { mutableStateOf("Dashboard") }
    var query by remember { mutableStateOf("") }
    var orderId by remember { mutableStateOf<Long?>(null) }
    var editOrder by remember { mutableStateOf<ZenthaOrder?>(null) }
    var createOrder by remember { mutableStateOf(false) }
    var product by remember { mutableStateOf<ZenthaProduct?>(null) }
    var createProduct by remember { mutableStateOf(false) }
    var variantsProduct by remember { mutableStateOf<ZenthaProduct?>(null) }
    var priceList by remember { mutableStateOf<ZenthaPriceList?>(null) }
    var createList by remember { mutableStateOf(false) }
    var client by remember { mutableStateOf<Client?>(null) }
    var balance by remember { mutableStateOf<ZenthaStockBalance?>(null) }
    var period by remember { mutableStateOf(LocalDate.now().toString().take(7)) }
    val s = vm.snapshot()
    val data = vm.state.zentha
    LaunchedEffect(initialOpenOrderId) { if(initialOpenOrderId != null) { orderId = initialOpenOrderId; onOpenConsumed() } }
    val orders = s.zenthaOrders.filter { (it.clientName + " " + it.orderCode + " " + it.notes).contains(query, true) }.sortedByDescending { it.orderDate }
    val open = s.zenthaOrders.filter { !it.status.isFinished() }
    val low = data.balances.filter { it.minimumQuantity > 0 && it.quantityAvailable <= it.minimumQuantity }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { Text("ZENTHA ERP 2.0", fontSize = 27.sp, fontWeight = FontWeight.Black); Text("Sport, squadre e produzione", color = p.textMuted) }
        item { Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("Dashboard", "Ordini", "Clienti", "Magazzino", "Prodotti", "Listini", "Produzione", "Pagamenti", "Statistiche").forEach { tab -> FilterChip(section == tab, { section = tab }, label = { Text(tab) }) }
        } }
        if(section !in listOf("Dashboard", "Statistiche")) item { SpectroTextField("Cerca", query, { query = it }) }
        when(section) {
            "Dashboard" -> {
                val month = zenthaStatistics(s, LocalDate.now().toString().take(7))
                item { ZenthaKpis(p, listOf("Ordini aperti" to open.size.toString(), "Attesa approvazione" to open.count { it.status == ZenthaOrderStatus.WAITING_APPROVAL }.toString(), "In produzione" to open.count { it.status in setOf(ZenthaOrderStatus.PRODUCTION, ZenthaOrderStatus.QUALITY_CHECK) }.toString(), "In ritardo" to open.count { it.dueDate.isNotBlank() && it.dueDate < LocalDate.now().toString() }.toString(), "Pronti" to open.count { it.status == ZenthaOrderStatus.READY }.toString(), "Scorte basse" to low.size.toString(), "Da incassare" to euro(zenthaStatistics(s).outstanding), "Utile mese" to euro(month.profit))) }
                item { Button(onClick = { createOrder = true }, modifier = Modifier.fillMaxWidth()) { Text("Nuovo ordine") } }
                items(open.sortedBy { it.dueDate.ifBlank { "9999" } }.take(8), key = { it.id }) { order -> ZenthaOrderCard(order, vm, p) { orderId = order.id } }
            }
            "Ordini", "Produzione", "Pagamenti" -> {
                if(section == "Ordini") item { Button(onClick = { createOrder = true }) { Text("Nuovo ordine") } }
                val filtered = orders.filter { when(section) {
                    "Produzione" -> !it.status.isFinished()
                    "Pagamenti" -> it.status != ZenthaOrderStatus.CANCELLED
                    else -> true
                } }
                if(filtered.isEmpty()) item { Text("Nessun ordine corrispondente.") }
                items(filtered, key = { it.id }) { order -> ZenthaOrderCard(order, vm, p) { orderId = order.id } }
            }
            "Prodotti" -> {
                item { Button(onClick = { createProduct = true }) { Text("Nuovo prodotto") } }
                items(s.zenthaProducts.filter { (it.name + it.sku + it.category + it.line).contains(query, true) }, key = { it.id }) { prod ->
                    SpectroCard(p = p) {
                        Text(prod.name, fontWeight = FontWeight.Black); Text("${prod.sku} • ${prod.category} • ${prod.line}")
                        if(!prod.active) Text("DISATTIVO", color = p.warning)
                        if(prod.imagePath.isNotBlank()) ImageStrip(listOf(prod.imagePath))
                        Text("Costo reale ${euro(prod.realCost)} • Prezzo ${euro(prod.standardPrice)}")
                        TextButton(onClick = { product = prod }) { Text("Modifica prodotto e costi") }
                        OutlinedButton(onClick = { variantsProduct = prod }) { Text("Varianti / taglie (${data.variants.count { it.productId == prod.id }})") }
                    }
                }
            }
            "Magazzino" -> {
                item { Text("Giacenze Zentha in pezzi. Lo scarico ordine si conferma dalla scheda ordine; le modifiche successive riallineano solo la differenza.", color = p.textMuted) }
                item { Text("${low.size} varianti sotto scorta • valore ${euro(zenthaStatistics(s).stockValue)}", fontWeight = FontWeight.Bold) }
                items(data.balances.filter { b -> data.variants.firstOrNull { it.id == b.variantId }?.let { v -> (v.label + v.sku + s.zenthaProducts.firstOrNull { it.id == v.productId }?.name.orEmpty()).contains(query, true) } ?: false }, key = { it.id }) { b ->
                    val v = data.variants.first { it.id == b.variantId }
                    SpectroCard(p = p, onClick = { balance = b }) {
                        Text(s.zenthaProducts.firstOrNull { it.id == v.productId }?.name.orEmpty(), fontWeight = FontWeight.Black)
                        Text("${v.sku} • ${v.label}")
                        Text("${b.quantityAvailable} pezzi • soglia ${b.minimumQuantity} • ${b.location}", color = if(b in low) p.warning else p.text)
                        Text("Apri movimenti / carico / rettifica", color = p.primary)
                    }
                }
            }
            "Listini" -> {
                item { Button(onClick = { createList = true }) { Text("Nuovo listino personalizzato") } }
                items(data.priceLists.filter { it.name.contains(query, true) }, key = { it.id }) { list -> SpectroCard(p = p, onClick = { priceList = list }) {
                    Text(list.name, fontWeight = FontWeight.Black); Text(list.description)
                    Text("${data.priceRules.count { it.priceListId == list.id }} regole • ${if(list.active) "Attivo" else "Disattivo"}")
                } }
            }
            "Clienti" -> {
                item { Text("Anagrafiche condivise con Spectro; listini e prezzi cliente separati per Zentha.", color = p.textMuted) }
                items(s.clients.filter { it.name.contains(query, true) }, key = { it.id }) { c -> SpectroCard(p = p, onClick = { client = c }) {
                    Text(c.name, fontWeight = FontWeight.Black)
                    val profile = data.clientProfiles.firstOrNull { it.clientId == c.id }
                    Text(data.priceLists.firstOrNull { it.id == profile?.assignedPriceListId }?.name ?: "Listino Standard")
                    Text("${s.zenthaOrders.count { c.matches(it.clientId, it.clientName) }} ordini • Apri listino e prezzi riservati")
                } }
            }
            "Statistiche" -> {
                val stats = zenthaStatistics(s, period)
                item { SpectroTextField("Periodo: AAAA-MM, AAAA oppure vuoto per tutti", period, { period = it }) }
                item { ZenthaKpis(p, listOf("Vendite" to euro(stats.revenue), "Utile" to euro(stats.profit), "Pagato" to euro(stats.paid), "Da incassare" to euro(stats.outstanding), "Valore stock attuale" to euro(stats.stockValue), "Giorni ordine–consegna" to (stats.averageDeliveryDays?.let { "%.1f".format(it) } ?: "Non disponibile"))) }
                item { Text("Margini prodotto prima di sconti e costi generali ordine. Tempi calcolati solo sulle consegne con data registrata.", color = p.textMuted) }
                listOf("Vendite mensili" to stats.byMonth, "Vendite clienti" to stats.byClient, "Prodotti più venduti (pezzi)" to stats.byProduct, "Taglie (pezzi)" to stats.bySize, "Margine prodotti" to stats.productMargins).forEach { (title, values) ->
                    item { SectionTitle(title) }
                    items(values) { (label, value) -> Text("$label: ${if(title.contains("pezzi")) value.toInt() else euro(value)}") }
                }
                item { SectionTitle("Margine per ordine") }
                items(s.zenthaOrders.filter { it.status != ZenthaOrderStatus.CANCELLED && it.orderDate.startsWith(period) }, key = { "margin-${it.id}" }) { o ->
                    val t = vm.zenthaTotals(o.id)
                    SpectroCard(p = p, onClick = { orderId = o.id }) { Text("${o.orderCode} • ${o.clientName}"); Text("${euro(t.profit)} • ${"%.1f".format(t.marginPercent)}%") }
                }
            }
        }
    }
    if(createProduct) ZenthaProductEditor(vm, p, null) { createProduct = false }
    product?.let { ZenthaProductEditor(vm, p, it) { product = null } }
    variantsProduct?.let { ZenthaVariantsEditor(vm, p, it) { variantsProduct = null } }
    balance?.let { ZenthaStockEditor(vm, p, it.variantId) { balance = null } }
    if(createList) ZenthaListEditor(vm, p, null) { createList = false }
    priceList?.let { ZenthaListEditor(vm, p, it) { priceList = null } }
    client?.let { ZenthaClientEditor(vm, p, it) { client = null } }
    if(createOrder) ZenthaOrderEditor(vm, p, null) { createOrder = false }
    editOrder?.let { ZenthaOrderEditor(vm, p, it) { editOrder = null } }
    orderId?.let { id -> s.zenthaOrders.firstOrNull { it.id == id }?.let { o -> ZenthaOrderDetail(vm, p, o, { orderId = null }, { editOrder = o; orderId = null }) } }
}

@Composable
internal fun ZenthaKpis(p: SpectroPalette, values: List<Pair<String, String>>) {
    SpectroCard(p = p) { values.chunked(2).forEach { pair -> Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        pair.forEach { (label, value) -> Metric(label, value, modifier = Modifier.weight(1f)) }
    }; Spacer(Modifier.height(12.dp)) } }
}
@Composable
private fun ZenthaOrderCard(order: ZenthaOrder, vm: SpectroViewModel, p: SpectroPalette, onClick: () -> Unit) {
    val t = vm.zenthaTotals(order.id)
    SpectroCard(p = p, onClick = onClick) {
        Text(order.orderCode, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Text(order.clientName); Text("${order.orderDate} • ${order.status.label()}", color = p.primary)
        Text("Consegna ${order.dueDate.ifBlank { "—" }}")
        Text("Vendita ${euro(t.revenue)} • Utile ${euro(t.profit)}")
        Text("${t.paymentState.label()} • Residuo ${euro(t.outstanding)}", color = if(t.outstanding > 0) p.warning else p.success)
    }
}
