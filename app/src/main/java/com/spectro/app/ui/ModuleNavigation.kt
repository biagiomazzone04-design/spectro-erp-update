package com.spectro.app.ui

import android.content.Context
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.ui.theme.SpectroPalette

internal data class ModuleDestination(val tab: AppTab, val title: String, val description: String, val group: String, val icon: ImageVector)
internal val moduleDestinations = listOf(
    ModuleDestination(AppTab.ZENTHA, "Zentha", "Prodotti, squadre, stock, listini e pagamenti", "Gestionali", Icons.Rounded.SportsVolleyball),
    ModuleDestination(AppTab.ECONOMIC, "Altri ricavi", "Lavorazioni, materiali, ricavi e margini", "Gestionali", Icons.Rounded.Payments),
    ModuleDestination(AppTab.COTTON, "Cotone", "Ordini reattivi e pigmento, produzione e costi", "Gestionali", Icons.Rounded.Texture),
    ModuleDestination(AppTab.WORK, "Ordini", "Commesse, consegne e avanzamento produzione", "Operatività", Icons.AutoMirrored.Rounded.Assignment),
    ModuleDestination(AppTab.INVENTORY, "Magazzino", "Tessuti, colli, movimenti e scorte", "Operatività", Icons.Rounded.Inventory2),
    ModuleDestination(AppTab.ACTIVITIES, "Attività", "Scadenze, campioni e progetti da completare", "Operatività", Icons.Rounded.Checklist),
    ModuleDestination(AppTab.ISSUES, "Criticità", "Problemi aperti, responsabilità e ritardi", "Controllo", Icons.Rounded.ReportProblem),
    ModuleDestination(AppTab.CLIENTS, "Clienti", "Anagrafiche, storico unificato e scadenze", "Controllo", Icons.Rounded.People),
    ModuleDestination(AppTab.DASHBOARD, "Dashboard", "Produzioni giornaliere, KPI e statistiche", "Controllo", Icons.Rounded.Analytics)
)
internal fun AppTab.displayName() = when(this) {
    AppTab.MORE -> "Moduli"
    AppTab.CREATE -> "Nuova registrazione"
    else -> moduleDestinations.first { it.tab == this }.title
}

/** Local navigation preferences only; not an authorization or permission system. */
internal object ShortcutStore {
    private val defaults = listOf(AppTab.ZENTHA, AppTab.ECONOMIC, AppTab.CLIENTS)
    fun read(context: Context): List<AppTab> {
        val value = context.getSharedPreferences("spectro_navigation", Context.MODE_PRIVATE).getString("favorites", null) ?: return defaults
        return value.split(',').mapNotNull { raw -> moduleDestinations.firstOrNull { it.tab.name == raw }?.tab }.distinct()
    }
    fun write(context: Context, tabs: List<AppTab>) {
        context.getSharedPreferences("spectro_navigation", Context.MODE_PRIVATE).edit().putString("favorites", tabs.distinct().joinToString(",") { it.name }).apply()
    }
}

@Composable
internal fun ModuleShortcuts(favorites: List<AppTab>, onOpen: (AppTab) -> Unit, onAll: () -> Unit) {
    Column {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Accesso rapido", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            TextButton(onClick = onAll) { Text("Tutti i moduli") }
        }
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            favorites.mapNotNull { tab -> moduleDestinations.firstOrNull { it.tab == tab } }.forEach { module ->
                AssistChip(onClick = { onOpen(module.tab) }, label = { Text(module.title) }, leadingIcon = { Icon(module.icon, null, Modifier.size(18.dp)) })
            }
        }
        if(favorites.isEmpty()) Text("Aggiungi le tue scorciatoie con la stella in Moduli.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
internal fun MoreModulesScreen(
    p: SpectroPalette,
    favorites: List<AppTab>,
    counts: Map<AppTab, String> = emptyMap(),
    onToggleFavorite: (AppTab) -> Unit,
    onOpen: (AppTab) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = moduleDestinations.filter { module -> query.trim().split(Regex("\\s+")).all { token -> "${module.title} ${module.description} ${module.group}".contains(token, true) } }
    LazyVerticalGrid(
        columns = GridCells.Adaptive(280.dp),
        modifier = Modifier.fillMaxSize().testTag("module-grid"),
        contentPadding = PaddingValues(20.dp, 16.dp, 20.dp, 24.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(key = "header", span = { GridItemSpan(maxLineSpan) }) {
            Column {
                Text("Centro moduli", fontSize = 26.sp, fontWeight = FontWeight.Black)
                Text("Gestione e controllo aziendale", color = p.textMuted)
            }
        }
        item(key = "search", span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(query, { query = it }, Modifier.fillMaxWidth().testTag("module-search"), label = { Text("Cerca un modulo") }, singleLine = true,
                leadingIcon = { Icon(Icons.Rounded.Search, null) }, trailingIcon = { if(query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Cancella ricerca moduli") } })
        }
        if(query.isBlank()) item(key = "favorites", span = { GridItemSpan(maxLineSpan) }) {
            Text("★ Aggiungi scorciatoie alla Home", style = MaterialTheme.typography.bodySmall, color = p.textMuted)
        }
        if(results.isEmpty()) item(key = "empty", span = { GridItemSpan(maxLineSpan) }) { Text("Nessun modulo trovato. Prova “Zentha”, “ricavi” o “magazzino”.") }
        results.groupBy { it.group }.forEach { (group, modules) ->
            item(key = group, span = { GridItemSpan(maxLineSpan) }) { Text(group.uppercase(), color = p.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
            items(modules, key = { it.tab.name }) { module ->
                SpectroCard(p = p, modifier = Modifier.animateItem().testTag("module-${module.tab.name}"), onClick = { onOpen(module.tab) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(module.icon, null, Modifier.size(28.dp), tint = p.primary)
                        Text(module.title, Modifier.weight(1f).padding(start = 12.dp), fontSize = 19.sp, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { onToggleFavorite(module.tab) }) {
                            Icon(if(module.tab in favorites) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                if(module.tab in favorites) "Rimuovi ${module.title} dai preferiti" else "Aggiungi ${module.title} ai preferiti",
                                tint = if(module.tab in favorites) p.primary else p.textMuted)
                        }
                    }
                    Text(module.description, color = p.textMuted, style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(counts[module.tab] ?: "Apri modulo", Modifier.weight(1f), fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
                        Icon(Icons.Rounded.ChevronRight, null, tint = p.primary)
                    }
                }
            }
        }
    }
}
