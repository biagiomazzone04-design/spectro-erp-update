package com.spectro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spectro.app.data.FabricRoll
import com.spectro.app.ui.theme.SpectroPalette
import kotlin.math.abs

@Composable
fun RollAllocationDialog(
    p: SpectroPalette,
    rolls: List<FabricRoll>,
    targetMeters: Double,
    initial: Map<Long, Double> = emptyMap(),
    onDismiss: () -> Unit,
    onConfirm: (Map<Long, Double>) -> Unit
) {
    var values by remember(rolls, initial) { mutableStateOf(rolls.associate { it.id to (initial[it.id]?.takeIf { v -> v > 0 }?.toString() ?: "") }) }
    val total = values.values.sumOf { it.toDoubleSafe() }
    val valid = targetMeters <= 0 || abs(total - targetMeters) <= .05
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona colli", fontWeight = FontWeight.Black) },
        text = {
            LazyColumn(Modifier.heightIn(max = 520.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text("Da prelevare: ${meters(targetMeters)} • Selezionati: ${meters(total)}", color = if (valid) p.success else p.warning, fontWeight = FontWeight.Bold) }
                items(rolls.filter { it.availableMeters > 0 || (initial[it.id] ?: 0.0) > 0 }, key = { it.id }) { roll ->
                    Column {
                        Text("${roll.rollCode} • disponibili ${meters(roll.availableMeters)}", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(5.dp))
                        SpectroTextField("Metri da usare", values[roll.id].orEmpty(), { v -> values = values.toMutableMap().also { it[roll.id] = v } }, numeric = true)
                    }
                }
            }
        },
        confirmButton = { Button(enabled = valid && total > 0, onClick = { onConfirm(values.mapValues { it.value.toDoubleSafe() }.filterValues { it > 0 }) }) { Text("Conferma colli") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } }
    )
}
