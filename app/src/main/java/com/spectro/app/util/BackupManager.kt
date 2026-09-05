package com.spectro.app.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.spectro.app.data.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object BackupManager {
    private const val DATA_ENTRY = "data.json"
    private const val ATTACHMENT_PREFIX = "attachment://"

    fun shareBackup(context: Context, snapshot: SpectroSnapshot) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Spectro_Backup_${System.currentTimeMillis()}.spectrobackup")
        val replacements = linkedMapOf<String, String>()
        val allImages = buildList {
            snapshot.inventory.forEach { addAll(it.images) }
            snapshot.production.forEach { addAll(it.images) }
            snapshot.workOrders.forEach { addAll(it.images) }
            snapshot.clientIssues.forEach { addAll(it.evidenceImages) }
            snapshot.issueUpdates.forEach { addAll(it.images) }
        }.distinct()

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            allImages.forEachIndexed { index, path ->
                val source = File(path)
                if (!source.isFile) return@forEachIndexed
                val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val entryName = "attachments/${index}_${safeName}"
                replacements[path] = ATTACHMENT_PREFIX + entryName
                zip.putNextEntry(ZipEntry(entryName)); source.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            val portable = snapshot.mapImages { replacements[it] ?: it }
            zip.putNextEntry(ZipEntry(DATA_ENTRY)); zip.write(portable.toJson().toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }, "Salva o condividi backup Spectro"))
    }

    fun readBackup(context: Context, uri: android.net.Uri): SpectroSnapshot {
        val temp = File(context.cacheDir, "spectro_restore_${System.currentTimeMillis()}.tmp")
        context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Impossibile leggere il backup." }; temp.outputStream().use { input.copyTo(it) } }
        return try {
            val head = temp.inputStream().buffered().use { it.read() to it.read() }
            if (head.first == 'P'.code && head.second == 'K'.code) readPortableZip(context, temp) else JSONObject(temp.readText()).toSnapshot()
        } finally { temp.delete() }
    }

    private fun readPortableZip(context: Context, file: File): SpectroSnapshot {
        val restoredDir = File(context.filesDir, "attachments").apply { mkdirs() }
        val restoredPaths = mutableMapOf<String, String>(); var jsonText: String? = null
        ZipInputStream(file.inputStream().buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                when {
                    entry.name == DATA_ENTRY -> jsonText = zip.readBytes().toString(Charsets.UTF_8)
                    entry.name.startsWith("attachments/") && !entry.isDirectory -> {
                        val base = File(entry.name).name; require(base.isNotBlank()) { "Allegato backup non valido." }
                        val target = File(restoredDir, "restored_${System.currentTimeMillis()}_$base")
                        target.outputStream().use { zip.copyTo(it) }; restoredPaths[ATTACHMENT_PREFIX + entry.name] = target.absolutePath
                    }
                }
                zip.closeEntry(); entry = zip.nextEntry
            }
        }
        return JSONObject(requireNotNull(jsonText) { "Backup Spectro non valido: manca data.json." }).toSnapshot().mapImages { restoredPaths[it] ?: it }
    }

    private fun SpectroSnapshot.mapImages(transform: (String) -> String) = copy(
        inventory = inventory.map { it.copy(images = it.images.map(transform)) },
        production = production.map { it.copy(images = it.images.map(transform)) },
        workOrders = workOrders.map { it.copy(images = it.images.map(transform)) },
        clientIssues = clientIssues.map { it.copy(evidenceImages = it.evidenceImages.map(transform)) },
        issueUpdates = issueUpdates.map { it.copy(images = it.images.map(transform)) }
    )

    private fun SpectroSnapshot.toJson() = JSONObject().apply {
        put("format", "spectro-native-backup"); put("version", 4)
        put("clients", JSONArray().apply { clients.forEach { put(it.toJson()) } })
        put("inventory", JSONArray().apply { inventory.forEach { put(it.toJson()) } })
        put("production", JSONArray().apply { production.forEach { put(it.toJson()) } })
        put("workOrders", JSONArray().apply { workOrders.forEach { put(it.toJson()) } })
        put("clientIssues", JSONArray().apply { clientIssues.forEach { put(it.toJson()) } })
        put("issueUpdates", JSONArray().apply { issueUpdates.forEach { put(it.toJson()) } })
        put("payments", JSONArray().apply { payments.forEach { put(it.toJson()) } })
        put("settings", settings.toJson())
    }

    private fun Client.toJson() = JSONObject().apply { put("id", id); put("name", name); put("defaultPrintPrice", defaultPrintPrice) }
    private fun InventoryItem.toJson() = JSONObject().apply { put("id", id); put("ownerType", ownerType.name); put("ownerName", ownerName); put("fabricName", fabricName); put("quantityMeters", quantityMeters); put("purchasePricePerMeter", purchasePricePerMeter); put("createdDate", createdDate); put("images", JSONArray(images)); put("minimumQuantityMeters", minimumQuantityMeters) }
    private fun ProductionRecord.toJson() = JSONObject().apply {
        put("id", id); put("type", type.name); put("date", date); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("designs", JSONArray(designs)); put("printPricePerMeter", printPricePerMeter); put("plotter", plotter)
        put("printedPaperMeters", printedPaperMeters); put("protectionPaperMeters", protectionPaperMeters); put("paperWasteMeters", paperWasteMeters); put("fabricMode", fabricMode.name); put("fabricId", fabricId ?: JSONObject.NULL); put("fabricUsedMeters", fabricUsedMeters); put("fabricWasteMeters", fabricWasteMeters)
        put("fabricSalePricePerMeter", fabricSalePricePerMeter); put("manualFabricName", manualFabricName); put("fabricDisplayName", fabricDisplayName); put("technicalNotes", technicalNotes); put("images", JSONArray(images)); put("refundCost", refundCost); put("fabricLossCost", fabricLossCost); put("applyFixedCosts", applyFixedCosts)
        put("paperPrintCost", paperPrintCost); put("protectionPaperCost", protectionPaperCost); put("fabricUsedCost", fabricUsedCost); put("materialCostTotal", materialCostTotal); put("grossRevenue", grossRevenue); put("grossProfit", grossProfit)
    }
    private fun WorkOrder.toJson() = JSONObject().apply { put("id", id); put("receivedDate", receivedDate); put("client", client); put("title", title); put("description", description); put("images", JSONArray(images)); put("status", status.name); put("dueDate", dueDate); put("priority", priority.name); put("blocker", blocker.name) }
    private fun ClientIssue.toJson() = JSONObject().apply { put("id", id); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("title", title); put("category", category.name); put("responsibility", responsibility.name); put("severity", severity.name); put("status", status.name); put("openedDate", openedDate); put("expectedDeliveryDate", expectedDeliveryDate); put("revisedDeliveryDate", revisedDeliveryDate); put("delayDays", delayDays); put("description", description); put("internalNotes", internalNotes); put("evidenceImages", JSONArray(evidenceImages)) }
    private fun IssueUpdate.toJson() = JSONObject().apply { put("id", id); put("issueId", issueId); put("date", date); put("note", note); put("images", JSONArray(images)) }
    private fun PaymentDeadline.toJson() = JSONObject().apply { put("id", id); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("amount", amount); put("dueDate", dueDate); put("paid", paid); put("paidDate", paidDate); put("method", method.name); put("notes", notes) }
    private fun AppSettings.toJson() = JSONObject().apply { put("dailyFixedCost", dailyFixedCost); put("plotterX1Cost", plotterX1Cost); put("plotterX2Cost", plotterX2Cost); put("protectionPaperCost", protectionPaperCost); put("themeKey", themeKey) }

    private fun JSONObject.toSnapshot(): SpectroSnapshot {
        if (optString("format") != "spectro-native-backup") return toLegacySnapshot()
        return SpectroSnapshot(
            clients = optJSONArray("clients").objects().map { it.toClient() },
            inventory = optJSONArray("inventory").objects().map { it.toInventory() },
            production = optJSONArray("production").objects().map { it.toProduction() },
            workOrders = optJSONArray("workOrders").objects().map { it.toWorkOrder() },
            settings = optJSONObject("settings")?.toSettings() ?: AppSettings(),
            clientIssues = optJSONArray("clientIssues").objects().map { it.toIssue() },
            issueUpdates = optJSONArray("issueUpdates").objects().map { it.toIssueUpdate() },
            payments = optJSONArray("payments").objects().map { it.toPayment() }
        )
    }

    private fun JSONObject.toLegacySnapshot(): SpectroSnapshot {
        require(has("notes") || has("workOrders") || has("clients") || has("inventory")) { "Formato backup non riconosciuto" }
        val legacyClients = optJSONArray("clients").objects().mapIndexed { i, o -> Client(o.optString("id").toLongOrNull() ?: (1_000_000L + i), o.optString("nome", o.optString("name")), o.optDouble("prezzoDefault", o.optDouble("defaultPrintPrice"))) }
        val legacyInventory = optJSONArray("inventory").objects().mapIndexed { i, o -> InventoryItem(o.optString("id").toLongOrNull() ?: (2_000_000L + i), if (o.optString("tipo") == "casa") OwnerType.HOUSE else enumOr(o.optString("ownerType"), OwnerType.CLIENT), o.optString("cliente", o.optString("ownerName")), o.optString("nomeTessuto", o.optString("fabricName")), o.optDouble("quantitaMt", o.optDouble("quantityMeters")), o.optDouble("prezzoMt", o.optDouble("purchasePricePerMeter")), o.optString("dataIngresso", o.optString("createdDate")), o.optJSONArray("immagini").strings().ifEmpty { o.optJSONArray("images").strings() }, o.optDouble("minimumQuantityMeters")) }
        val legacyProduction = optJSONArray("notes").objects().mapIndexed { i, o ->
            val negative = o.optString("tipo") == "negativo"
            ProductionRecord(id = o.optString("id").toLongOrNull() ?: (3_000_000L + i), type = if (negative) RecordType.NEGATIVE else RecordType.PRODUCTION, date = o.optString("data"), clientId = o.optString("clienteId").toLongOrNull(), clientName = o.optString("clienteNome"), orderRef = o.optString("ordineStampa"), designs = o.optJSONArray("disegni").strings(), printPricePerMeter = o.optDouble("prezzoStampaMt"), plotter = o.optString("plotter", "X1"), printedPaperMeters = o.optDouble("cartaStampataMt"), protectionPaperMeters = o.optDouble("cartaProtezioneMt"), paperWasteMeters = o.optDouble("sfridoCartaMt"), fabricMode = when (o.optString("modalitaTessuto")) { "magazzino" -> FabricMode.INVENTORY; "manuale" -> FabricMode.MANUAL; else -> FabricMode.NONE }, fabricId = o.optString("tessutoId").toLongOrNull(), fabricUsedMeters = o.optDouble("tessutoUsatoMt"), fabricWasteMeters = o.optDouble("sfridoTessutoMt"), fabricSalePricePerMeter = o.optDouble("prezzoVenditaTessuto"), manualFabricName = o.optString("tessutoManuale"), fabricDisplayName = o.optString("tessutoNomeVisualizzato", "Nessuno"), technicalNotes = if (negative) o.optString("descrizione") else o.optString("noteTecniche"), images = o.optJSONArray("immagini").strings(), refundCost = o.optDouble("costoRimborso"), fabricLossCost = o.optDouble("costoTessuto"), applyFixedCosts = o.optBoolean("applicaCostiFissi", true), paperPrintCost = o.optDouble("costoCartaStampa"), protectionPaperCost = o.optDouble("costoCartaProtezione"), fabricUsedCost = o.optDouble("costoTessutoUsato"), materialCostTotal = o.optDouble("totaleCostiMateria"), grossRevenue = o.optDouble("fatturatoLordo"), grossProfit = o.optDouble("utileLordoNota"))
        }
        val legacyWork = optJSONArray("workOrders").objects().mapIndexed { i, o -> WorkOrder(id = o.optString("id").toLongOrNull() ?: (4_000_000L + i), receivedDate = o.optString("data", o.optString("receivedDate")), client = o.optString("cliente", o.optString("client")), title = o.optString("titolo", o.optString("title")), description = o.optString("descrizione", o.optString("description")), images = o.optJSONArray("immagini").strings().ifEmpty { o.optJSONArray("images").strings() }, status = if (o.optString("stato") == "trasferito") WorkStatus.DELIVERED else enumOr(o.optString("status"), WorkStatus.RECEIVED), dueDate = o.optString("dataConsegna", o.optString("dueDate")), priority = if (o.optString("urgenza") == "alta") Priority.HIGH else enumOr(o.optString("priority"), Priority.NORMAL), blocker = when (o.optString("impedimento")) { "attesa_cliente" -> Blocker.WAITING_CLIENT; "attesa_materiale" -> Blocker.WAITING_MATERIAL; else -> enumOr(o.optString("blocker"), Blocker.NONE) }) }
        val oldS = optJSONObject("settings")
        return SpectroSnapshot(clients = legacyClients, inventory = legacyInventory, production = legacyProduction, workOrders = legacyWork, settings = AppSettings(oldS?.optDouble("dailyFixedCost", 760.0) ?: 760.0, oldS?.optDouble("costoPlotterX1", .8) ?: .8, oldS?.optDouble("costoPlotterX2", 1.0) ?: 1.0, oldS?.optDouble("costoProtezione", .1) ?: .1))
    }

    private fun JSONObject.toClient() = Client(optLong("id"), optString("name"), optDouble("defaultPrintPrice"))
    private fun JSONObject.toInventory() = InventoryItem(optLong("id"), enumOr(optString("ownerType"), OwnerType.CLIENT), optString("ownerName"), optString("fabricName"), optDouble("quantityMeters"), optDouble("purchasePricePerMeter"), optString("createdDate"), optJSONArray("images").strings(), optDouble("minimumQuantityMeters"))
    private fun JSONObject.toProduction() = ProductionRecord(id = optLong("id"), type = enumOr(optString("type"), RecordType.PRODUCTION), date = optString("date"), clientId = nullableLong("clientId"), clientName = optString("clientName"), orderRef = optString("orderRef"), designs = optJSONArray("designs").strings(), printPricePerMeter = optDouble("printPricePerMeter"), plotter = optString("plotter", "X1"), printedPaperMeters = optDouble("printedPaperMeters"), protectionPaperMeters = optDouble("protectionPaperMeters"), paperWasteMeters = optDouble("paperWasteMeters"), fabricMode = enumOr(optString("fabricMode"), FabricMode.NONE), fabricId = nullableLong("fabricId"), fabricUsedMeters = optDouble("fabricUsedMeters"), fabricWasteMeters = optDouble("fabricWasteMeters"), fabricSalePricePerMeter = optDouble("fabricSalePricePerMeter"), manualFabricName = optString("manualFabricName"), fabricDisplayName = optString("fabricDisplayName", "Nessuno"), technicalNotes = optString("technicalNotes"), images = optJSONArray("images").strings(), refundCost = optDouble("refundCost"), fabricLossCost = optDouble("fabricLossCost"), applyFixedCosts = optBoolean("applyFixedCosts", true), paperPrintCost = optDouble("paperPrintCost"), protectionPaperCost = optDouble("protectionPaperCost"), fabricUsedCost = optDouble("fabricUsedCost"), materialCostTotal = optDouble("materialCostTotal"), grossRevenue = optDouble("grossRevenue"), grossProfit = optDouble("grossProfit"))
    private fun JSONObject.toWorkOrder() = WorkOrder(optLong("id"), optString("receivedDate"), optString("client"), optString("title"), optString("description"), optJSONArray("images").strings(), enumOr(optString("status"), WorkStatus.RECEIVED), optString("dueDate"), enumOr(optString("priority"), Priority.NORMAL), enumOr(optString("blocker"), Blocker.NONE))
    private fun JSONObject.toIssue() = ClientIssue(optLong("id"), nullableLong("clientId"), optString("clientName"), optString("orderRef"), optString("title"), enumOr(optString("category"), IssueCategory.OTHER), enumOr(optString("responsibility"), IssueResponsibility.TO_VERIFY), enumOr(optString("severity"), IssueSeverity.MEDIUM), enumOr(optString("status"), IssueStatus.OPEN), optString("openedDate"), optString("expectedDeliveryDate"), optString("revisedDeliveryDate"), optInt("delayDays"), optString("description"), optString("internalNotes"), optJSONArray("evidenceImages").strings())
    private fun JSONObject.toIssueUpdate() = IssueUpdate(optLong("id"), optLong("issueId"), optString("date"), optString("note"), optJSONArray("images").strings())
    private fun JSONObject.toPayment() = PaymentDeadline(optLong("id"), nullableLong("clientId"), optString("clientName"), optString("orderRef"), optDouble("amount"), optString("dueDate"), optBoolean("paid"), optString("paidDate"), enumOr(optString("method"), PaymentMethod.UNSPECIFIED), optString("notes"))
    private fun JSONObject.toSettings() = AppSettings(optDouble("dailyFixedCost", 760.0), optDouble("plotterX1Cost", .8), optDouble("plotterX2Cost", 1.0), optDouble("protectionPaperCost", .1), optString("themeKey", "spectro"))

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
    private inline fun <reified T : Enum<T>> enumOr(value: String, default: T): T = enumValues<T>().firstOrNull { it.name == value } ?: default
}
