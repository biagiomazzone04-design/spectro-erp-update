package com.spectro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.ReportExporter
import com.spectro.app.util.ZenthaDocument
import java.time.LocalDate

@Composable
internal fun ZenthaOrderEditor(vm: SpectroViewModel, p: SpectroPalette, original: ZenthaOrder?, close: () -> Unit) {
    var order by remember { mutableStateOf(original ?: ZenthaOrder(clientName = "", orderDate = LocalDate.now().toString(), status = ZenthaOrderStatus.RECEIVED, splitPercentA = vm.state.settings.zenthaSplitA, splitPercentB = vm.state.settings.zenthaSplitB)) }
    var rows by remember { mutableStateOf(vm.state.zenthaItems.filter { original != null && it.orderId == original.id }) }
    var extras by remember { mutableStateOf(vm.state.zenthaExtraCosts.filter { original != null && it.orderId == original.id }) }
    var rowIndex by remember { mutableStateOf<Int?>(null) }
    var addRow by remember { mutableStateOf(false) }
    var extraIndex by remember { mutableStateOf<Int?>(null) }
    var addExtra by remember { mutableStateOf(false) }
    var discount by remember { mutableStateOf(order.discount.toString()) }
    var shipping by remember { mutableStateOf(order.shippingRevenue.toString()) }
    var other by remember { mutableStateOf(order.otherRevenue.toString()) }
    var split by remember { mutableStateOf(order.splitPercentA.toString()) }
    var pricingError by remember { mutableStateOf<String?>(null) }
    ZenthaEditor("Ordine Zentha", vm, close, {
        vm.saveZenthaOrder(order.copy(discount = discount.zMoney(), shippingRevenue = shipping.zMoney(), otherRevenue = other.zMoney(), splitPercentA = split.zMoney(), splitPercentB = 100 - split.zMoney()), rows, extras, close)
    }) {
        item { SimpleSelectField("Cliente / società", order.clientName.ifBlank { "Seleziona" }, vm.state.clients.map { it.id to it.name }) { id -> order = order.copy(clientId = id, clientName = vm.state.clients.first { it.id == id }.name) } }
        item { SpectroTextField("Nome cliente / società", order.clientName, { order = order.copy(clientId = null, clientName = it) }) }
        item { SpectroTextField("Contatto", order.contact, { order = order.copy(contact = it) }) }
        item { SpectroTextField("Data ordine (AAAA-MM-GG)", order.orderDate, { order = order.copy(orderDate = it) }) }
        item { SpectroTextField("Consegna (AAAA-MM-GG)", order.dueDate, { order = order.copy(dueDate = it) }) }
        item { SectionTitle("Righe prodotto • prezzi e costi salvati nell'ordine") }
        items(rows.size) { i -> val row = rows[i]
            SpectroCard(p = p, onClick = { rowIndex = i }) {
                Text(row.productName, fontWeight = FontWeight.Bold); Text(row.variantLabel)
                Text("${row.quantity} × ${euro(row.unitPrice)} • costo ${euro(row.unitCost)}")
                Text("Ricavo ${euro(row.revenue)} • costo ${euro(row.cost)} • margine ${euro(row.margin)}")
                TextButton(onClick = { rows = rows.filterIndexed { index, _ -> index != i } }) { Text("Rimuovi riga") }
            }
        }
        item { Button(onClick = { addRow = true }) { Text("Aggiungi prodotto") } }
        item { OutlinedButton(onClick = {
            runCatching { rows.map { row -> vm.state.zenthaProducts.firstOrNull { it.id == row.productId }?.let { product -> row.copy(unitPrice = ZenthaBusiness.price(product, row.variantId, rows.filter { it.productId == row.productId }.sumOf { it.quantity }, order.clientId, vm.state.zentha)) } ?: row } }
                .onSuccess { rows = it; pricingError = "Listino applicato alle righe da catalogo." }.onFailure { pricingError = it.message }
        }) { Text("Applica listino a tutte le righe") }
            Text("Le fasce usano la quantità totale del prodotto, sommando le varianti. I prezzi esistenti cambiano solo con questa azione o modificando la riga.")
            pricingError?.let { Text(it) }
        }
        item { SpectroTextField("Sconto totale €", discount, { discount = it }, numeric = true) }
        item { SpectroTextField("Ricavo trasporto €", shipping, { shipping = it }, numeric = true) }
        item { SpectroTextField("Altri ricavi €", other, { other = it }, numeric = true) }
        item { SpectroTextField("Quota utile A % (B = 100 − A)", split, { split = it }, numeric = true) }
        item { SectionTitle("Costi extra: personalizzazioni, packaging, trasporto, altro") }
        items(extras.size) { i -> SpectroCard(p = p, onClick = { extraIndex = i }) { Text("${extras[i].label}: ${euro(extras[i].amount)}"); TextButton(onClick = { extras = extras.filterIndexed { index, _ -> index != i } }) { Text("Rimuovi costo") } } }
        item { OutlinedButton(onClick = { addExtra = true }) { Text("Aggiungi costo extra") } }
        item { SpectroTextField("Note ordine", order.notes, { order = order.copy(notes = it) }, singleLine = false) }
        item { ZenthaAttachments(order.attachments) { order = order.copy(attachments = it) } }
    }
    if(addRow || rowIndex != null) {
        val index = rowIndex
        ZenthaLineEditor(vm, rows.getOrNull(index ?: -1), order, rows) { result ->
            if(result != null) rows = if(index == null) rows + result else rows.toMutableList().also { it[index] = result }
            addRow = false; rowIndex = null
        }
    }
    if(addExtra || extraIndex != null) {
        val index = extraIndex
        val previous = extras.getOrNull(index ?: -1)
        var label by remember { mutableStateOf(previous?.label.orEmpty()) }
        var amount by remember { mutableStateOf(previous?.amount?.toString().orEmpty()) }
        var error by remember { mutableStateOf<String?>(null) }
        ZenthaEditor("Costo extra", vm, { addExtra = false; extraIndex = null }, {
            if(label.isBlank() || !amount.zMoney().isFinite() || amount.zMoney() < 0) error = "Inserisci descrizione e importo valido."
            else { val result = ZenthaExtraCost(previous?.id ?: 0, order.id, label, amount.zMoney()); extras = if(index == null) extras + result else extras.toMutableList().also { it[index] = result }; addExtra = false; extraIndex = null }
        }) { item { SpectroTextField("Descrizione", label, { label = it }) }; item { SpectroTextField("Importo €", amount, { amount = it }, numeric = true) }; error?.let { item { Text(it) } } }
    }
}

@Composable
private fun ZenthaLineEditor(vm: SpectroViewModel, old: ZenthaOrderItem?, order: ZenthaOrder, rows: List<ZenthaOrderItem>, done: (ZenthaOrderItem?) -> Unit) {
    var row by remember { mutableStateOf(old ?: ZenthaOrderItem(orderId = order.id, productName = "", quantity = 1, unitPrice = 0.0, unitCost = 0.0)) }
    var quantity by remember { mutableStateOf(row.quantity.toString()) }
    var price by remember { mutableStateOf(row.unitPrice.toString()) }
    var cost by remember { mutableStateOf(row.unitCost.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    ZenthaEditor("Riga prodotto", vm, { done(null) }, {
        if(quantity.zInt() <= 0 || !price.zMoney().isFinite() || !cost.zMoney().isFinite() || price.zMoney() < 0 || cost.zMoney() < 0 || row.productName.isBlank()) error = "Verifica prodotto, quantità e importi."
        else done(row.copy(quantity = quantity.zInt(), unitPrice = price.zMoney(), unitCost = cost.zMoney()))
    }) {
        item { SimpleSelectField("Prodotto", row.productName.ifBlank { "Seleziona" }, vm.state.zenthaProducts.filter { it.active || it.id == row.productId }.map { it.id to it.name }) { id ->
            val product = vm.state.zenthaProducts.first { it.id == id }
            row = row.copy(productId = id, productName = product.name, variantId = null, variantLabel = "", sizeSnapshot = "", skuSnapshot = "")
            cost = product.realCost.toString(); price = product.standardPrice.toString()
        } }
        item { SpectroTextField("Descrizione salvata", row.productName, { row = row.copy(productName = it) }) }
        item { SimpleSelectField("Variante / taglia", row.variantLabel.ifBlank { "Senza variante (nessuno scarico)" }, listOf(0L to "Senza variante") + vm.state.zentha.variants.filter { it.productId == row.productId && (it.active || it.id == row.variantId) }.map { it.id to it.label }) { id ->
            val v = vm.state.zentha.variants.firstOrNull { it.id == id }
            row = row.copy(variantId = v?.id, variantLabel = v?.label.orEmpty(), sizeSnapshot = v?.size.orEmpty(), skuSnapshot = v?.sku.orEmpty())
        } }
        item { SpectroTextField("Quantità pezzi", quantity, { quantity = it }, numeric = true) }
        item { SpectroTextField("Prezzo unitario €", price, { price = it }, numeric = true) }
        item { SpectroTextField("Costo unitario €", cost, { cost = it }, numeric = true) }
        item { TextButton(onClick = {
            runCatching { val product = vm.state.zenthaProducts.firstOrNull { it.id == row.productId } ?: error("Seleziona un prodotto da catalogo."); require(quantity.zInt() > 0); ZenthaBusiness.price(product, row.variantId, quantity.zInt() + rows.filter { it.productId == product.id && it !== old }.sumOf { it.quantity }, order.clientId, vm.state.zentha) }.onSuccess { price = it.toString(); error = null }.onFailure { error = it.message ?: "Quantità non valida." }
        }) { Text("Calcola prezzo da listino") } }
        error?.let { item { Text(it) } }
    }
}

@Composable
internal fun ZenthaOrderDetail(vm: SpectroViewModel, p: SpectroPalette, order: ZenthaOrder, close: () -> Unit, edit: () -> Unit) {
    val context = LocalContext.current
    val t = vm.zenthaTotals(order.id)
    var payment by remember { mutableStateOf(false) }
    var roster by remember { mutableStateOf<ZenthaRosterEntry?>(null) }
    var addRoster by remember { mutableStateOf(false) }
    var customization by remember { mutableStateOf<ZenthaCustomization?>(null) }
    var addCustomization by remember { mutableStateOf(false) }
    var stockConfirm by remember { mutableStateOf(false) }
    var nextStatus by remember { mutableStateOf<ZenthaOrderStatus?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    ZenthaEditor("${order.orderCode} • ${order.clientName}", vm, close) {
        item { Text("${order.status.label()} • ${order.orderDate} • consegna ${order.dueDate.ifBlank { "—" }}"); Text(order.contact); Button(onClick = edit) { Text("Modifica ordine") } }
        item { ZenthaKpis(p, listOf("Ricavi" to euro(t.revenue), "Costi prodotti" to euro(t.productCosts), "Extra" to euro(t.extraCosts), "Utile" to euro(t.profit), "Margine" to "%.1f%%".format(t.marginPercent), "Quote A / B" to "${euro(t.shareA)} / ${euro(t.shareB)}", "Pagato" to euro(t.paid), "Residuo" to euro(t.outstanding))) }
        item { Text(t.paymentState.label()); if(t.overpayment > 0) Text("Eccedenza incassata: ${euro(t.overpayment)}", color = p.warning) }
        items(vm.state.zenthaItems.filter { it.orderId == order.id }, key = { "line-${it.id}" }) { row -> Text("${row.productName}\n${row.variantLabel}\n${row.quantity} × ${euro(row.unitPrice)} • costo ${euro(row.unitCost)} • margine ${euro(row.margin)}") }
        item { Text("Sconto ${euro(order.discount)} • Trasporto ${euro(order.shippingRevenue)} • Altri ricavi ${euro(order.otherRevenue)}") }
        items(vm.state.zenthaExtraCosts.filter { it.orderId == order.id }) { Text("${it.label}: ${euro(it.amount)}") }
        item { SimpleSelectField("Workflow", order.status.label(), (zenthaWorkflow + ZenthaOrderStatus.CANCELLED).map { it to it.label() }) { nextStatus = it } }
        item { OutlinedButton(enabled = !vm.state.saving && order.status != ZenthaOrderStatus.CANCELLED, onClick = { stockConfirm = true }) { Text(if(order.stockPosted) "Verifica scarico magazzino" else "Conferma scarico magazzino") } }
        item { SectionTitle("Pagamenti"); Button(onClick = { payment = true }) { Text("Registra pagamento") } }
        items(vm.state.zenthaPayments.filter { it.orderId == order.id }.sortedByDescending { it.date }) { Text("${it.date} • ${euro(it.amount)} • ${it.method.name}\n${it.notes}") }
        item { SectionTitle("Distinta squadra"); Button(onClick = { addRoster = true }) { Text("Aggiungi atleta") } }
        items(vm.state.zentha.roster.filter { it.orderId == order.id }, key = { "roster-${it.id}" }) { r -> SpectroCard(p = p, onClick = { roster = r }) { Text("${r.surname} ${r.name} • #${r.number}", fontWeight = FontWeight.Bold); Text("Maglia ${r.shirtSize} • pantalone ${r.shortsSize}"); Text(r.notes); Text(vm.state.zenthaProducts.firstOrNull { it.id == r.productId }?.name.orEmpty()) } }
        item { SectionTitle("Personalizzazioni / mockup"); Button(onClick = { addCustomization = true }) { Text("Aggiungi personalizzazione") } }
        items(vm.state.zentha.customizations.filter { it.orderId == order.id }, key = { "custom-${it.id}" }) { c -> SpectroCard(p = p, onClick = { customization = c }) { Text("${c.type} • ${if(c.approved) "APPROVATO" else "Da approvare"}", fontWeight = FontWeight.Bold); Text(c.value); Text(c.notes); ZenthaAttachments(c.attachments) } }
        item { SectionTitle("Note e documenti"); Text(order.notes); ZenthaAttachments(order.attachments) }
        item { SectionTitle("PDF") }
        items(ZenthaDocument.entries) { type -> OutlinedButton(onClick = { runCatching { ReportExporter.shareZenthaDocument(context, vm.snapshot(), order.id, type) }.onFailure { error = it.message } }) { Text(type.title) } }
        error?.let { item { Text(it, color = p.warning) } }
        item { SectionTitle("Cronologia completa") }
        items(vm.state.zentha.events.filter { it.orderId == order.id }.sortedByDescending { it.dateTime }) { event -> Text("${event.dateTime.replace('T', ' ')}\n${runCatching { ZenthaOrderStatus.valueOf(event.toStatus).label() }.getOrDefault(event.toStatus)} • ${event.note}") }
    }
    if(stockConfirm) AlertDialog(onDismissRequest = { stockConfirm = false }, title = { Text("Confermare lo scarico?") }, text = { Text("Saranno scaricate solo le righe con variante. Le righe legacy senza variante sono escluse. Un ordine già scaricato viene riallineato senza duplicare i movimenti.") }, confirmButton = { TextButton(onClick = { vm.postZenthaStock(order.id); stockConfirm = false }) { Text("Conferma") } }, dismissButton = { TextButton(onClick = { stockConfirm = false }) { Text("Annulla") } })
    nextStatus?.let { status -> AlertDialog(onDismissRequest = { nextStatus = null }, title = { Text("Passare a ${status.label()}?") }, text = { Text(if(status == ZenthaOrderStatus.CANCELLED) "Le quantità scaricate saranno restituite. Pagamenti, allegati e cronologia restano conservati." else "Il passaggio sarà registrato con data e ora.") }, confirmButton = { TextButton(onClick = { vm.setZenthaStatus(order.id, status); nextStatus = null }) { Text("Conferma") } }, dismissButton = { TextButton(onClick = { nextStatus = null }) { Text("Annulla") } }) }
    if(payment) ZenthaPaymentEditor(vm, order.id) { payment = false }
    if(addRoster) ZenthaRosterEditor(vm, order.id, null) { addRoster = false }
    roster?.let { ZenthaRosterEditor(vm, order.id, it) { roster = null } }
    if(addCustomization) ZenthaCustomizationEditor(vm, order.id, null) { addCustomization = false }
    customization?.let { ZenthaCustomizationEditor(vm, order.id, it) { customization = null } }
}

@Composable
private fun ZenthaPaymentEditor(vm: SpectroViewModel, orderId: Long, close: () -> Unit) {
    var amount by remember { mutableStateOf("") }; var date by remember { mutableStateOf(LocalDate.now().toString()) }; var notes by remember { mutableStateOf("") }; var method by remember { mutableStateOf(PaymentMethod.UNSPECIFIED) }
    ZenthaEditor("Pagamento", vm, close, { vm.addZenthaPayment(ZenthaPayment(orderId = orderId, date = date, amount = amount.zMoney(), method = method, notes = notes), close) }) {
        item { SpectroTextField("Importo €", amount, { amount = it }, numeric = true) }; item { SpectroTextField("Data (AAAA-MM-GG)", date, { date = it }) }
        item { SimpleSelectField("Metodo", method.name, PaymentMethod.entries.map { it to it.name }) { method = it } }; item { SpectroTextField("Note", notes, { notes = it }, singleLine = false) }
    }
}

@Composable
private fun ZenthaRosterEditor(vm: SpectroViewModel, orderId: Long, old: ZenthaRosterEntry?, close: () -> Unit) {
    var row by remember { mutableStateOf(old ?: ZenthaRosterEntry(orderId = orderId, surname = "")) }
    var remove by remember { mutableStateOf(false) }
    ZenthaEditor("Atleta", vm, close, { vm.saveZenthaRoster(row, close) }) {
        item { SpectroTextField("Cognome", row.surname, { row = row.copy(surname = it) }) }; item { SpectroTextField("Nome", row.name, { row = row.copy(name = it) }) }
        item { SpectroTextField("Numero", row.number, { row = row.copy(number = it) }) }; item { SpectroTextField("Taglia maglia", row.shirtSize, { row = row.copy(shirtSize = it) }) }; item { SpectroTextField("Taglia pantalone", row.shortsSize, { row = row.copy(shortsSize = it) }) }
        item { SimpleSelectField("Prodotto / kit opzionale", vm.state.zenthaProducts.firstOrNull { it.id == row.productId }?.name ?: "Nessuno", listOf(0L to "Nessuno") + vm.state.zenthaProducts.map { it.id to it.name }) { row = row.copy(productId = it.takeIf { id -> id != 0L }) } }
        item { SpectroTextField("Note", row.notes, { row = row.copy(notes = it) }, singleLine = false) }
        if(old != null) item { TextButton(onClick = { remove = true }) { Text("Rimuovi atleta dalla distinta") } }
    }
    if(remove) AlertDialog(onDismissRequest = { remove = false }, title = { Text("Rimuovere questo atleta?") }, confirmButton = { TextButton(onClick = { vm.deleteZenthaRoster(row.id, close) }) { Text("Rimuovi") } }, dismissButton = { TextButton(onClick = { remove = false }) { Text("Annulla") } })
}

@Composable
private fun ZenthaCustomizationEditor(vm: SpectroViewModel, orderId: Long, old: ZenthaCustomization?, close: () -> Unit) {
    var row by remember { mutableStateOf(old ?: ZenthaCustomization(orderId = orderId)) }
    ZenthaEditor("Personalizzazione", vm, close, { vm.saveZenthaCustomization(row, close) }) {
        item { SimpleSelectField("Tipo", row.type, listOf("Nome/cognome", "Numero", "Sponsor", "Logo", "Colori", "Grafica", "Vestibilità", "Note produzione", "Mockup").map { it to it }) { row = row.copy(type = it) } }
        item { SimpleSelectField("Riga ordine", vm.state.zenthaItems.firstOrNull { it.id == row.orderItemId }?.let { "${it.productName} ${it.variantLabel}" } ?: "Intero ordine", listOf(0L to "Intero ordine") + vm.state.zenthaItems.filter { it.orderId == orderId }.map { it.id to "${it.productName} ${it.variantLabel}" }) { row = row.copy(orderItemId = it.takeIf { id -> id != 0L }) } }
        item { SpectroTextField("Contenuto", row.value, { row = row.copy(value = it) }, singleLine = false) }; item { SpectroTextField("Note produzione", row.notes, { row = row.copy(notes = it) }, singleLine = false) }
        item { Row { Text("Mockup / contenuto approvato", Modifier.weight(1f)); Switch(row.approved, { row = row.copy(approved = it) }) } }
        item { ZenthaAttachments(row.attachments) { row = row.copy(attachments = it) } }
    }
}
