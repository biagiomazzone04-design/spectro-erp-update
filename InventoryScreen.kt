package com.spectro.app.ui

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import com.spectro.app.data.InventoryItem
import com.spectro.app.data.OwnerType
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.util.AttachmentStore
import java.time.LocalDate

@Composable
fun InventoryScreen(vm: SpectroViewModel, p: SpectroPalette) {
    val context = LocalContext.current
    var activeOwner by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<InventoryItem?>(null) }
    var showForm by remember { mutableStateOf(false) }
    var ownerType by remember { mutableStateOf(OwnerType.CLIENT) }
    var ownerName by remember { mutableStateOf("") }
    var fabricName by remember { mutableStateOf("") }
    var quantity by remember { mutableStateOf("") }
    var purchasePrice by remember { mutableStateOf("") }
    var minimumQuantity by remember { mutableStateOf("") }
    var images by remember { mutableStateOf<List<String>>(emptyList()) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        val imported = uris.mapNotNull { runCatching { AttachmentStore.importToPrivateStorage(context, it) }.getOrNull() }
        images = images + imported
    }

    fun open(item: InventoryItem?) {
        editing = item
        ownerType = item?.ownerType ?: OwnerType.CLIENT
        ownerName = item?.ownerName ?: activeOwner.orEmpty()
        fabricName = item?.fabricName.orEmpty()
        quantity = item?.quantityMeters?.toString().orEmpty()
        purchasePrice = item?.purchasePricePerMeter?.takeIf { it > 0 }?.toString().orEmpty()
        minimumQuantity = item?.minimumQuantityMeters?.takeIf { it > 0 }?.toString().orEmpty()
        images = item?.images ?: emptyList()
        showForm = true
    }

    val owners = vm.state.inventory.map { it.ownerName }.distinct().sorted()
    val shownItems = if (activeOwner == null) emptyList() else vm.state.inventory.filter { it.ownerName == activeOwner }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text(if (activeOwner == null) "Magazzino" else activeOwner!!, fontSize = 28.sp, fontWeight = FontWeight.Black)
                    Text(if (activeOwner == null) "Giacenze tessuti per cliente e tessuti di casa." else "${shownItems.size} tessuti registrati", color = p.textMuted)
                }
                Row {
                    if (activeOwner != null) IconButton(onClick = { activeOwner = null; showForm = false }) { Icon(Icons.Rounded.ArrowBack, null) }
                    FilledIconButton(onClick = { if (showForm) showForm = false else open(null) }) { Icon(if (showForm) Icons.Rounded.Close else Icons.Rounded.Add, null) }
                }
            }
        }
        item {
            AnimatedVisibility(showForm) {
                SpectroCard(p = p) {
                    Text(if (editing == null) "Nuovo tessuto" else "Modifica tessuto", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    SegmentedChoice(listOf("CLIENT" to "CLIENTE", "HOUSE" to "MIO / CASA"), ownerType.name, p) { ownerType = OwnerType.valueOf(it) }
                    Spacer(Modifier.height(14.dp))
                    SpectroTextField("Proprietà", ownerName, { ownerName = it }, placeholder = "Cliente o interno")
                    Spacer(Modifier.height(12.dp))
                    SpectroTextField("Nome tessuto", fabricName, { fabricName = it })
                    Spacer(Modifier.height(12.dp))
                    SpectroTextField("Metri totali / giacenza", quantity, { quantity = it }, numeric = true)
                    if (ownerType == OwnerType.HOUSE) {
                        Spacer(Modifier.height(12.dp)); SpectroTextField("Costo acquisto €/mt", purchasePrice, { purchasePrice = it }, numeric = true)
                    }
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Soglia minima scorta (mt)", minimumQuantity, { minimumQuantity = it }, numeric = true, placeholder = "0 = nessun avviso")
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.AddPhotoAlternate, null); Spacer(Modifier.width(8.dp)); Text("Allega foto") }
                    if (images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(images) { idx -> images = images.filterIndexed { i, _ -> i != idx } } }
                    Spacer(Modifier.height(16.dp))
                    GradientButton("Salva tessuto", p) {
                        if (ownerName.isNotBlank() && fabricName.isNotBlank() && quantity.toDoubleSafe() >= 0) {
                            vm.saveInventory(InventoryItem(
                                id = editing?.id ?: 0L,
                                ownerType = ownerType,
                                ownerName = ownerName.trim(),
                                fabricName = fabricName.trim(),
                                quantityMeters = quantity.toDoubleSafe(),
                                purchasePricePerMeter = if (ownerType == OwnerType.HOUSE) purchasePrice.toDoubleSafe() else 0.0,
                                createdDate = editing?.createdDate ?: LocalDate.now().toString(),
                                images = images,
                                minimumQuantityMeters = minimumQuantity.toDoubleSafe()
                            ))
                            showForm = false
                        }
                    }
                    if (editing != null) TextButton(onClick = { vm.deleteInventory(editing!!.id); showForm = false }, modifier = Modifier.align(Alignment.End)) { Text("Elimina", color = p.danger) }
                }
            }
        }

        if (activeOwner == null) {
            if (owners.isEmpty()) item { EmptyState(Icons.Rounded.Inventory2, "Magazzino vuoto", "Aggiungi un tessuto o una cartella cliente") }
            items(owners, key = { it }) { owner ->
                val items = vm.state.inventory.filter { it.ownerName == owner }
                val meters = items.sumOf { it.quantityMeters }
                SpectroCard(p = p, onClick = { activeOwner = owner }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.FolderOpen, null, tint = p.primary, modifier = Modifier.size(38.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) { Text(owner, fontWeight = FontWeight.Black, fontSize = 17.sp); Text("${items.size} tessuti", color = p.textMuted) }
                        Text(meters(meters), fontWeight = FontWeight.Black, color = p.primary)
                    }
                }
            }
        } else {
            items(shownItems, key = { it.id }) { item ->
                SpectroCard(p = p, onClick = { open(item) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(item.fabricName, fontWeight = FontWeight.Black, fontSize = 17.sp)
                            Text("Ingresso ${item.createdDate} • ${if (item.ownerType == OwnerType.HOUSE) "Mio" else "Cliente"}", color = p.textMuted, fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(meters(item.quantityMeters), fontWeight = FontWeight.Black, fontSize = 22.sp)
                            if (item.minimumQuantityMeters > 0 && item.quantityMeters <= item.minimumQuantityMeters) Text("SCORTA BASSA", color = p.danger, fontWeight = FontWeight.Black, fontSize = 10.sp)
                        }
                    }
                    if (item.images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(item.images) }
                }
            }
        }
    }
}
