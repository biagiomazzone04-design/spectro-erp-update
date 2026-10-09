package com.spectro.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
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
    val context = LocalContext.current
    var favorites by remember { mutableStateOf(ShortcutStore.read(context)) }
    val screenState = rememberSaveableStateHolder()
    val state = vm.state
    val palette = paletteFor(state.settings.themeKey)
    var tab by rememberSaveable { mutableStateOf(AppTab.DASHBOARD) }
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
    var clientReturn by remember { mutableStateOf<Client?>(null) }
    var clientProfileSection by remember { mutableStateOf(ClientProfileSection.HOME) }
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
        selectedOrder = null; clientReturn = null; editingProduction = null; editingWork = null; sourceWork = order; createMode = CreateMode.PRODUCTION; navigate(AppTab.CREATE)
    }
    fun editOrder(order: WorkOrder) {
        selectedOrder = null; clientReturn = null; editingWork = order; editingProduction = null; sourceWork = null; createMode = CreateMode.WORK; navigate(AppTab.CREATE)
    }
    fun openOrder(order: WorkOrder) { selectedOrder = order }
    fun closeOrder() {
        selectedOrder = null
        clientReturn?.let { client ->
            selectedClient = client
            clientReturn = null
        }
    }

    BackHandler(enabled = selectedOrder != null) { closeOrder() }
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
                    Column(
                        Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            if (tab != AppTab.DASHBOARD) {
                                IconButton(onClick = { goBack() }, modifier = Modifier.size(48.dp)) {
                                    Icon(Icons.Rounded.ArrowBack, "Indietro", tint = palette.text)
                                }
                                Spacer(Modifier.width(4.dp))
                            }
                            Image(
                                painter = painterResource(R.drawable.spectro_header_logo),
                                contentDescription = "Spectro",
                                modifier = Modifier.width(76.dp).height(56.dp),
                                contentScale = ContentScale.Fit
                            )
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                BasicText("SPECTRO", modifier = Modifier.fillMaxWidth(), maxLines = 1, softWrap = false,
                                    autoSize = TextAutoSize.StepBased(minFontSize = 10.sp, maxFontSize = 28.sp),
                                    style = TextStyle(fontWeight = FontWeight.Black, color = palette.primary, letterSpacing = 1.2.sp))
                                Text(
                                    "ERP 3.0 • NATIVE",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    color = palette.text,
                                    letterSpacing = 1.1.sp,
                                    maxLines = 1
                                )
                            }
                        }
                        val openTaskCount = vm.openTasks().size
                        val urgentTaskCount = vm.urgentTasks().size
                        Row(
                            Modifier.fillMaxWidth().padding(top = 4.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { navigate(AppTab.MORE) }, modifier = Modifier.weight(1f)) {
                                Icon(Icons.Rounded.Apps, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if(tab == AppTab.DASHBOARD) "Moduli" else tab.displayName(), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            FilledIconButton(
                                onClick = { searchOpen = true },
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)
                            ) { Icon(Icons.Rounded.Search, "Cerca", tint = palette.text) }
                            Spacer(Modifier.width(8.dp))
                            BadgedBox(
                                badge = {
                                    if (openTaskCount > 0) {
                                        Badge(containerColor = if (urgentTaskCount > 0) palette.secondary else palette.primary, contentColor = Color.Black) {
                                            Text(openTaskCount.coerceAtMost(99).toString(), fontWeight = FontWeight.Black)
                                        }
                                    }
                                }
                            ) {
                                FilledIconButton(
                                    onClick = { navigate(AppTab.ACTIVITIES) },
                                    modifier = Modifier.size(48.dp),
                                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)
                                ) {
                                    Icon(Icons.Rounded.NotificationsActive, "Attività", tint = if (urgentTaskCount > 0) palette.secondary else palette.text)
                                }
                            }
                            Spacer(Modifier.width(8.dp))
                            FilledIconButton(
                                onClick = { settingsOpen = true },
                                modifier = Modifier.size(48.dp),
                                colors = IconButtonDefaults.filledIconButtonColors(containerColor = palette.surface)
                            ) { Icon(Icons.Rounded.Settings, "Impostazioni", tint = palette.text) }
                        }
                    }
                }
            },
            bottomBar = { SpectroBottomBar(tab, palette) { next -> if (next == AppTab.CREATE) { editingProduction = null; editingWork = null; sourceWork = null; createMode = CreateMode.PRODUCTION }; navigate(next) } }
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding()) {
                AnimatedContent(tab, transitionSpec = { (fadeIn(tween(200)) + slideInVertically(tween(200)) { it / 40 }) togetherWith (fadeOut(tween(120)) + slideOutVertically(tween(120)) { -it / 60 }) }, label = "tabs") { current ->
                    screenState.SaveableStateProvider(current.name) {
                    when (current) {
                        AppTab.DASHBOARD -> DashboardScreen(
                            vm,
                            palette,
                            onRecord = { detail = it },
                            onOpenOrder = ::openOrder,
                            onEditOrder = ::editOrder,
                            shortcuts = { ModuleShortcuts(favorites, { navigate(it) }, { navigate(AppTab.MORE) }) }
                        )
                        AppTab.WORK -> WorkOrdersScreen(vm, palette, onEdit = ::editOrder, onOpen = ::openOrder, onStartProduction = ::startProduction)
                        AppTab.CREATE -> CreateScreen(vm, palette, createMode, editingProduction, editingWork, sourceWork) { done -> editingProduction = null; editingWork = null; sourceWork = null; navigate(if (done == CreateMode.WORK) AppTab.WORK else AppTab.DASHBOARD, rememberCurrent = false) }
                        AppTab.INVENTORY -> InventoryScreen(vm, palette)
                        AppTab.CLIENTS -> ClientsScreen(
                            vm,
                            palette,
                            onOpenClient = {
                                clientProfileSection = ClientProfileSection.HOME
                                selectedClient = it
                            }
                        )
                        AppTab.ISSUES -> IssuesCenterScreen(vm, palette, onOpenOrder = ::openOrder)
                        AppTab.COTTON -> CottonScreen(vm, palette, initialOpenOrderId = cottonOpenId, onOpenConsumed = { cottonOpenId = null })
                        AppTab.ZENTHA -> ZenthaScreen(vm, palette, initialOpenOrderId = zenthaOpenId, onOpenConsumed = { zenthaOpenId = null })
                        AppTab.ECONOMIC -> EconomicWorkScreen(vm, palette)
                        AppTab.ACTIVITIES -> OperationalTasksScreen(vm, palette, onOpenOrder = ::openOrder)
                        AppTab.MORE -> MoreModulesScreen(palette, favorites,
                            counts = mapOf(AppTab.CLIENTS to "${state.clients.size} clienti", AppTab.ISSUES to "${vm.openIssues().size} criticità aperte", AppTab.ACTIVITIES to "${vm.openTasks().size} attività • ${vm.urgentTasks().size} urgenti", AppTab.ZENTHA to "${state.zenthaOrders.count { !it.status.isFinished() }} ordini aperti", AppTab.ECONOMIC to "${state.economicWorks.size} lavorazioni"),
                            onToggleFavorite = { destination -> favorites = if(destination in favorites) favorites - destination else favorites + destination; ShortcutStore.write(context, favorites) },
                            onOpen = { navigate(it) })
                    }
                    }
                }
            }
        }

        if (settingsOpen) SettingsSheet(vm, palette) { settingsOpen = false }
        if (searchOpen) GlobalSearchSheet(
            vm = vm,
            p = palette,
            onDismiss = { searchOpen = false },
            onOpenClient = { clientProfileSection = ClientProfileSection.HOME; selectedClient = it },
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
                onOpenOrder = {
                    clientReturn = selectedClient
                    selectedClient = null
                    openOrder(it)
                },
                onOpenProduction = {
                    clientReturn = selectedClient
                    selectedClient = null
                    detail = it
                },
                onOpenInventory = {
                    clientReturn = selectedClient
                    selectedClient = null
                    selectedInventory = it
                },
                onOpenCotton = { selectedClient = null; cottonOpenId = it.id; navigate(AppTab.COTTON) },
                initialSection = clientProfileSection,
                onSectionChange = { clientProfileSection = it }
            )
        }
        selectedInventory?.let { item ->
            InventoryDetailSheet(vm, palette, item, onDismiss = {
                selectedInventory = null
                clientReturn?.let { client -> selectedClient = client; clientReturn = null }
            })
        }
        selectedOrder?.let { order ->
            OrderTimelineScreen(
                vm = vm,
                p = palette,
                order = order,
                onBack = { closeOrder() },
                onEdit = ::editOrder,
                onStartProduction = ::startProduction,
                onComplete = { vm.completeWorkOrder(it.id) },
                onReopen = { vm.reopenWorkOrder(it.id) }
            )
        }
        detail?.let { r ->
            val linkedOrder = r.workOrderId?.let { id -> vm.state.workOrders.firstOrNull { it.id == id } }
            RecordDetailSheet(
                r = r,
                p = palette,
                linkedOrder = linkedOrder,
                onDismiss = {
                    detail = null
                    clientReturn?.let { client -> selectedClient = client; clientReturn = null }
                },
                onEdit = { editingProduction = r; editingWork = null; sourceWork = null; createMode = if (r.type == RecordType.NEGATIVE) CreateMode.NEGATIVE else CreateMode.PRODUCTION; detail = null; navigate(AppTab.CREATE) },
                onDelete = {
                    vm.deleteProduction(r.id)
                    detail = null
                    clientReturn?.let { client -> selectedClient = client; clientReturn = null }
                },
                onOpenHistory = {
                    linkedOrder?.let { order -> detail = null; openOrder(order) }
                },
                onCompleteOrder = {
                    linkedOrder?.let { vm.completeWorkOrder(it.id) }
                    detail = null
                    clientReturn?.let { client -> selectedClient = client; clientReturn = null }
                }
            )
        }
    }
}

@Composable
private fun SpectroBottomBar(selected: AppTab, p: SpectroPalette, onSelect: (AppTab) -> Unit) {
    val main = listOf(AppTab.DASHBOARD, AppTab.WORK, AppTab.CREATE, AppTab.INVENTORY, AppTab.MORE)
    NavigationBar(containerColor = p.surface, tonalElevation = 0.dp) {
        main.forEach { destination ->
            val active = selected == destination || (destination == AppTab.MORE && selected !in main)
            val title = when(destination) { AppTab.DASHBOARD -> "Home"; AppTab.CREATE -> "Crea"; else -> destination.displayName() }
            val icon = when(destination) { AppTab.DASHBOARD -> Icons.Rounded.GridView; AppTab.WORK -> Icons.Rounded.Assignment; AppTab.CREATE -> Icons.Rounded.Add; AppTab.INVENTORY -> Icons.Rounded.Inventory2; else -> Icons.Rounded.Apps }
            NavigationBarItem(modifier = Modifier.testTag("nav-${destination.name}"), selected = active, onClick = { onSelect(destination) }, icon = { Icon(icon, null) }, label = { Text(title, maxLines = 1, fontSize = 10.sp) },
                colors = NavigationBarItemDefaults.colors(selectedIconColor = p.primary, selectedTextColor = p.text, indicatorColor = p.primary.copy(alpha = .14f), unselectedIconColor = p.textMuted, unselectedTextColor = p.textMuted))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordDetailSheet(
    r: ProductionRecord,
    p: SpectroPalette,
    linkedOrder: WorkOrder? = null,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onOpenHistory: () -> Unit = {},
    onCompleteOrder: () -> Unit = {}
) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(20.dp).navigationBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) { Text(r.date, color = p.textMuted, fontSize = 12.sp); Text(r.clientName, fontSize = 26.sp, fontWeight = FontWeight.Black); Text(if (r.type == RecordType.NEGATIVE) "Evento negativo" else r.orderRef.ifBlank { "Produzione" }, color = if (r.type == RecordType.NEGATIVE) p.danger else p.primary, fontWeight = FontWeight.Bold) }
                IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, null, tint = p.primary) }
                if (linkedOrder?.status?.isArchivedOrder() != true) {
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, null, tint = p.danger) }
                }
            }
            Spacer(Modifier.height(16.dp))
            SpectroCard(p = p) {
                if (r.type == RecordType.PRODUCTION) {
                    Metric("Tessuto", r.fabricDisplayName); Text("Disegni: ${r.designs.joinToString().ifBlank { "—" }}"); Spacer(Modifier.height(10.dp)); Row { Metric("Carta", meters(r.printedPaperMeters), modifier = Modifier.weight(1f)); Metric("Tessuto", meters(r.fabricUsedMeters), modifier = Modifier.weight(1f)) }
                    HorizontalDivider(Modifier.padding(vertical = 14.dp), color = p.border); Row { Metric("Fatturato", euro(r.grossRevenue), p.success, Modifier.weight(1f)); Metric("Materie", euro(r.materialCostTotal), modifier = Modifier.weight(1f)) }; Spacer(Modifier.height(12.dp)); Metric("Utile lordo", euro(r.grossProfit), if (r.grossProfit >= 0) p.success else p.danger)
                } else { Text(r.technicalNotes, color = p.text); Spacer(Modifier.height(12.dp)); Metric("Perdita totale", euro(r.materialCostTotal), p.danger) }
            }
            if (r.technicalNotes.isNotBlank() && r.type == RecordType.PRODUCTION) { Spacer(Modifier.height(12.dp)); SpectroCard(p = p) { Text("NOTE TECNICHE", fontWeight = FontWeight.Black, color = p.primary); Spacer(Modifier.height(8.dp)); Text(r.technicalNotes) } }
            if (r.images.isNotEmpty()) { Spacer(Modifier.height(12.dp)); ImageStrip(r.images) }
            if (r.type == RecordType.PRODUCTION && r.workOrderId != null) {
                Spacer(Modifier.height(14.dp))
                OutlinedButton(onClick = onOpenHistory, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Rounded.History, null); Spacer(Modifier.width(7.dp)); Text("Apri storico completo dell'ordine") }
                if (linkedOrder != null && !linkedOrder.status.isFinishedOrder()) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onCompleteOrder,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = p.success)
                    ) {
                        Icon(Icons.Rounded.TaskAlt, null); Spacer(Modifier.width(7.dp)); Text("Completa ed evadi ordine")
                    }
                } else if (linkedOrder?.status?.isArchivedOrder() == true) {
                    Spacer(Modifier.height(8.dp))
                    Text("Ordine archiviato nello storico cliente • ${linkedOrder.effectiveDeliveryDate.ifBlank { "data non disponibile" }}", color = p.success, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(16.dp)); GradientButton("Esporta PDF analitico", p, icon = Icons.Rounded.PictureAsPdf) { ReportExporter.share(context, r) }; Spacer(Modifier.height(24.dp))
        }
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("Eliminare la registrazione?") }, text = { Text("Il magazzino verrà riallineato automaticamente se questa produzione ha consumato tessuto.") }, confirmButton = { TextButton(onClick = onDelete) { Text("Elimina", color = p.danger) } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annulla") } })
}
