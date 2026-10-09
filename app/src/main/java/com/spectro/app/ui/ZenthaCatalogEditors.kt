package com.spectro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import java.time.LocalDateTime

@Composable
internal fun ZenthaProductEditor(vm: SpectroViewModel, p: SpectroPalette, editing: ZenthaProduct?, close: () -> Unit) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var sku by remember { mutableStateOf(editing?.sku.orEmpty()) }
    var category by remember { mutableStateOf(editing?.category.orEmpty()) }
    var line by remember { mutableStateOf(editing?.line.orEmpty()) }
    var description by remember { mutableStateOf(editing?.description.orEmpty()) }
    var photo by remember { mutableStateOf(editing?.imagePath.orEmpty()) }
    var active by remember { mutableStateOf(editing?.active ?: true) }
    var price by remember { mutableStateOf(editing?.standardPrice?.toString().orEmpty()) }
    var costs by remember { mutableStateOf(listOf(editing?.standardCost, editing?.printingCost, editing?.sewingCost, editing?.accessoriesCost, editing?.packagingCost, editing?.otherCost).map { it?.toString().orEmpty() }) }
    ZenthaEditor(if(editing == null) "Nuovo prodotto" else "Modifica prodotto", vm, close, {
        vm.saveZenthaProduct(ZenthaProduct(id = editing?.id ?: 0, name = name.trim(), sku = sku.trim(), category = category.trim(), standardCost = costs[0].zMoney(), standardPrice = price.zMoney(), description = description, imagePath = photo, active = active, line = line, printingCost = costs[1].zMoney(), sewingCost = costs[2].zMoney(), accessoriesCost = costs[3].zMoney(), packagingCost = costs[4].zMoney(), otherCost = costs[5].zMoney()), close)
    }) {
        item { SpectroTextField("Nome", name, { name = it }) }
        item { SpectroTextField("SKU prodotto", sku, { sku = it }) }
        item { SpectroTextField("Categoria", category, { category = it }) }
        item { SpectroTextField("Linea", line, { line = it }) }
        item { SpectroTextField("Descrizione", description, { description = it }, singleLine = false) }
        item { ZenthaAttachments(photo.takeIf { it.isNotBlank() }?.let { listOf(it) }.orEmpty(), mimeType = "image/*") { photo = it.lastOrNull().orEmpty() } }
        item { Row { Text("Attivo", Modifier.weight(1f)); Switch(active, { active = it }) } }
        listOf("Costo base", "Stampa", "Confezione", "Accessori", "Packaging", "Altri costi").forEachIndexed { i, label ->
            item { SpectroTextField("$label €/pezzo", costs[i], { v -> costs = costs.toMutableList().also { it[i] = v } }, numeric = true) }
        }
        item { Text("Costo reale: ${euro(costs.sumOf { it.zMoney() })}", fontWeight = FontWeight.Black) }
        item { SpectroTextField("Prezzo standard €/pezzo", price, { price = it }, numeric = true) }
        item { Text("Le modifiche al catalogo non cambiano prezzi e costi delle righe ordine già salvate.", color = p.textMuted) }
    }
}

@Composable
internal fun ZenthaVariantsEditor(vm: SpectroViewModel, p: SpectroPalette, product: ZenthaProduct, close: () -> Unit) {
    var editing by remember { mutableStateOf<ZenthaProductVariant?>(null) }
    var create by remember { mutableStateOf(false) }
    ZenthaEditor("Varianti • ${product.name}", vm, close) {
        item { Button(onClick = { create = true }) { Text("Nuova taglia / variante") } }
        items(vm.state.zentha.variants.filter { it.productId == product.id }, key = { it.id }) { v ->
            SpectroCard(p = p, onClick = { editing = v }) { Text(v.label, fontWeight = FontWeight.Black); Text("${v.sku} • ${if(v.active) "Attiva" else "Disattiva"}") }
        }
    }
    if(create) ZenthaVariantForm(vm, product.id, null) { create = false }
    editing?.let { ZenthaVariantForm(vm, product.id, it) { editing = null } }
}

@Composable
private fun ZenthaVariantForm(vm: SpectroViewModel, productId: Long, v: ZenthaProductVariant?, close: () -> Unit) {
    var sku by remember { mutableStateOf(v?.sku.orEmpty()) }
    var size by remember { mutableStateOf(v?.size.orEmpty()) }
    var color by remember { mutableStateOf(v?.color.orEmpty()) }
    var fit by remember { mutableStateOf(v?.fit ?: "Uomo") }
    var type by remember { mutableStateOf(v?.variantType ?: "Standard") }
    var active by remember { mutableStateOf(v?.active ?: true) }
    ZenthaEditor("Variante", vm, close, { vm.saveZenthaVariant(ZenthaProductVariant(v?.id ?: 0, productId, sku, size, color, fit, type, active), close) }) {
        item { SpectroTextField("SKU variante", sku, { sku = it }) }
        item { SpectroTextField("Taglia", size, { size = it }, placeholder = "XS, M, 12 anni, Unica…") }
        item { SpectroTextField("Colore", color, { color = it }) }
        item { SimpleSelectField("Vestibilità", fit, listOf("Uomo", "Donna", "Junior").map { it to it }) { fit = it } }
        item { SimpleSelectField("Tipologia", type, listOf("Standard", "Double", "Reverse", "Altro").map { it to it }) { type = it } }
        item { Row { Text("Attiva", Modifier.weight(1f)); Switch(active, { active = it }) } }
    }
}

@Composable
internal fun ZenthaStockEditor(vm: SpectroViewModel, p: SpectroPalette, variantId: Long, close: () -> Unit) {
    val balance = vm.state.zentha.balances.first { it.variantId == variantId }
    var minimum by remember { mutableStateOf(balance.minimumQuantity.toString()) }
    var location by remember { mutableStateOf(balance.location) }
    var moving by remember { mutableStateOf(false) }
    ZenthaEditor("Magazzino variante", vm, close, { vm.saveZenthaBalance(balance.copy(minimumQuantity = minimum.zInt(), location = location), close) }) {
        item { Metric("Disponibili", "${balance.quantityAvailable} pezzi") }
        item { SpectroTextField("Scorta minima", minimum, { minimum = it }, numeric = true) }
        item { SpectroTextField("Ubicazione", location, { location = it }) }
        item { Button(onClick = { moving = true }) { Text("Registra movimento") } }
        item { SectionTitle("Movimenti completi") }
        items(vm.state.zentha.movements.filter { it.variantId == variantId }.sortedWith(compareByDescending<ZenthaStockMovement> { it.dateTime }.thenByDescending { it.id }), key = { it.id }) { move ->
            SpectroCard(p = p) {
                Text("${move.type.label()} • ${if(move.quantity > 0) "+" else ""}${move.quantity}", fontWeight = FontWeight.Black)
                Text(move.dateTime.replace('T', ' ')); Text(move.note)
                move.orderId?.let { id -> Text(vm.state.zenthaOrders.firstOrNull { it.id == id }?.orderCode ?: "Ordine #$id") }
            }
        }
    }
    if(moving) {
        var type by remember { mutableStateOf(ZenthaMovementType.LOAD) }
        var quantity by remember { mutableStateOf("") }
        var note by remember { mutableStateOf("") }
        var dateTime by remember { mutableStateOf(LocalDateTime.now().withNano(0).toString()) }
        ZenthaEditor("Nuovo movimento", vm, { moving = false }, {
            val amount = quantity.toIntOrNull() ?: 0
            val delta = if(type in listOf(ZenthaMovementType.SAMPLE, ZenthaMovementType.WASTE)) -amount else amount
            // Parsing/sign validation is done before committing, never converted silently to zero.
            if(type != ZenthaMovementType.ADJUSTMENT && amount <= 0) vm.moveZenthaStock(ZenthaStockMovement(variantId = variantId, type = type, quantity = 0, dateTime = dateTime, note = note))
            else vm.moveZenthaStock(ZenthaStockMovement(variantId = variantId, type = type, quantity = delta, dateTime = dateTime, note = note)) { moving = false }
        }) {
            item { SimpleSelectField("Tipo", type.label(), ZenthaMovementType.entries.filter { it != ZenthaMovementType.ORDER_USE }.map { it to it.label() }) { type = it } }
            item { SpectroTextField(if(type == ZenthaMovementType.ADJUSTMENT) "Variazione pezzi (+/-)" else "Numero pezzi", quantity, { quantity = it }, numeric = type != ZenthaMovementType.ADJUSTMENT) }
            item { SpectroTextField("Data e ora (AAAA-MM-GGTHH:MM)", dateTime, { dateTime = it }) }
            item { SpectroTextField("Motivo", note, { note = it }, singleLine = false) }
            item { Text("Uso ordine: apri l'ordine e conferma lo scarico. Le rettifiche modificano la giacenza con un movimento tracciato.") }
        }
    }
}

@Composable
internal fun ZenthaListEditor(vm: SpectroViewModel, p: SpectroPalette, list: ZenthaPriceList?, close: () -> Unit) {
    var name by remember { mutableStateOf(list?.name.orEmpty()) }
    var description by remember { mutableStateOf(list?.description.orEmpty()) }
    var active by remember { mutableStateOf(list?.active ?: true) }
    var rule by remember { mutableStateOf<ZenthaPriceListRule?>(null) }
    var add by remember { mutableStateOf(false) }
    ZenthaEditor("Listino", vm, close, { vm.saveZenthaPriceList(ZenthaPriceList(list?.id ?: 0, name, description, active), close) }) {
        item { SpectroTextField("Nome", name, { name = it }) }
        item { SpectroTextField("Descrizione", description, { description = it }, singleLine = false) }
        item { Row { Text("Attivo", Modifier.weight(1f)); Switch(active, { active = it }) } }
        if(list != null) {
            item { Button(onClick = { add = true }) { Text("Aggiungi fascia quantità") } }
            items(vm.state.zentha.priceRules.filter { it.priceListId == list.id }, key = { it.id }) { r -> ZenthaRuleCard(vm, p, r) { rule = r } }
        } else item { Text("Salva il listino, poi aprilo per definire le fasce quantità.") }
    }
    if(add && list != null) ZenthaRuleEditor(vm, null, list.id, null) { add = false }
    rule?.let { ZenthaRuleEditor(vm, it, it.priceListId, null) { rule = null } }
}

@Composable
internal fun ZenthaClientEditor(vm: SpectroViewModel, p: SpectroPalette, client: Client, close: () -> Unit) {
    val profile = vm.state.zentha.clientProfiles.firstOrNull { it.clientId == client.id }
    var listId by remember { mutableStateOf(profile?.assignedPriceListId) }
    var notes by remember { mutableStateOf(profile?.notes.orEmpty()) }
    var rule by remember { mutableStateOf<ZenthaPriceListRule?>(null) }
    var add by remember { mutableStateOf(false) }
    ZenthaEditor(client.name, vm, close, { vm.saveZenthaClient(ZenthaClientProfile(profile?.id ?: 0, client.id, listId, notes), close) }) {
        item { SimpleSelectField("Listino assegnato", vm.state.zentha.priceLists.firstOrNull { it.id == listId }?.name ?: "Standard", listOf(0L to "Standard predefinito") + vm.state.zentha.priceLists.filter { it.active }.map { it.id to it.name }) { listId = it.takeIf { id -> id != 0L } } }
        item { SpectroTextField("Note cliente Zentha", notes, { notes = it }, singleLine = false) }
        item { Button(onClick = { add = true }) { Text("Prezzo riservato al cliente") } }
        items(vm.state.zentha.priceRules.filter { it.clientId == client.id }, key = { it.id }) { r -> ZenthaRuleCard(vm, p, r) { rule = r } }
        item { Text("I prezzi cliente prevalgono sul listino; a parità di cliente/listino prevale la variante specifica. Le fasce non possono sovrapporsi.") }
    }
    if(add) ZenthaRuleEditor(vm, null, null, client.id) { add = false }
    rule?.let { ZenthaRuleEditor(vm, it, null, client.id) { rule = null } }
}

@Composable
private fun ZenthaRuleCard(vm: SpectroViewModel, p: SpectroPalette, r: ZenthaPriceListRule, edit: () -> Unit) {
    var confirm by remember { mutableStateOf(false) }
    SpectroCard(p = p, onClick = edit) {
        Text(vm.state.zenthaProducts.firstOrNull { it.id == r.productId }?.name.orEmpty(), fontWeight = FontWeight.Black)
        Text(vm.state.zentha.variants.firstOrNull { it.id == r.variantId }?.label ?: "Tutte le varianti")
        Text("${r.minQty}–${r.maxQty ?: "∞"} pezzi: ${euro(r.unitPrice)}")
        TextButton(onClick = { confirm = true }) { Text("Elimina regola") }
    }
    if(confirm) AlertDialog(onDismissRequest = { confirm = false }, title = { Text("Eliminare la regola?") }, text = { Text("I prezzi già salvati negli ordini restano invariati.") }, confirmButton = { TextButton(onClick = { vm.deleteZenthaPriceRule(r.id); confirm = false }) { Text("Elimina") } }, dismissButton = { TextButton(onClick = { confirm = false }) { Text("Annulla") } })
}

@Composable
private fun ZenthaRuleEditor(vm: SpectroViewModel, rule: ZenthaPriceListRule?, listId: Long?, clientId: Long?, close: () -> Unit) {
    var productId by remember { mutableStateOf(rule?.productId ?: 0L) }
    var variantId by remember { mutableStateOf(rule?.variantId) }
    var min by remember { mutableStateOf(rule?.minQty?.toString() ?: "1") }
    var max by remember { mutableStateOf(rule?.maxQty?.toString().orEmpty()) }
    var price by remember { mutableStateOf(rule?.unitPrice?.toString().orEmpty()) }
    ZenthaEditor("Fascia prezzo", vm, close, { vm.saveZenthaPriceRule(ZenthaPriceListRule(rule?.id ?: 0, listId, productId, variantId, min.zInt(), if(max.isBlank()) null else max.zInt(), price.zMoney(), clientId), close) }) {
        item { SimpleSelectField("Prodotto", vm.state.zenthaProducts.firstOrNull { it.id == productId }?.name ?: "Seleziona", vm.state.zenthaProducts.map { it.id to it.name }) { productId = it; variantId = null } }
        item { SimpleSelectField("Variante", vm.state.zentha.variants.firstOrNull { it.id == variantId }?.label ?: "Tutte", listOf(0L to "Tutte") + vm.state.zentha.variants.filter { it.productId == productId }.map { it.id to it.label }) { variantId = it.takeIf { id -> id != 0L } } }
        item { SpectroTextField("Da quantità (inclusa)", min, { min = it }, numeric = true) }
        item { SpectroTextField("A quantità (inclusa; vuoto = illimitata)", max, { max = it }, numeric = true) }
        item { SpectroTextField("Prezzo unitario €", price, { price = it }, numeric = true) }
    }
}
