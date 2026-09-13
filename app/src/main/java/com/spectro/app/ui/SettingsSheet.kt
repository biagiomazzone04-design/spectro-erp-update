package com.spectro.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.data.AppSettings
import com.spectro.app.ui.theme.SpectroPalette
import com.spectro.app.ui.theme.SpectroPalettes
import com.spectro.app.util.BackupManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(vm: SpectroViewModel, p: SpectroPalette, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var daily by remember(vm.state.settings) { mutableStateOf(vm.state.settings.dailyFixedCost.toString()) }
    var x1 by remember(vm.state.settings) { mutableStateOf(vm.state.settings.plotterX1Cost.toString()) }
    var x2 by remember(vm.state.settings) { mutableStateOf(vm.state.settings.plotterX2Cost.toString()) }
    var protection by remember(vm.state.settings) { mutableStateOf(vm.state.settings.protectionPaperCost.toString()) }
    var zenthaA by remember(vm.state.settings) { mutableStateOf(vm.state.settings.zenthaSplitA.toString()) }
    var zenthaB by remember(vm.state.settings) { mutableStateOf(vm.state.settings.zenthaSplitB.toString()) }
    var theme by remember(vm.state.settings) { mutableStateOf(vm.state.settings.themeKey) }
    var confirmReset by remember { mutableStateOf(false) }
    var restoreError by remember { mutableStateOf<String?>(null) }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) runCatching { BackupManager.readBackup(context, uri) }
            .onSuccess { vm.restore(it) }
            .onFailure { restoreError = it.message ?: "Backup non valido o non leggibile." }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = p.background) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(20.dp, 4.dp, 20.dp, 48.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Text("Impostazioni", fontSize = 28.sp, fontWeight = FontWeight.Black) }
            item {
                SpectroCard(p = p) {
                    Text("Parametri finanziari", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    SpectroTextField("Costo fisso giornaliero €", daily, { daily = it }, numeric = true)
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Carta Plotter X1 €/mt", x1, { x1 = it }, numeric = true)
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Carta Plotter X2 €/mt", x2, { x2 = it }, numeric = true)
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Carta protezione €/mt", protection, { protection = it }, numeric = true)
                }
            }
            item {
                SpectroCard(p = p) {
                    Text("ZENTHA • Ripartizione utile", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Le due quote devono totalizzare 100%. Il valore viene copiato nei nuovi ordini e resta congelato negli ordini già creati.", color = p.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpectroTextField("Quota A %", zenthaA, { zenthaA = it }, Modifier.weight(1f), numeric = true)
                        SpectroTextField("Quota B %", zenthaB, { zenthaB = it }, Modifier.weight(1f), numeric = true)
                    }
                    val total = zenthaA.toDoubleSafe() + zenthaB.toDoubleSafe()
                    Spacer(Modifier.height(6.dp))
                    Text("Totale: ${"%.1f".format(total)}%", color = if (kotlin.math.abs(total - 100.0) < .01) p.success else p.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
            item {
                SpectroCard(p = p) {
                    Text("Tema grafico", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SpectroPalettes.forEach { palette ->
                            val active = theme == palette.key
                            Column(
                                Modifier.weight(1f).clickable { theme = palette.key },
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Box(
                                    Modifier.size(if (active) 52.dp else 44.dp).background(palette.heroBrush, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) { if (active) Text("✓", color = androidx.compose.ui.graphics.Color.White, fontWeight = FontWeight.Black) }
                                Spacer(Modifier.height(6.dp)); Text(palette.name.substringBefore(' '), fontSize = 10.sp, color = if (active) p.text else p.textMuted)
                            }
                        }
                    }
                }
            }
            item {
                GradientButton("Salva impostazioni", p) {
                    vm.saveSettings(AppSettings(
                        dailyFixedCost = daily.toDoubleSafe(),
                        plotterX1Cost = x1.toDoubleSafe(),
                        plotterX2Cost = x2.toDoubleSafe(),
                        protectionPaperCost = protection.toDoubleSafe(),
                        themeKey = theme,
                        zenthaSplitA = zenthaA.toDoubleSafe(),
                        zenthaSplitB = zenthaB.toDoubleSafe()
                    ))
                    onDismiss()
                }
            }
            item {
                SpectroCard(p = p) {
                    Text("Backup e sicurezza dati", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Backup portabile con database e foto allegate. Puoi ripristinarlo anche dopo una reinstallazione.", color = p.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { BackupManager.shareBackup(context, vm.snapshot()) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Backup, null); Spacer(Modifier.width(8.dp)); Text("Esporta backup") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(8.dp)); Text("Ripristina da file") }
                }
            }
            item {
                OutlinedButton(onClick = { confirmReset = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(contentColor = p.danger)) {
                    Icon(Icons.Rounded.DeleteForever, null); Spacer(Modifier.width(8.dp)); Text("Azzera tutti i dati")
                }
            }
        }
    }

    if (confirmReset) AlertDialog(
        onDismissRequest = { confirmReset = false },
        title = { Text("Cancellare tutto?") },
        text = { Text("Clienti, produzione, lavorazioni e magazzino verranno eliminati. Crea prima un backup se ti servono.") },
        confirmButton = { TextButton(onClick = { vm.resetAll(); confirmReset = false; onDismiss() }) { Text("Azzera", color = p.danger) } },
        dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Annulla") } }
    )

    restoreError?.let { message ->
        AlertDialog(
            onDismissRequest = { restoreError = null },
            title = { Text("Ripristino non riuscito") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { restoreError = null }) { Text("OK") } }
        )
    }
}
