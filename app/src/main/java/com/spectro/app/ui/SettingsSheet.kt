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
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.CloudUpload
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
import com.spectro.app.auth.AppPermission
import com.spectro.app.auth.AuthState
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
    var confirmPublish by remember { mutableStateOf(false) }
    var confirmDownload by remember { mutableStateOf(false) }
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
            item { Text("Account e impostazioni", fontSize = 28.sp, fontWeight = FontWeight.Black) }
            item {
                val access = (vm.authState as? AuthState.SignedIn)?.access
                SpectroCard(p = p) {
                    Text(access?.displayName ?: "Utente Spectro", fontWeight = FontWeight.Black, fontSize = 18.sp)
                    Text(access?.email.orEmpty(), color = p.textMuted)
                    Spacer(Modifier.height(8.dp))
                    Text("${access?.organizationName.orEmpty()} • ${access?.roleName.orEmpty()}", color = p.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(onClick = { vm.signOut(); onDismiss() }, Modifier.fillMaxWidth()) { Text("Disconnetti") }
                }
            }
            if (vm.can(AppPermission.SETTINGS_MANAGE)) item {
                SpectroCard(p = p) {
                    Text("Parametri finanziari", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Spacer(Modifier.height(14.dp))
                    SpectroTextField("Costo fisso giornaliero €", daily, { daily = it }, numeric = true)
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Carta Plotter X1 €/mt", x1, { x1 = it }, numeric = true)
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Carta Plotter X2 €/mt", x2, { x2 = it }, numeric = true)
                    Spacer(Modifier.height(12.dp)); SpectroTextField("Carta protezione €/mt", protection, { protection = it }, numeric = true)
                }
            }
            if (vm.can(AppPermission.SETTINGS_MANAGE)) item {
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
            if (vm.can(AppPermission.SETTINGS_MANAGE)) item {
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
            if (vm.can(AppPermission.SETTINGS_MANAGE)) item {
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
            if (vm.can(AppPermission.SYNC_USE)) item {
                val sync = vm.cloudSyncState
                val inspection = sync.inspection
                val remote = inspection?.remote
                val local = inspection?.local
                val mustDownload = remote?.exists == true && remote.revision != local?.baseRevision
                val localRecordCount = vm.snapshot().recordCount
                SpectroCard(p = p) {
                    Text("Workspace cloud", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        "Sincronizzazione versionata dell’intero gestionale, inclusi gli allegati. Prima di ogni trasferimento viene creato un backup locale di recupero.",
                        color = p.textMuted,
                        fontSize = 12.sp
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("Questa postazione • $localRecordCount record", color = p.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(6.dp))
                    when {
                        inspection == null -> Text("Stato non ancora verificato", color = p.textMuted)
                        remote?.exists != true -> Text("Cloud vuoto • revisione locale ${local?.baseRevision ?: 0}", color = p.primary, fontWeight = FontWeight.Bold)
                        else -> {
                            Text("Cloud rev. ${remote.revision} • ${remote.recordCount} record • ${formatBytes(remote.sizeBytes)}", fontWeight = FontWeight.Bold)
                            Text("Base locale rev. ${local?.baseRevision ?: 0}${remote.updatedBy.takeIf { it.isNotBlank() }?.let { " • ultimo invio: $it" }.orEmpty()}", color = p.textMuted, fontSize = 12.sp)
                        }
                    }
                    if (mustDownload) {
                        Spacer(Modifier.height(8.dp))
                        Text("È presente una revisione diversa: scaricala prima di pubblicare. Nessuna copia verrà sovrascritta automaticamente.", color = p.danger, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                    sync.message?.let { Spacer(Modifier.height(8.dp)); Text(it, color = p.success, fontSize = 12.sp) }
                    sync.error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = p.danger, fontSize = 12.sp) }
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(
                        onClick = vm::inspectCloudWorkspace,
                        enabled = !sync.loading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.CloudSync, null); Spacer(Modifier.width(8.dp)); Text(if (sync.loading) "Sincronizzazione…" else "Verifica stato cloud")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedButton(
                            onClick = { confirmDownload = true },
                            enabled = !sync.loading && remote?.exists == true,
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Rounded.CloudDownload, null); Spacer(Modifier.width(6.dp)); Text("Scarica") }
                        Button(
                            onClick = { confirmPublish = true },
                            enabled = !sync.loading && inspection != null && !mustDownload,
                            modifier = Modifier.weight(1f)
                        ) { Icon(Icons.Rounded.CloudUpload, null); Spacer(Modifier.width(6.dp)); Text("Pubblica") }
                    }
                }
            }
            if (!vm.can(AppPermission.SYNC_USE) && vm.can(AppPermission.USERS_MANAGE)) item {
                SpectroCard(p = p) {
                    Text("Workspace cloud da abilitare", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text(
                        "Il ruolo Admin non ha ancora il permesso sync.use. Esegui la migrazione cloud 003 su Supabase, poi disconnettiti e accedi di nuovo.",
                        color = p.warning,
                        fontSize = 12.sp
                    )
                }
            }
            if (vm.can(AppPermission.BACKUP_MANAGE)) item {
                SpectroCard(p = p) {
                    Text("Backup e sicurezza dati", fontWeight = FontWeight.Black, fontSize = 17.sp)
                    Text("Backup portabile con database e foto allegate. Puoi ripristinarlo anche dopo una reinstallazione.", color = p.textMuted, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    OutlinedButton(onClick = { BackupManager.shareBackup(context, vm.snapshot()) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Backup, null); Spacer(Modifier.width(8.dp)); Text("Esporta backup") }
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/zip", "application/json", "application/octet-stream", "*/*")) }, Modifier.fillMaxWidth()) { Icon(Icons.Rounded.Restore, null); Spacer(Modifier.width(8.dp)); Text("Ripristina da file") }
                }
            }
            if (vm.can(AppPermission.BACKUP_MANAGE)) item {
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

    if (confirmPublish) AlertDialog(
        onDismissRequest = { confirmPublish = false },
        title = { Text("Pubblicare questa postazione?") },
        text = { Text("Verranno preparati ${vm.snapshot().recordCount} record. Sarà creato un backup locale, caricato il workspace completo e pubblicata una nuova revisione solo se il cloud non è cambiato. In caso di conflitto l’operazione si fermerà senza sovrascrivere dati.") },
        confirmButton = { TextButton(onClick = { confirmPublish = false; vm.publishCloudWorkspace() }) { Text("Pubblica") } },
        dismissButton = { TextButton(onClick = { confirmPublish = false }) { Text("Annulla") } }
    )

    if (confirmDownload) AlertDialog(
        onDismissRequest = { confirmDownload = false },
        title = { Text("Scaricare il workspace cloud?") },
        text = { Text("La copia locale corrente verrà salvata automaticamente come backup di recupero, poi sostituita dalla revisione cloud verificata tramite SHA-256.") },
        confirmButton = { TextButton(onClick = { confirmDownload = false; vm.downloadCloudWorkspace() }) { Text("Scarica e applica") } },
        dismissButton = { TextButton(onClick = { confirmDownload = false }) { Text("Annulla") } }
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

private fun formatBytes(bytes: Long): String = when {
    bytes >= 1024L * 1024L -> "%.1f MB".format(bytes / (1024.0 * 1024.0))
    bytes >= 1024L -> "%.1f KB".format(bytes / 1024.0)
    else -> "$bytes B"
}
