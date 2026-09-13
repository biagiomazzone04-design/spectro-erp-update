package com.spectro.app.ui

import android.graphics.BitmapFactory
import android.widget.NumberPicker
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.spectro.app.ui.theme.SpectroPalette
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun SpectroCard(
    modifier: Modifier = Modifier,
    p: SpectroPalette,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(24.dp))
    Column(
        modifier = (if (onClick != null) base.clickable(onClick = onClick) else base).padding(20.dp),
        content = content
    )
}

@Composable
fun SpectroTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    numeric: Boolean = false,
    singleLine: Boolean = true
) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        Spacer(Modifier.height(7.dp))
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { if (placeholder.isNotBlank()) Text(placeholder) },
            singleLine = singleLine,
            minLines = if (singleLine) 1 else 3,
            keyboardOptions = if (numeric) KeyboardOptions(keyboardType = KeyboardType.Decimal) else KeyboardOptions.Default,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun GradientButton(text: String, p: SpectroPalette, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) .975f else 1f, label = "buttonScale")
    Surface(
        modifier = modifier.fillMaxWidth().scale(scale).clip(RoundedCornerShape(17.dp)).clickable(
            interactionSource = interaction,
            indication = null,
            onClick = onClick
        ),
        color = Color.Transparent
    ) {
        Row(
            Modifier.background(p.heroBrush).padding(horizontal = 20.dp, vertical = 17.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) { Icon(icon, null, tint = Color.White); Spacer(Modifier.width(9.dp)) }
            Text(text, color = Color.White, fontWeight = FontWeight.Black, fontSize = 15.sp)
        }
    }
}

@Composable
fun SegmentedChoice(options: List<Pair<String, String>>, selected: String, p: SpectroPalette, onSelected: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(17.dp)).background(p.surface).border(1.dp, p.border, RoundedCornerShape(17.dp)).padding(4.dp)
    ) {
        options.forEach { (key, label) ->
            val active = key == selected
            val color by animateColorAsState(if (active) p.primary else Color.Transparent, label = "segment")
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).background(color).clickable { onSelected(key) }.padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(label, fontWeight = FontWeight.Black, fontSize = 12.sp, color = if (active) Color.White else p.textMuted)
            }
        }
    }
}

@Composable
fun Metric(label: String, value: String, valueColor: Color = MaterialTheme.colorScheme.onSurface, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .52f))
        Spacer(Modifier.height(4.dp))
        Text(value, fontSize = 20.sp, fontWeight = FontWeight.Black, color = valueColor)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = .8f))
        Spacer(Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Black, fontSize = 18.sp)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .55f))
    }
}

@Composable
fun PrivateImage(path: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val bitmap = remember(path) { runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull() }
    if (bitmap != null) Image(bitmap, contentDescription = null, contentScale = contentScale, modifier = modifier.clip(RoundedCornerShape(14.dp)))
    else Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.BrokenImage, null)
    }
}

@Composable
private fun FullScreenImage(path: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier.fillMaxSize().background(Color.Black.copy(alpha = .96f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center
        ) {
            PrivateImage(path, Modifier.fillMaxWidth().fillMaxHeight(.82f).padding(18.dp), ContentScale.Fit)
            FilledIconButton(
                onClick = onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(18.dp),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Color.White.copy(alpha = .15f), contentColor = Color.White)
            ) { Icon(Icons.Rounded.Close, contentDescription = "Chiudi") }
        }
    }
}

@Composable
fun SectionTitle(text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .72f))
        if (actionLabel != null && onAction != null) TextButton(onClick = onAction) { Text(actionLabel) }
    }
}

@Composable
fun ImageStrip(paths: List<String>, onRemove: ((Int) -> Unit)? = null) {
    if (paths.isEmpty()) return
    var viewerPath by remember { mutableStateOf<String?>(null) }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        paths.take(3).forEachIndexed { index, path ->
            Box(Modifier.size(88.dp).clickable { viewerPath = path }) {
                PrivateImage(path, Modifier.fillMaxSize())
                if (onRemove != null) IconButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier.align(Alignment.TopEnd).size(28.dp).background(Color.Black.copy(alpha = .65f), CircleShape)
                ) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
            }
        }
        if (paths.size > 3) Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant).clickable { viewerPath = paths[3] },
            contentAlignment = Alignment.Center
        ) { Text("+${paths.size - 3}", fontWeight = FontWeight.Black) }
    }
    viewerPath?.let { FullScreenImage(it) { viewerPath = null } }
}

fun euro(v: Double): String = "€%,.2f".format(java.util.Locale.ITALY, v)
fun meters(v: Double): String = "%.1f mt".format(java.util.Locale.ITALY, v)
fun String.toDoubleSafe(): Double = replace(',', '.').toDoubleOrNull() ?: 0.0


@Composable
fun WheelDateField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    allowEmpty: Boolean = false
) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        if (label.isNotBlank()) {
            Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
            Spacer(Modifier.height(7.dp))
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = { open = true }, modifier = Modifier.weight(1f), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(horizontal = 16.dp, vertical = 15.dp)) {
                Icon(Icons.Rounded.CalendarMonth, null); Spacer(Modifier.width(9.dp)); Text(value.ifBlank { if (allowEmpty) "Nessuna data" else "Seleziona data" }, modifier = Modifier.weight(1f))
            }
            if (allowEmpty && value.isNotBlank()) { Spacer(Modifier.width(6.dp)); IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, null) } }
        }
    }
    if (open) DateWheelDialog(value, allowEmpty, onDismiss = { open = false }) { selected ->
        onValueChange(selected); open = false
    }
}

@Composable
private fun DateWheelDialog(current: String, allowEmpty: Boolean, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val initial = runCatching { LocalDate.parse(current) }.getOrDefault(LocalDate.now())
    var day by remember { mutableIntStateOf(initial.dayOfMonth) }
    var month by remember { mutableIntStateOf(initial.monthValue) }
    var year by remember { mutableIntStateOf(initial.year) }
    val maxDay = YearMonth.of(year, month).lengthOfMonth()
    LaunchedEffect(month, year) {
        if (day > maxDay) day = maxDay
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona data", fontWeight = FontWeight.Black) },
        text = {
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumberWheel(day, 1, maxDay, Modifier.weight(1f)) { day = it }
                    NumberWheel(month, 1, 12, Modifier.weight(1f), arrayOf("Gen", "Feb", "Mar", "Apr", "Mag", "Giu", "Lug", "Ago", "Set", "Ott", "Nov", "Dic")) { month = it }
                    NumberWheel(year, 2020, 2038, Modifier.weight(1f)) { year = it }
                }
                Spacer(Modifier.height(10.dp))
                Text("${day.toString().padStart(2,'0')}/${month.toString().padStart(2,'0')}/$year", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center, fontWeight = FontWeight.Bold)
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(LocalDate.of(year, month, day.coerceAtMost(YearMonth.of(year, month).lengthOfMonth())).toString()) }) { Text("Conferma") } },
        dismissButton = { Row { if (allowEmpty) TextButton(onClick = { onConfirm("") }) { Text("Svuota") }; TextButton(onClick = onDismiss) { Text("Annulla") } } }
    )
}

@Composable
private fun NumberWheel(value: Int, min: Int, max: Int, modifier: Modifier = Modifier, labels: Array<String>? = null, onValueChange: (Int) -> Unit) {
    AndroidView(
        modifier = modifier.height(128.dp),
        factory = { context ->
            NumberPicker(context).apply {
                minValue = min; maxValue = max; wrapSelectorWheel = false
                if (labels != null && labels.size == max - min + 1) displayedValues = labels
                setOnValueChangedListener { _, _, newVal -> onValueChange(newVal) }
            }
        },
        update = { picker ->
            if (picker.minValue != min || picker.maxValue != max) { picker.displayedValues = null; picker.minValue = min; picker.maxValue = max; if (labels != null && labels.size == max-min+1) picker.displayedValues = labels }
            picker.value = value.coerceIn(min, max)
        }
    )
}

@Composable
fun <T> SimpleSelectField(
    label: String,
    selectedLabel: String,
    options: List<Pair<T, String>>,
    modifier: Modifier = Modifier,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(label.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onSurface.copy(alpha = .58f))
        Spacer(Modifier.height(7.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                Text(selectedLabel, modifier = Modifier.weight(1f)); Icon(Icons.Rounded.ExpandMore, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { (key, text) -> DropdownMenuItem(text = { Text(text) }, onClick = { onSelected(key); expanded = false }) }
            }
        }
    }
}

fun workStatusLabel(status: com.spectro.app.data.WorkStatus): String = when (status) {
    com.spectro.app.data.WorkStatus.DRAFT -> "Bozza"
    com.spectro.app.data.WorkStatus.RECEIVED, com.spectro.app.data.WorkStatus.IN_PROCESS -> "In processo"
    com.spectro.app.data.WorkStatus.TRANSFERRED_TO_PRODUCTION -> "Trasferito in produzione"
    com.spectro.app.data.WorkStatus.WAITING_MATERIAL -> "Attesa materiale"
    com.spectro.app.data.WorkStatus.WAITING_CLIENT -> "Attesa cliente"
    com.spectro.app.data.WorkStatus.IN_PRODUCTION, com.spectro.app.data.WorkStatus.IN_PROGRESS -> "In produzione"
    com.spectro.app.data.WorkStatus.PRINTED -> "Stampato"
    com.spectro.app.data.WorkStatus.TRANSFERRED -> "Trasferito"
    com.spectro.app.data.WorkStatus.READY -> "Pronto"
    com.spectro.app.data.WorkStatus.COMPLETED -> "Completato"
    com.spectro.app.data.WorkStatus.DELIVERED -> "Evaso"
    com.spectro.app.data.WorkStatus.CLOSED -> "Chiuso"
    com.spectro.app.data.WorkStatus.ON_HOLD -> "Sospeso"
    com.spectro.app.data.WorkStatus.BLOCKED -> "Bloccato"
    com.spectro.app.data.WorkStatus.CANCELLED -> "Annullato"
}

fun localizeWorkStatusTokens(text: String): String {
    var result = text
    com.spectro.app.data.WorkStatus.entries
        .sortedByDescending { it.name.length }
        .forEach { status ->
            result = result.replace(Regex("\\b${Regex.escape(status.name)}\\b"), workStatusLabel(status))
        }
    return result
}

fun issueCategoryLabel(v: com.spectro.app.data.IssueCategory): String = when (v) {
    com.spectro.app.data.IssueCategory.GRAPHICS -> "File / grafica"
    com.spectro.app.data.IssueCategory.APPROVAL -> "Approvazione cliente"
    com.spectro.app.data.IssueCategory.MATERIAL -> "Materiale / tessuto"
    com.spectro.app.data.IssueCategory.ORDER_CHANGE -> "Modifica ordine"
    com.spectro.app.data.IssueCategory.PAYMENT -> "Pagamento"
    com.spectro.app.data.IssueCategory.QUALITY -> "Qualità / contestazione"
    com.spectro.app.data.IssueCategory.INFORMATION -> "Informazioni mancanti"
    com.spectro.app.data.IssueCategory.OTHER -> "Altro"
}

fun issueResponsibilityLabel(v: com.spectro.app.data.IssueResponsibility): String = when (v) {
    com.spectro.app.data.IssueResponsibility.CLIENT -> "Cliente"
    com.spectro.app.data.IssueResponsibility.INTERNAL -> "Interna"
    com.spectro.app.data.IssueResponsibility.SUPPLIER -> "Fornitore"
    com.spectro.app.data.IssueResponsibility.MIXED -> "Mista"
    com.spectro.app.data.IssueResponsibility.TO_VERIFY -> "Da verificare"
}

fun issueSeverityLabel(v: com.spectro.app.data.IssueSeverity): String = when (v) {
    com.spectro.app.data.IssueSeverity.LOW -> "Bassa"
    com.spectro.app.data.IssueSeverity.MEDIUM -> "Media"
    com.spectro.app.data.IssueSeverity.HIGH -> "Alta"
    com.spectro.app.data.IssueSeverity.CRITICAL -> "Critica"
}

fun issueStatusLabel(v: com.spectro.app.data.IssueStatus): String = when (v) {
    com.spectro.app.data.IssueStatus.OPEN -> "Aperta"
    com.spectro.app.data.IssueStatus.WAITING_CLIENT -> "Attesa cliente"
    com.spectro.app.data.IssueStatus.MONITORING -> "Monitoraggio"
    com.spectro.app.data.IssueStatus.RESOLVED -> "Risolta"
    com.spectro.app.data.IssueStatus.CLOSED -> "Chiusa"
}

fun timelineCategoryLabel(v: com.spectro.app.data.TimelineCategory): String = when (v) {
    com.spectro.app.data.TimelineCategory.NOTE -> "Nota"
    com.spectro.app.data.TimelineCategory.CLIENT -> "Cliente"
    com.spectro.app.data.TimelineCategory.INTERNAL_PROBLEM -> "Problema interno"
    com.spectro.app.data.TimelineCategory.GRAPHICS -> "Grafica"
    com.spectro.app.data.TimelineCategory.FILE_ERROR -> "File errato"
    com.spectro.app.data.TimelineCategory.MATERIAL -> "Materiale"
    com.spectro.app.data.TimelineCategory.SAMPLE -> "Campionatura"
    com.spectro.app.data.TimelineCategory.APPROVAL -> "Approvazione"
    com.spectro.app.data.TimelineCategory.PRODUCTION -> "Produzione"
    com.spectro.app.data.TimelineCategory.ORDER_CHANGE -> "Modifica ordine"
    com.spectro.app.data.TimelineCategory.DELAY -> "Ritardo"
    com.spectro.app.data.TimelineCategory.DELIVERY -> "Consegna"
    com.spectro.app.data.TimelineCategory.STATUS_CHANGE -> "Cambio stato"
}

fun timelineResponsibilityLabel(v: com.spectro.app.data.TimelineResponsibility): String = when (v) {
    com.spectro.app.data.TimelineResponsibility.CLIENT -> "Cliente"
    com.spectro.app.data.TimelineResponsibility.INTERNAL -> "Interna"
    com.spectro.app.data.TimelineResponsibility.SUPPLIER -> "Fornitore"
    com.spectro.app.data.TimelineResponsibility.NONE -> "Nessuna"
}

fun timelineSeverityLabel(v: com.spectro.app.data.TimelineSeverity): String = when (v) {
    com.spectro.app.data.TimelineSeverity.INFO -> "Informativa"
    com.spectro.app.data.TimelineSeverity.LOW -> "Bassa"
    com.spectro.app.data.TimelineSeverity.MEDIUM -> "Media"
    com.spectro.app.data.TimelineSeverity.HIGH -> "Alta"
    com.spectro.app.data.TimelineSeverity.BLOCKING -> "Bloccante"
}
