package com.spectro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import com.spectro.app.data.*
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.ui.theme.SpectroTheme
import com.spectro.app.ui.theme.paletteFor
import com.spectro.app.util.ReportExporter

enum class AppTab { DASHBOARD, WORK, CREATE, INVENTORY, MORE, CLIENTS, ISSUES, COTTON, ZENTHA, ECONOMIC, ACTIVITIES }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpectroApp(vm: SpectroViewModel) {
    val state = vm.state
    val palette = paletteFor(state.settings.themeKey)
    var tab by remember { mutableStateOf(AppTab.DASHBOARD) }
    val navHistory = remember { mutableStateListOf<AppTab>() }
    var settingsOpen by remember { mutableStateOf(false) }
    var searchOpen by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<ProductionRecord?>(null) }
    var selectedOrder by remember { mutableStateOf<WorkOrder?>(null) }
    var editingProduction by remember { mutableStateOf<ProductionRecord?>(null) }
    var editingWork by remember { mutableStateOf<WorkOrder?>(null) }
    var sourceWork by remember { mutableStateOf<WorkOrder?>(null) }
    var createMode by remember { mutableStateOf(CreateMode.PRODUCTION) }
    var selectedClient by remember { mutableStateOf<Client?>(null) }
    var selectedInventory by remember { mutableStateOf<InventoryItem?>(null) }
    var cottonOpenId by remember { mutableStateOf<Long?>(null) }
    var zenthaOpenId by remember { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); vm.consumeMessage() } }

    fun navigate(next: AppTab, rememberCurrent: Boolean = true) {
        if (next == tab) return
        if (rememberCurrent) navHistory.add(tab)
        tab = next
    }
    fun goBack() {
        tab = if (navHistory.isNotEmpty()) navHistory.removeAt(navHistory.lastIndex) else AppTab.DASHBOARD
    }

    fun startProduction(order: WorkOrder) {
        selectedOrder = null; editingProduction = null; editingWork = null; sourceWork = order; createMode = CreateMode.PRODUCTION; navigate(AppTab.CREATE)
    }
    fun editOrder(order: WorkOrder) {
        selectedOrder = null; editingWork = order; editingProduction = null; sourceWork = null; createMode = CreateMode.WORK; navigate(AppTab.CREATE)
    }
    fun openOrder(order: WorkOrder) { selectedOrder = order }

    BackHandler(enabled = selectedOrder != null) { selectedOrder = null }
    BackHandler(enabled = selectedOrder == null && tab != AppTab.DASHBOARD) { goBack() }

    SpectroTheme(state.settings.themeKey) {
        if (state.loading) {
            Box(Modifier.fillMaxSize().background(palette.background), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.spectro_header_logo), null, Modifier.size(126.dp, 92.dp))
                    Spacer(Modifier.height(18.dp)); CircularProgressIndicator(color = palette.primary)
                    Spacer(Modifier.height(10.dp)); Text("Aggiornamento dati Spectro ERP…", color = palette.textMuted)
                }
            }
            return@SpectroTheme
        }

        Scaffold(
            containerColor = palette.background,
            snackbarHost = { SnackbarHost(snackbar) },
            topBar = {
                Surface(color = Color.Transparent) {
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (tab != AppTab.DASHBOARD) {
                            IconButton(onClick = { goBack() }) { Icon(Icons.Rounded.ArrowBack, "Indietro", tint = palette.text) }
                            Spacer(Modifier.width(4.dp))
                        }
                        Image(painterResource(R.drawable.spectro_header_logo), null, Modifier.width(76.dp).height(46.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("SPECTRO", fontWeight = FontWeight.Black, fontSize = 22.sp, color = palette.primary, letterSpacing = 1.sp)
                            Text("ERP 3.0 • NATIVE", fontSize = 9.sp, fontWeight = FontWeight.Black, color = palette.textMuted, letterSpacing = 1.3.sp)
                        }
                        FilledIconButton(onClick = { searchOpen = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)) { Icon(Icons.Rounded.Search, null, tint = palette.text) }
                        Spacer(Modifier.width(8.dp))
                        FilledIconButton(onClick = { settingsOpen = true }, colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)) { Icon(Icons.Rounded.Settings, null, tint = palette.text) }
                    }
                }
            },
            bottomBar = { SpectroBottomBar(tab, palette) { next -> if (next == AppTab.CREATE) { editingProduction = null; editingWork = null; sourceWork = null; createMode = CreateMode.PRODUCTION }; navigate(next) } }
        ) { padding ->
            Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
                AnimatedContent(tab, transitionSpec = { fadeIn(tween(160)) togetherWith fadeOut(tween(130)) }, label = "tabs") { current ->
                    when (current) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            vm,
                            palette,
                            onRecord = { detail = it },
                            onOpenOrder = ::openOrder,
                            onEditOrder = ::editOrder
                        )
                        AppTab.WORK -> WorkOrdersScreen(vm, palette, onEdit = ::editOrder, onOpen = ::openOrder, onStartProduction = ::startProduction)
                        AppTab.CREATE -> CreateScreen(vm, palette, createMode, editingProduction, editingWork, sourceWork) { done -> editingProduction = null; editingWork = null; sourceWork = null; navigate(if (done == CreateMode.WORK) AppTab.WORK else AppTab.DASHBOARD, rememberCurrent = false) }
                        AppTab.INVENTORY -> InventoryScreen(vm, palette)
                        AppTab.CLIENTS -> ClientsScreen(vm, palette, onOpenOrder = ::openOrder)
                        AppTab.ISSUES -> IssuesCenterScreen(vm, palette, onOpenOrder = ::openOrder)
                        AppTab.COTTON -> CottonScreen(vm, palette, initialOpenOrderId = cottonOpenId, onOpenConsumed = { cottonOpenId = null })
                        AppTab.ZENTHA -> ZenthaScreen(vm, palette, initialOpenOrderId = zenthaOpenId, onOpenConsumed = { zenthaOpenId = null })
                        AppTab.ECONOMIC -> EconomicWorkScreen(vm, palette)
                        AppTab.ACTIVITIES -> OperationalTasksScreen(vm, palette, onOpenOrder = ::openOrder)
                        AppTab.MORE -> MoreModulesScreen(vm, palette) { navigate(it) }
                    }
                }
            }
        }

        if (settingsOpen) SettingsSheet(vm, palette) { settingsOpen = false }
        if (searchOpen) GlobalSearchSheet(
            vm = vm,
            p = palette,
            onDismiss = { searchOpen = false },
            onOpenClient = { selectedClient = it },
            onOpenOrder = ::openOrder,
            onOpenProduction = { detail = it },
            onOpenCotton = { cottonOpenId = it.id; navigate(AppTab.COTTON) },
            onOpenZentha = { zenthaOpenId = it.id; navigate(AppTab.ZENTHA) },
            onOpenInventory = { selectedInventory = it }
        )
        selectedClient?.let { client ->
            SearchClientProfileSheet(
                vm = vm,
                p = palette,
                client = client,
                onDismiss = { selectedClient = null },
                onOpenOrder = { selectedClient = null; openOrder(it) },
                onOpenProduction = { selectedClient = null; detail = it },
                onOpenInventory = { selectedClient = null; selectedInventory = it },
                onOpenCotton = { selectedClient = null; cottonOpenId = it.id; navigate(AppTab.COTTON) }
            )
        }
        selectedInventory?.let { item -> InventoryDetailSheet(vm, palette, item, onDismiss = { selectedInventory = null }) }
        selectedOrder?.let { order -> OrderTimelineScreen(vm, palette, order, onBack = { selectedOrder = null }, onEdit = ::editOrder, onStartProduction = ::startProduction) }
        detail?.let { r ->
            RecordDetailSheet(
                r, palette,
                onDismiss = { detail = null },
                onEdit = { editingProduction = r; editingWork = null; sourceWork = null; createMode = if (r.type == RecordType.NEGATIVE) CreateMode.NEGATIVE else CreateMode.PRODUCTION; detail = null; navigate(AppTab.CREATE) },
                onDelete = { vm.deleteProduction(r.id); detail = null },
                onOpenHistory = {
                    r.workOrderId?.let { id -> vm.state.workOrders.firstOrNull { it.id == id }?.let { order -> detail = null; openOrder(order) } }
                }
            )
        }
    }
}

@Composable
private fun MoreModulesScreen(vm: SpectroViewModel, p: SpectroPalette, onOpen: (AppTab) -> Unit) {
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Altri moduli", fontSize = 28.sp, fontWeight = FontWeight.Black)
        Text("CRM, criticità, cotone, attività, ricavi e Zentha.", color = p.textMuted)
        Spacer(Modifier.height(4.dp))
        ModuleCard("Clienti", "Anagrafica, scadenze e storico", Icons.Rounded.People, p) { onOpen(AppTab.CLIENTS) }
        ModuleCard("Criticità", "Centro operativo di problemi e ritardi • ${vm.openIssues().size} aperte", Icons.Rounded.ReportProblem, p) { onOpen(AppTab.ISSUES) }
        ModuleCard("Cotone", "Ordini reattivi/pigmento e margini", Icons.Rounded.Texture, p) { onOpen(AppTab.COTTON) }
        ModuleCard("Attività", "Campioni, ricerche grafiche e lavori da fare • ${vm.openTasks().size} aperte", Icons.Rounded.AddTask, p) { onOpen(AppTab.ACTIVITIES) }
        ModuleCard("Altri ricavi", "Trasferimenti, lavorazioni e materiali fatturati", Icons.Rounded.Payments, p) { onOpen(AppTab.ECONOMIC) }
        ModuleCard("ZENTHA", "Catalogo, ordini, utile 50/50 e pagamenti", Icons.Rounded.SportsVolleyball, p) { onOpen(AppTab.ZENTHA) }
    }
}

@Composable
private fun ModuleCard(title: String, subtitle: String, icon: androidx.compose.ui.graphics.vector.ImageVector, p: SpectroPalette, onClick: () -> Unit) {
    SpectroCard(p = p, onClick = onClick) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = p.primary, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp); Text(subtitle, color = p.textMuted, fontSize = 12.sp) }; Icon(Icons.Rounded.ChevronRight, null, tint = p.textMuted) } }
}

@Composable
private fun SpectroBottomBar(selected: AppTab, p: SpectroPalette, onSelect: (AppTab) -> Unit) {
    val moreSelected = selected in listOf(AppTab.MORE, AppTab.CLIENTS, AppTab.ISSUES, AppTab.COTTON, AppTab.ZENTHA, AppTab.ECONOMIC, AppTab.ACTIVITIES)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp), contentAlignment = Alignment.Center) {
        Row(Modifier.fillMaxWidth().height(72.dp).clip(RoundedCornerShape(36.dp)).background(p.surface).padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceAround) {
            BottomIcon(Icons.Rounded.GridView, selected == AppTab.DASHBOARD, p) { onSelect(AppTab.DASHBOARD) }
            BottomIcon(Icons.Rounded.Assignment, selected == AppTab.WORK, p) { onSelect(AppTab.WORK) }
            FilledIconButton(onClick = { onSelect(AppTab.CREATE) }, modifier = Modifier.size(58.dp), colors = IconButtonDefaults.filledIconButtonColors(containerColor = p.primary, contentColor = Color.White)) { Icon(Icons.Rounded.Add, null, Modifier.size(30.dp)) }
            BottomIcon(Icons.Rounded.Inventory2, selected == AppTab.INVENTORY, p) { onSelect(AppTab.INVENTORY) }
            BottomIcon(Icons.Rounded.Apps, moreSelected, p) { onSelect(AppTab.MORE) }
        }
    }
}

@Composable
private fun BottomIcon(icon: androidx.compose.ui.graphics.vector.ImageVector, active: Boolean, p: SpectroPalette, onClick: () -> Unit) {
    IconButton(onClick = onClick) { Icon(icon, null, tint = if (active) p.primary else p.textMuted) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailSheet(r: ProductionRecord, p: SpectroPalette, onDismiss: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit, onOpenHistory: () -> Unit = {}) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(r.date, color = p.textMuted, fontSize = 12.sp); Text(r.clientName, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(if (r.type == RecordType.NEGATIVE) "Evento negativo" else r.orderRef.ifBlank { "Produzione" }, color = if (r.type == RecordType.NEGATIVE) p.danger else p.primary, fontWeight = FontWeight.Bold) }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }
                IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
            }
            Spacer(Modifier.height(16.dp))
            SpectroCard(p = p) {
                if (r.type == RecordType.PRODUCTION) {
                    Metric("Tessuto", r.fabricDisplayName); Spacer(Modifier.height(10.dp)); Row { Metric("Carta", meters(r.printedPaperMeters), modifier = Modifier.weight(1f)); Metric("Tessuto", meters(r.fabricUsedMeters), modifier = Modifier.weight(1f)) }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = p.border); Row { Metric("Fatturato", euro(r.grossRevenue), p.success, Modifier.weight(1f)); Metric("Materie", euro(r.materialCostTotal), modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(12.dp)); Metric("Utile lordo", euro(r.grossProfit), if (r.grossProfit >= 0) p.success else p.danger)
                } else { Text(r.technicalNotes, color = p.text); Spacer(Modifier.height(12.dp)); Metric("Perdita totale", euro(r.materialCostTotal), p.danger) }
            }
            if (r.technicalNotes.isNotBlank() && r.type == RecordType.PRODUCTION) { Spacer(Modifier.height(12.dp)); SpectroCard(p = p) { Text("NOTE TECNICHE", fontWeight = FontWeight.Black, color = p.primary); Spacer(Modifier.height(8.dp)); Text(r.technicalNotes) } }
            if (r.images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(r.images) }
            if (r.type == RecordType.PRODUCTION && r.workOrderId != null) {
                Spacer(Modifier.height(14.dp)); OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(7.dp)); Text("Apri storico completo dell'ordine") }
            }
            Spacer(Modifier.height(16.dp)); GradientButton("Esporta PDF analitico", p, icon = Icons.Rounded.PictureAsPdf) { ReportExporter.share(context, r) }; Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminare la registrazione?") }, text = { Text("Il magazzino verrà riallineato automaticamente se questa produzione ha consumato tessuto.") }, confirmButton = { TextButton(onClick = onDelete) { Text("Elimina", color = p.danger) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } })
}
