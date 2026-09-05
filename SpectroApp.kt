package com.spectro.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.R
import com.spectro.app.data.ProductionRecord
import com.spectro.app.data.RecordType
import com.spectro.app.data.WorkOrder
import com.spectro.app.ui.theme.SpectroTheme
import com.spectro.app.ui.theme.paletteFor
import com.spectro.app.util.ReportExporter

enum class AppTab { DASHBOARD, WORK, CREATE, INVENTORY, CLIENTS }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectroApp(vm: SpectroViewModel) {
    val state = vm.state
    val palette = paletteFor(state.settings.themeKey)
    var tab by remember { mutableStateOf(AppTab.DASHBOARD) }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ProductionRecord?>(null) }
    var editingProduction by remember { mutableStateOf<ProductionRecord?>(null) }
    var editingWork by remember { mutableStateOf<WorkOrder?>(null) }
    var createMode by remember { mutableStateOf(CreateMode.PRODUCTION) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() }
    }

    SpectroTheme(state.settings.themeKey) {
        if (state.loading) {
            Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.spectro_header_logo), null, Modifier.size(126.dp, 92.dp))
                    Spacer(Modifier.height(18.dp)); CircularProgressIndicator(color = palette.primary)
                    Spacer(Modifier.height(10.dp)); Text("Avvio Spectro ERP…", color = palette.textMuted)
                }
            }
            return@SpectroTheme
        }

        Scaffold(
            containerColor = palette.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Surface(color = Color.Transparent) {
                    Row(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Image(painterResource(R.drawable.spectro_header_logo), null, Modifier.width(76.dp).height(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SPECTRO", fontWeight = FontWeight.Black, fontSize = 22.sp, color = palette.primary, letterSpacing = 1.sp)
                            Text("CHROMIA ERP • NATIVE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = palette.textMuted, letterSpacing = 1.3.sp)
                        }
                        FilledIconButton(onClick = { searchOpen = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)) {
                            Icon(Icons.Rounded.Search, null, tint = palette.text)
                        }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { settingsOpen = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)) {
                            Icon(Icons.Rounded.Settings, null, tint = palette.text)
                        }
                    }
                }
            },
            bottomBar = {
                SpectroBottomBar(tab, palette) { next ->
                    if (next == AppTab.CREATE) {
                        editingProduction = null; editingWork = null; createMode = CreateMode.PRODUCTION
                    }
                    tab = next
                }
            }
        ) { padding ->
            Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) }, label = "tabs") { current ->
                    when (current) {
                        AppTab.DASHBOARD -> DashboardScreen(vm, palette) { detail = it }
                        AppTab.WORK -> WorkOrdersScreen(vm, palette) { w -> editingWork = w; editingProduction = null; createMode = CreateMode.WORK; tab = AppTab.CREATE }
                        AppTab.CREATE -> CreateScreen(vm, palette, createMode, editingProduction, editingWork) { done ->
                            editingProduction = null; editingWork = null
                            tab = if (done == CreateMode.WORK) AppTab.WORK else AppTab.DASHBOARD
                        }
                        AppTab.INVENTORY -> InventoryScreen(vm, palette)
                        AppTab.CLIENTS -> ClientsScreen(vm, palette)
                    }
                }
            }
        }

        if (settingsOpen) SettingsSheet(vm, palette) { settingsOpen = false }
        if (searchOpen) GlobalSearchSheet(vm, palette) { searchOpen = false }
        detail?.let { r ->
            RecordDetailSheet(r, palette,
                onDismiss = { detail = null },
                onEdit = {
                    editingProduction = r; editingWork = null; createMode = if (r.type == RecordType.NEGATIVE) CreateMode.NEGATIVE else CreateMode.PRODUCTION
                    detail = null; tab = AppTab.CREATE
                },
                onDelete = { vm.deleteProduction(r.id); detail = null }
            )
        }
    }
}

@Composable
private fun SpectroBottomBar(selected: AppTab, p: com.spectro.app.ui.theme.SpectroPalette, onSelect: (AppTab) -> Unit) {
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(
            Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp)).background(p.surface).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            BottomIcon(Icons.Rounded.GridView, selected == AppTab.DASHBOARD, p) { onSelect(AppTab.DASHBOARD) }
            BottomIcon(Icons.Rounded.Assignment, selected == AppTab.WORK, p) { onSelect(AppTab.WORK) }
            FilledIconButton(
                onClick = { onSelect(AppTab.CREATE) },
                modifier = Modifier.size(58.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = p.primary, contentColor = Color.White)
            ) { Icon(Icons.Rounded.Add, null, Modifier.size(30.dp)) }
            BottomIcon(Icons.Rounded.Inventory2, selected == AppTab.INVENTORY, p) { onSelect(AppTab.INVENTORY) }
            BottomIcon(Icons.Rounded.People, selected == AppTab.CLIENTS, p) { onSelect(AppTab.CLIENTS) }
        }
    }
}

@Composable
private fun BottomIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, p: com.spectro.app.ui.theme.SpectroPalette, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, null, tint = if (active) p.primary else p.textMuted) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailSheet(r: ProductionRecord, p: com.spectro.app.ui.theme.SpectroPalette, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.date, color = p.textMuted, fontSize = 12.sp)
                    Text(r.clientName, fontSize = 26.sp, fontWeight = FontWeight.Black)
                    Text(if (r.type == RecordType.NEGATIVE) "Evento negativo" else r.orderRef.ifBlank { "Produzione" }, color = if (r.type == RecordType.NEGATIVE) p.danger else p.primary, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
            }
            Spacer(Modifier.height(16.dp))
            SpectroCard(p = p) {
                if (r.type == RecordType.PRODUCTION) {
                    Metric("Tessuto", r.fabricDisplayName); Spacer(Modifier.height(10.dp))
                    Row { Metric("Carta", meters(r.printedPaperMeters), modifier = Modifier.weight(1f)); Metric("Tessuto", meters(r.fabricUsedMeters), modifier = Modifier.weight(1f)) }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = p.border)
                    Row { Metric("Fatturato", euro(r.grossRevenue), p.success, Modifier.weight(1f)); Metric("Materie", euro(r.materialCostTotal), modifier = Modifier.weight(1f)) }
                    Spacer(Modifier.height(12.dp)); Metric("Utile lordo", euro(r.grossProfit), if (r.grossProfit >= 0) p.success else p.danger)
                } else {
                    Text(r.technicalNotes, color = p.text)
                    Spacer(Modifier.height(12.dp)); Metric("Perdita totale", euro(r.materialCostTotal), p.danger)
                }
            }
            if (r.technicalNotes.isNotBlank() && r.type == RecordType.PRODUCTION) {
                Spacer(Modifier.height(12.dp)); SpectroCard(p = p) { Text("NOTE TECNICHE", fontWeight = FontWeight.Black, color = p.primary); Spacer(Modifier.height(8.dp)); Text(r.technicalNotes) }
            }
            if (r.images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(r.images) }
            Spacer(Modifier.height(16.dp))
            GradientButton("Esporta PDF analitico", p, icon = Icons.Rounded.PictureAsPdf) { ReportExporter.share(context, r) }
            Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Eliminare la registrazione?") },
        text = { Text("Il magazzino verrà riallineato automaticamente se questa produzione ha consumato tessuto.") },
        confirmButton = { TextButton(onClick = onDelete) { Text("Elimina", color = p.danger) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } }
    )
}
