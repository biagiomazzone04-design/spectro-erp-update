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
        val file = writePortableBackup(context, snapshot, File(dir, "Spectro_Backup_${System.currentTimeMillis()}.spectrobackup"))

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/zip"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, "Salva o condividi backup Spectro"))
    }

    fun createRecoveryBackup(context: Context, snapshot: SpectroSnapshot, reason: String): File {
        val safeReason = reason.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val dir = File(context.filesDir, "recovery").apply { mkdirs() }
        return writePortableBackup(context, snapshot, File(dir, "Spectro_Recovery_${safeReason}_${System.currentTimeMillis()}.spectrobackup"))
    }

    /**
     * Cloud workspaces contain only the database snapshot. Attachments are
     * content-addressed Storage objects and are referenced by their remote
     * paths, so every synchronization does not duplicate all binary files.
     * Manual and recovery backups still use [writePortableBackup] and remain
     * fully self-contained.
     */
    fun createCloudTransferBackup(
        context: Context,
        snapshot: SpectroSnapshot,
        cloudAttachments: Map<String, String>
    ): File {
        val dir = File(context.cacheDir, "cloud-sync").apply { mkdirs() }
        val file = File(dir, "workspace_${System.currentTimeMillis()}.spectrobackup")
        val cloudSnapshot = remapAttachments(snapshot) { cloudAttachments[it] ?: it }
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry(DATA_ENTRY))
            zip.write(cloudSnapshot.toJson().toString(2).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return file
    }

    fun writePortableBackup(context: Context, snapshot: SpectroSnapshot, file: File): File {
        file.parentFile?.mkdirs()
        val replacements = linkedMapOf<String, String>()
        val allAttachments = attachmentPaths(snapshot)

        ZipOutputStream(FileOutputStream(file)).use { zip ->
            allAttachments.forEachIndexed { index, path ->
                val source = File(path)
                if (!source.isFile) return@forEachIndexed
                val safeName = source.name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val entryName = "attachments/${index}_${safeName}"
                replacements[path] = ATTACHMENT_PREFIX + entryName
                zip.putNextEntry(ZipEntry(entryName)); source.inputStream().use { it.copyTo(zip) }; zip.closeEntry()
            }
            val portable = remapAttachments(snapshot) { replacements[it] ?: it }
            zip.putNextEntry(ZipEntry(DATA_ENTRY)); zip.write(portable.toJson().toString(2).toByteArray(Charsets.UTF_8)); zip.closeEntry()
        }
        return file
    }

    fun attachmentPaths(snapshot: SpectroSnapshot): List<String> = buildList {
            snapshot.inventory.forEach { addAll(it.images) }
            snapshot.fabricRolls.forEach { addAll(it.images) }
            snapshot.production.forEach { addAll(it.images) }
            snapshot.workOrders.forEach { addAll(it.images) }
            snapshot.clientIssues.forEach { addAll(it.evidenceImages) }
            snapshot.issueUpdates.forEach { addAll(it.images) }
            snapshot.timelineEvents.forEach { addAll(it.images) }
            snapshot.cottonOrders.forEach { addAll(it.images) }
            snapshot.zenthaProducts.forEach { if (it.imagePath.isNotBlank()) add(it.imagePath) }
            snapshot.zenthaOrders.forEach { addAll(it.attachments) }
            snapshot.economicWorks.forEach { addAll(it.images) }
            snapshot.operationalTasks.forEach { addAll(it.images) }
        }.distinct()

    fun readBackup(context: Context, uri: android.net.Uri): SpectroSnapshot {
        val temp = File(context.cacheDir, "spectro_restore_${System.currentTimeMillis()}.tmp")
        context.contentResolver.openInputStream(uri).use { input -> requireNotNull(input) { "Impossibile leggere il backup." }; temp.outputStream().use { input.copyTo(it) } }
        return try { readBackupFile(context, temp) } finally { temp.delete() }
    }

    fun readBackupFile(context: Context, file: File): SpectroSnapshot {
        val head = file.inputStream().buffered().use { it.read() to it.read() }
        return if (head.first == 'P'.code && head.second == 'K'.code) readPortableZip(context, file)
        else JSONObject(file.readText()).toSnapshot()
    }

    /**
     * Builds the permission-filterable web mirror. Device file paths are never
     * copied to database rows. When available, only private Storage object paths
     * are added so the web client can request short-lived signed previews.
     */
    fun toCloudRecords(snapshot: SpectroSnapshot, cloudAttachments: Map<String, String> = emptyMap()): JSONArray = JSONArray().apply {
        fun add(module: String, entityType: String, legacyId: Long, raw: JSONObject) {
            val attachmentKeys = listOf("images", "evidenceImages", "attachments")
            var attachmentCount = 0
            val remotePaths = JSONArray()
            attachmentKeys.forEach { key ->
                val values = raw.optJSONArray(key)
                attachmentCount += values?.length() ?: 0
                if (values != null) for (index in 0 until values.length()) cloudAttachments[values.optString(index)]?.let { remotePaths.put(it) }
                raw.remove(key)
            }
            val imagePath = raw.optString("imagePath")
            if (imagePath.isNotBlank()) { attachmentCount += 1; cloudAttachments[imagePath]?.let { remotePaths.put(it) } }
            raw.remove("imagePath")
            if (attachmentCount > 0) raw.put("attachmentCount", attachmentCount)
            if (remotePaths.length() > 0) raw.put("attachmentPaths", remotePaths)
            put(JSONObject()
                .put("module", module)
                .put("entity_type", entityType)
                .put("legacy_id", legacyId)
                .put("payload", raw))
        }

        snapshot.clients.forEach { add("clients", "client", it.id, it.toJson()) }
        snapshot.inventory.forEach { add("inventory", "inventory_item", it.id, it.toJson()) }
        snapshot.fabricRolls.forEach { add("inventory", "fabric_roll", it.id, it.toJson()) }
        snapshot.stockMovements.forEach { add("inventory", "stock_movement", it.id, it.toJson()) }
        snapshot.rollUsages.forEach { add("inventory", "roll_usage", it.id, it.toJson()) }
        snapshot.production.forEach { add("production", "production_record", it.id, it.toJson()) }
        snapshot.workOrders.forEach { add("orders", "work_order", it.id, it.toJson()) }
        snapshot.timelineEvents.forEach { add("issues", "order_timeline_event", it.id, it.toJson()) }
        snapshot.clientIssues.forEach { add("issues", "client_issue", it.id, it.toJson()) }
        snapshot.issueUpdates.forEach { add("issues", "issue_update", it.id, it.toJson()) }
        snapshot.payments.forEach { add("finance", "payment_deadline", it.id, it.toJson()) }
        snapshot.economicWorks.forEach { add("finance", "economic_work", it.id, it.toJson()) }
        snapshot.cottonOrders.forEach { add("cotton", "cotton_order", it.id, it.toJson()) }
        snapshot.zenthaProducts.forEach { add("zentha", "zentha_product", it.id, it.toJson()) }
        snapshot.zenthaOrders.forEach { add("zentha", "zentha_order", it.id, it.toJson()) }
        snapshot.zenthaItems.forEach { add("zentha", "zentha_order_item", it.id, it.toJson()) }
        snapshot.zenthaExtraCosts.forEach { add("zentha", "zentha_extra_cost", it.id, it.toJson()) }
        snapshot.zenthaPayments.forEach { add("zentha", "zentha_payment", it.id, it.toJson()) }
        snapshot.operationalTasks.forEach { add("tasks", "operational_task", it.id, it.toJson()) }
        add("settings", "app_settings", 1, snapshot.settings.toJson())
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
        return remapAttachments(JSONObject(requireNotNull(jsonText) { "Backup Spectro non valido: manca data.json." }).toSnapshot()) { restoredPaths[it] ?: it }
    }

    fun remapAttachments(snapshot: SpectroSnapshot, transform: (String) -> String) = snapshot.copy(
        inventory = snapshot.inventory.map { it.copy(images = it.images.map(transform)) },
        fabricRolls = snapshot.fabricRolls.map { it.copy(images = it.images.map(transform)) },
        production = snapshot.production.map { it.copy(images = it.images.map(transform)) },
        workOrders = snapshot.workOrders.map { it.copy(images = it.images.map(transform)) },
        clientIssues = snapshot.clientIssues.map { it.copy(evidenceImages = it.evidenceImages.map(transform)) },
        issueUpdates = snapshot.issueUpdates.map { it.copy(images = it.images.map(transform)) },
        timelineEvents = snapshot.timelineEvents.map { it.copy(images = it.images.map(transform)) },
        cottonOrders = snapshot.cottonOrders.map { it.copy(images = it.images.map(transform)) },
        zenthaProducts = snapshot.zenthaProducts.map { it.copy(imagePath = it.imagePath.takeIf { path -> path.isNotBlank() }?.let(transform).orEmpty()) },
        zenthaOrders = snapshot.zenthaOrders.map { it.copy(attachments = it.attachments.map(transform)) },
        economicWorks = snapshot.economicWorks.map { it.copy(images = it.images.map(transform)) },
        operationalTasks = snapshot.operationalTasks.map { it.copy(images = it.images.map(transform)) }
    )

    private fun SpectroSnapshot.toJson() = JSONObject().apply {
        put("format", "spectro-native-backup"); put("version", 9)
        put("clients", clients.jsonArray { it.toJson() })
        put("inventory", inventory.jsonArray { it.toJson() })
        put("production", production.jsonArray { it.toJson() })
        put("workOrders", workOrders.jsonArray { it.toJson() })
        put("clientIssues", clientIssues.jsonArray { it.toJson() })
        put("issueUpdates", issueUpdates.jsonArray { it.toJson() })
        put("payments", payments.jsonArray { it.toJson() })
        put("fabricRolls", fabricRolls.jsonArray { it.toJson() })
        put("stockMovements", stockMovements.jsonArray { it.toJson() })
        put("rollUsages", rollUsages.jsonArray { it.toJson() })
        put("timelineEvents", timelineEvents.jsonArray { it.toJson() })
        put("cottonOrders", cottonOrders.jsonArray { it.toJson() })
        put("zenthaProducts", zenthaProducts.jsonArray { it.toJson() })
        put("zenthaOrders", zenthaOrders.jsonArray { it.toJson() })
        put("zenthaItems", zenthaItems.jsonArray { it.toJson() })
        put("zenthaExtraCosts", zenthaExtraCosts.jsonArray { it.toJson() })
        put("zenthaPayments", zenthaPayments.jsonArray { it.toJson() })
        put("economicWorks", economicWorks.jsonArray { it.toJson() })
        put("operationalTasks", operationalTasks.jsonArray { it.toJson() })
        put("settings", settings.toJson())
    }

    private fun <T> List<T>.jsonArray(block: (T) -> JSONObject) = JSONArray().apply { this@jsonArray.forEach { put(block(it)) } }
    private fun Client.toJson() = JSONObject().apply { put("id", id); put("name", name); put("defaultPrintPrice", defaultPrintPrice) }
    private fun InventoryItem.toJson() = JSONObject().apply { put("id", id); put("ownerType", ownerType.name); put("ownerName", ownerName); put("fabricName", fabricName); put("quantityMeters", quantityMeters); put("purchasePricePerMeter", purchasePricePerMeter); put("createdDate", createdDate); put("images", JSONArray(images)); put("minimumQuantityMeters", minimumQuantityMeters); put("supplierName", supplierName); put("composition", composition); put("weightGsm", weightGsm); put("variant", variant); put("lotCode", lotCode); put("processingCostPerMeter", processingCostPerMeter); put("defaultSalePricePerMeter", defaultSalePricePerMeter); put("archived", archived) }
    private fun FabricRoll.toJson() = JSONObject().apply { put("id", id); put("inventoryId", inventoryId); put("rollCode", rollCode); put("initialMeters", initialMeters); put("availableMeters", availableMeters); put("createdDate", createdDate); put("note", note); put("status", status.name); put("images", JSONArray(images)) }
    private fun StockMovement.toJson() = JSONObject().apply { put("id", id); put("inventoryId", inventoryId); put("rollId", rollId); put("type", type.name); put("dateTime", dateTime); put("meters", meters); put("orderKind", orderKind?.name ?: JSONObject.NULL); put("orderId", orderId ?: JSONObject.NULL); put("orderRef", orderRef); put("note", note) }
    private fun RollUsage.toJson() = JSONObject().apply { put("id", id); put("orderKind", orderKind.name); put("orderId", orderId); put("orderRef", orderRef); put("inventoryId", inventoryId); put("rollId", rollId); put("meters", meters); put("createdAt", createdAt) }
    private fun ProductionRecord.toJson() = JSONObject().apply {
        put("id", id); put("type", type.name); put("date", date); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("designs", JSONArray(designs)); put("printPricePerMeter", printPricePerMeter); put("plotter", plotter); put("printedPaperMeters", printedPaperMeters); put("protectionPaperMeters", protectionPaperMeters); put("paperWasteMeters", paperWasteMeters); put("fabricMode", fabricMode.name); put("fabricId", fabricId ?: JSONObject.NULL); put("fabricUsedMeters", fabricUsedMeters); put("fabricWasteMeters", fabricWasteMeters); put("fabricSalePricePerMeter", fabricSalePricePerMeter); put("manualFabricName", manualFabricName); put("fabricDisplayName", fabricDisplayName); put("technicalNotes", technicalNotes); put("images", JSONArray(images)); put("refundCost", refundCost); put("fabricLossCost", fabricLossCost); put("applyFixedCosts", applyFixedCosts); put("paperPrintCost", paperPrintCost); put("protectionPaperCost", protectionPaperCost); put("fabricUsedCost", fabricUsedCost); put("materialCostTotal", materialCostTotal); put("grossRevenue", grossRevenue); put("grossProfit", grossProfit); put("workOrderId", workOrderId ?: JSONObject.NULL); put("zenthaOrderId", zenthaOrderId ?: JSONObject.NULL)
    }
    private fun WorkOrder.toJson() = JSONObject().apply { put("id", id); put("receivedDate", receivedDate); put("client", client); put("title", title); put("description", description); put("images", JSONArray(images)); put("status", status.name); put("dueDate", dueDate); put("priority", priority.name); put("blocker", blocker.name); put("clientId", clientId ?: JSONObject.NULL); put("orderCode", orderCode); put("fabricInventoryId", fabricInventoryId ?: JSONObject.NULL); put("orderedMeters", orderedMeters); put("printPricePerMeter", printPricePerMeter); put("designCode", designCode); put("productionRecordId", productionRecordId ?: JSONObject.NULL); put("effectiveDeliveryDate", effectiveDeliveryDate); put("cancelReason", cancelReason); put("materialReceivedDate", materialReceivedDate); put("materialReceiptNotes", materialReceiptNotes) }
    private fun OrderTimelineEvent.toJson() = JSONObject().apply { put("id", id); put("workOrderId", workOrderId); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("date", date); put("time", time); put("category", category.name); put("responsibility", responsibility.name); put("severity", severity.name); put("status", status.name); put("title", title); put("description", description); put("oldDueDate", oldDueDate); put("newDueDate", newDueDate); put("images", JSONArray(images)); put("resolutionNote", resolutionNote); put("resolvedDate", resolvedDate) }
    private fun ClientIssue.toJson() = JSONObject().apply { put("id", id); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("title", title); put("category", category.name); put("responsibility", responsibility.name); put("severity", severity.name); put("status", status.name); put("openedDate", openedDate); put("expectedDeliveryDate", expectedDeliveryDate); put("revisedDeliveryDate", revisedDeliveryDate); put("delayDays", delayDays); put("description", description); put("internalNotes", internalNotes); put("evidenceImages", JSONArray(evidenceImages)) }
    private fun IssueUpdate.toJson() = JSONObject().apply { put("id", id); put("issueId", issueId); put("date", date); put("note", note); put("images", JSONArray(images)) }
    private fun PaymentDeadline.toJson() = JSONObject().apply { put("id", id); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderRef", orderRef); put("amount", amount); put("dueDate", dueDate); put("paid", paid); put("paidDate", paidDate); put("method", method.name); put("notes", notes) }
    private fun CottonOrder.toJson() = JSONObject().apply { put("id", id); put("orderCode", orderCode); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderDate", orderDate); put("dueDate", dueDate); put("completedDate", completedDate); put("fabricName", fabricName); put("printType", printType.name); put("orderedMeters", orderedMeters); put("actualProducedMeters", actualProducedMeters); put("deliveredMeters", deliveredMeters); put("defectiveMeters", defectiveMeters); put("sampleMeters", sampleMeters); put("leftoverMeters", leftoverMeters); put("productionCostPerMeter", productionCostPerMeter); put("printPricePerMeter", printPricePerMeter); put("fabricOwnerType", fabricOwnerType.name); put("inventoryId", inventoryId ?: JSONObject.NULL); put("fabricCostPerMeter", fabricCostPerMeter); put("notes", notes); put("status", status.name); put("title", title); put("images", JSONArray(images)) }
    private fun ZenthaProduct.toJson() = JSONObject().apply {
        put("id", id); put("name", name); put("sku", sku); put("category", category); put("standardCost", standardCost); put("standardPrice", standardPrice)
        put("description", description); put("imagePath", imagePath); put("active", active); put("fabricCost", fabricCost); put("ribCost", ribCost)
        put("liningCost", liningCost); put("printCost", printCost); put("accessoryCost", accessoryCost); put("cuttingCost", cuttingCost); put("sewingCost", sewingCost)
        put("labelCost", labelCost); put("packagingCost", packagingCost); put("otherCost", otherCost); put("priceListName", priceListName)
        put("markupPercent", markupPercent); put("sourceReference", sourceReference)
    }
    private fun ZenthaOrder.toJson() = JSONObject().apply { put("id", id); put("orderCode", orderCode); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("orderDate", orderDate); put("dueDate", dueDate); put("status", status.name); put("notes", notes); put("attachments", JSONArray(attachments)); put("splitPercentA", splitPercentA); put("splitPercentB", splitPercentB); put("chromiaRatePerMeter", chromiaRatePerMeter); put("sport", sport); put("audience", audience); put("priority", priority.name); put("blockerReason", blockerReason) }
    private fun ZenthaOrderItem.toJson() = JSONObject().apply {
        put("id", id); put("orderId", orderId); put("productId", productId ?: JSONObject.NULL); put("productName", productName); put("quantity", quantity)
        put("unitPrice", unitPrice); put("unitCost", unitCost); put("skuSnapshot", skuSnapshot); put("productDescriptionSnapshot", productDescriptionSnapshot)
        put("variant", variant); put("color", color); put("sizeBreakdown", sizeBreakdown); put("personalization", personalization); put("productionNotes", productionNotes)
    }
    private fun ZenthaExtraCost.toJson() = JSONObject().apply { put("id", id); put("orderId", orderId); put("label", label); put("amount", amount) }
    private fun ZenthaPayment.toJson() = JSONObject().apply { put("id", id); put("orderId", orderId); put("date", date); put("amount", amount); put("method", method.name); put("notes", notes) }
    private fun EconomicWork.toJson() = JSONObject().apply { put("id", id); put("date", date); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("title", title); put("quantity", quantity); put("unitPrice", unitPrice); put("manualGrossRevenue", manualGrossRevenue); put("materials", JSONArray().apply { materials.forEach { m -> put(JSONObject().apply { put("name", m.name); put("quantity", m.quantity); put("unitCost", m.unitCost) }) } }); put("otherCosts", otherCosts); put("notes", notes); put("images", JSONArray(images)); put("status", status.name) }
    private fun OperationalTask.toJson() = JSONObject().apply { put("id", id); put("createdDate", createdDate); put("dueDate", dueDate); put("clientId", clientId ?: JSONObject.NULL); put("clientName", clientName); put("workOrderId", workOrderId ?: JSONObject.NULL); put("title", title); put("type", type.name); put("description", description); put("priority", priority.name); put("status", status.name); put("images", JSONArray(images)) }
    private fun AppSettings.toJson() = JSONObject().apply { put("dailyFixedCost", dailyFixedCost); put("plotterX1Cost", plotterX1Cost); put("plotterX2Cost", plotterX2Cost); put("protectionPaperCost", protectionPaperCost); put("themeKey", themeKey); put("zenthaSplitA", zenthaSplitA); put("zenthaSplitB", zenthaSplitB); put("chromiaRatePerMeter", chromiaRatePerMeter) }

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
            payments = optJSONArray("payments").objects().map { it.toPayment() },
            fabricRolls = optJSONArray("fabricRolls").objects().map { it.toFabricRoll() },
            stockMovements = optJSONArray("stockMovements").objects().map { it.toStockMovement() },
            rollUsages = optJSONArray("rollUsages").objects().map { it.toRollUsage() },
            timelineEvents = optJSONArray("timelineEvents").objects().map { it.toTimelineEvent() },
            cottonOrders = optJSONArray("cottonOrders").objects().map { it.toCottonOrder() },
            zenthaProducts = optJSONArray("zenthaProducts").objects().map { it.toZenthaProduct() },
            zenthaOrders = optJSONArray("zenthaOrders").objects().map { it.toZenthaOrder() },
            zenthaItems = optJSONArray("zenthaItems").objects().map { it.toZenthaItem() },
            zenthaExtraCosts = optJSONArray("zenthaExtraCosts").objects().map { it.toZenthaExtraCost() },
            zenthaPayments = optJSONArray("zenthaPayments").objects().map { it.toZenthaPayment() },
            economicWorks = optJSONArray("economicWorks").objects().map { it.toEconomicWork() },
            operationalTasks = optJSONArray("operationalTasks").objects().map { it.toOperationalTask() }
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
        val legacyWork = optJSONArray("workOrders").objects().mapIndexed { i, o -> WorkOrder(id = o.optString("id").toLongOrNull() ?: (4_000_000L + i), receivedDate = o.optString("data", o.optString("receivedDate")), client = o.optString("cliente", o.optString("client")), title = o.optString("titolo", o.optString("title")), description = o.optString("descrizione", o.optString("description")), images = o.optJSONArray("immagini").strings().ifEmpty { o.optJSONArray("images").strings() }, status = if (o.optString("stato") == "trasferito") WorkStatus.DELIVERED else enumOr(o.optString("status"), WorkStatus.IN_PROCESS), dueDate = o.optString("dataConsegna", o.optString("dueDate")), priority = if (o.optString("urgenza") == "alta") Priority.HIGH else enumOr(o.optString("priority"), Priority.NORMAL), blocker = when (o.optString("impedimento")) { "attesa_cliente" -> Blocker.WAITING_CLIENT; "attesa_materiale" -> Blocker.WAITING_MATERIAL; else -> enumOr(o.optString("blocker"), Blocker.NONE) }) }
        val oldS = optJSONObject("settings")
        return SpectroSnapshot(clients = legacyClients, inventory = legacyInventory, production = legacyProduction, workOrders = legacyWork, settings = AppSettings(oldS?.optDouble("dailyFixedCost", 760.0) ?: 760.0, oldS?.optDouble("costoPlotterX1", .8) ?: .8, oldS?.optDouble("costoPlotterX2", 1.0) ?: 1.0, oldS?.optDouble("costoProtezione", .1) ?: .1))
    }

    private fun JSONObject.toClient() = Client(optLong("id"), optString("name"), optDouble("defaultPrintPrice"))
    private fun JSONObject.toInventory() = InventoryItem(optLong("id"), enumOr(optString("ownerType"), OwnerType.CLIENT), optString("ownerName"), optString("fabricName"), optDouble("quantityMeters"), optDouble("purchasePricePerMeter"), optString("createdDate"), optJSONArray("images").strings(), optDouble("minimumQuantityMeters"), optString("supplierName"), optString("composition"), optString("weightGsm"), optString("variant"), optString("lotCode"), optDouble("processingCostPerMeter"), optDouble("defaultSalePricePerMeter"), optBoolean("archived", false))
    private fun JSONObject.toFabricRoll() = FabricRoll(optLong("id"), optLong("inventoryId"), optString("rollCode"), optDouble("initialMeters"), optDouble("availableMeters"), optString("createdDate"), optString("note"), enumOr(optString("status"), RollStatus.OPEN), optJSONArray("images").strings())
    private fun JSONObject.toStockMovement() = StockMovement(optLong("id"), optLong("inventoryId"), optLong("rollId"), enumOr(optString("type"), StockMovementType.ADJUSTMENT), optString("dateTime"), optDouble("meters"), nullableString("orderKind")?.let { enumOr(it, OrderKind.WORK) }, nullableLong("orderId"), optString("orderRef"), optString("note"))
    private fun JSONObject.toRollUsage() = RollUsage(optLong("id"), enumOr(optString("orderKind"), OrderKind.WORK), optLong("orderId"), optString("orderRef"), optLong("inventoryId"), optLong("rollId"), optDouble("meters"), optString("createdAt"))
    private fun JSONObject.toProduction() = ProductionRecord(id = optLong("id"), type = enumOr(optString("type"), RecordType.PRODUCTION), date = optString("date"), clientId = nullableLong("clientId"), clientName = optString("clientName"), orderRef = optString("orderRef"), designs = optJSONArray("designs").strings(), printPricePerMeter = optDouble("printPricePerMeter"), plotter = optString("plotter", "X1"), printedPaperMeters = optDouble("printedPaperMeters"), protectionPaperMeters = optDouble("protectionPaperMeters"), paperWasteMeters = optDouble("paperWasteMeters"), fabricMode = enumOr(optString("fabricMode"), FabricMode.NONE), fabricId = nullableLong("fabricId"), fabricUsedMeters = optDouble("fabricUsedMeters"), fabricWasteMeters = optDouble("fabricWasteMeters"), fabricSalePricePerMeter = optDouble("fabricSalePricePerMeter"), manualFabricName = optString("manualFabricName"), fabricDisplayName = optString("fabricDisplayName", "Nessuno"), technicalNotes = optString("technicalNotes"), images = optJSONArray("images").strings(), refundCost = optDouble("refundCost"), fabricLossCost = optDouble("fabricLossCost"), applyFixedCosts = optBoolean("applyFixedCosts", true), paperPrintCost = optDouble("paperPrintCost"), protectionPaperCost = optDouble("protectionPaperCost"), fabricUsedCost = optDouble("fabricUsedCost"), materialCostTotal = optDouble("materialCostTotal"), grossRevenue = optDouble("grossRevenue"), grossProfit = optDouble("grossProfit"), workOrderId = nullableLong("workOrderId"), zenthaOrderId = nullableLong("zenthaOrderId"))
    private fun JSONObject.toWorkOrder() = WorkOrder(optLong("id"), optString("receivedDate"), optString("client"), optString("title"), optString("description"), optJSONArray("images").strings(), enumOr(optString("status"), WorkStatus.IN_PROCESS), optString("dueDate"), enumOr(optString("priority"), Priority.NORMAL), enumOr(optString("blocker"), Blocker.NONE), nullableLong("clientId"), optString("orderCode"), nullableLong("fabricInventoryId"), optDouble("orderedMeters"), optDouble("printPricePerMeter"), optString("designCode"), nullableLong("productionRecordId"), optString("effectiveDeliveryDate"), optString("cancelReason"), optString("materialReceivedDate"), optString("materialReceiptNotes"))
    private fun JSONObject.toTimelineEvent() = OrderTimelineEvent(optLong("id"), optLong("workOrderId"), nullableLong("clientId"), optString("clientName"), optString("orderRef"), optString("date"), optString("time"), enumOr(optString("category"), TimelineCategory.NOTE), enumOr(optString("responsibility"), TimelineResponsibility.NONE), enumOr(optString("severity"), TimelineSeverity.INFO), enumOr(optString("status"), TimelineStatus.OPEN), optString("title"), optString("description"), optString("oldDueDate"), optString("newDueDate"), optJSONArray("images").strings(), optString("resolutionNote"), optString("resolvedDate"))
    private fun JSONObject.toIssue() = ClientIssue(optLong("id"), nullableLong("clientId"), optString("clientName"), optString("orderRef"), optString("title"), enumOr(optString("category"), IssueCategory.OTHER), enumOr(optString("responsibility"), IssueResponsibility.TO_VERIFY), enumOr(optString("severity"), IssueSeverity.MEDIUM), enumOr(optString("status"), IssueStatus.OPEN), optString("openedDate"), optString("expectedDeliveryDate"), optString("revisedDeliveryDate"), optInt("delayDays"), optString("description"), optString("internalNotes"), optJSONArray("evidenceImages").strings())
    private fun JSONObject.toIssueUpdate() = IssueUpdate(optLong("id"), optLong("issueId"), optString("date"), optString("note"), optJSONArray("images").strings())
    private fun JSONObject.toPayment() = PaymentDeadline(optLong("id"), nullableLong("clientId"), optString("clientName"), optString("orderRef"), optDouble("amount"), optString("dueDate"), optBoolean("paid"), optString("paidDate"), enumOr(optString("method"), PaymentMethod.UNSPECIFIED), optString("notes"))
    private fun JSONObject.toCottonOrder() = CottonOrder(optLong("id"), optString("orderCode"), nullableLong("clientId"), optString("clientName"), optString("orderDate"), optString("dueDate"), optString("fabricName"), enumOr(optString("printType"), CottonPrintType.REACTIVE), optDouble("orderedMeters"), optDouble("actualProducedMeters"), optDouble("deliveredMeters"), optDouble("defectiveMeters"), optDouble("sampleMeters"), optDouble("leftoverMeters"), optDouble("productionCostPerMeter"), optDouble("printPricePerMeter"), enumOr(optString("fabricOwnerType"), OwnerType.CLIENT), nullableLong("inventoryId"), optDouble("fabricCostPerMeter"), optString("notes"), enumOr(optString("status"), WorkStatus.IN_PROCESS), optString("title"), optJSONArray("images").strings(), optString("completedDate"))
    private fun JSONObject.toZenthaProduct() = ZenthaProduct(
        id = optLong("id"), name = optString("name"), sku = optString("sku"), category = optString("category"),
        standardCost = optDouble("standardCost"), standardPrice = optDouble("standardPrice"), description = optString("description"),
        imagePath = optString("imagePath"), active = optBoolean("active", true), fabricCost = optDouble("fabricCost"),
        ribCost = optDouble("ribCost"), liningCost = optDouble("liningCost"), printCost = optDouble("printCost"),
        accessoryCost = optDouble("accessoryCost"), cuttingCost = optDouble("cuttingCost"), sewingCost = optDouble("sewingCost"),
        labelCost = optDouble("labelCost"), packagingCost = optDouble("packagingCost"), otherCost = optDouble("otherCost"),
        priceListName = optString("priceListName", "Listino standard"), markupPercent = optDouble("markupPercent"), sourceReference = optString("sourceReference")
    )
    private fun JSONObject.toZenthaOrder() = ZenthaOrder(optLong("id"), optString("orderCode"), nullableLong("clientId"), optString("clientName"), optString("orderDate"), optString("dueDate"), enumOr(optString("status"), ZenthaOrderStatus.CONFIRMED), optString("notes"), optJSONArray("attachments").strings(), optDouble("splitPercentA", 50.0), optDouble("splitPercentB", 50.0), optDouble("chromiaRatePerMeter", 2.5), optString("sport", "MULTISPORT"), optString("audience", "MISTO"), enumOr(optString("priority"), Priority.NORMAL), optString("blockerReason"))
    private fun JSONObject.toZenthaItem() = ZenthaOrderItem(
        id = optLong("id"), orderId = optLong("orderId"), productId = nullableLong("productId"), productName = optString("productName"),
        quantity = optInt("quantity"), unitPrice = optDouble("unitPrice"), unitCost = optDouble("unitCost"), skuSnapshot = optString("skuSnapshot"),
        productDescriptionSnapshot = optString("productDescriptionSnapshot"), variant = optString("variant"), color = optString("color"),
        sizeBreakdown = optString("sizeBreakdown"), personalization = optString("personalization"), productionNotes = optString("productionNotes")
    )
    private fun JSONObject.toZenthaExtraCost() = ZenthaExtraCost(optLong("id"), optLong("orderId"), optString("label"), optDouble("amount"))
    private fun JSONObject.toZenthaPayment() = ZenthaPayment(optLong("id"), optLong("orderId"), optString("date"), optDouble("amount"), enumOr(optString("method"), PaymentMethod.UNSPECIFIED), optString("notes"))
    private fun JSONObject.toEconomicWork() = EconomicWork(optLong("id"), optString("date"), nullableLong("clientId"), optString("clientName"), optString("title"), optDouble("quantity"), optDouble("unitPrice"), optDouble("manualGrossRevenue"), optJSONArray("materials").objects().map { m -> EconomicMaterialUsage(m.optString("name"), m.optDouble("quantity"), m.optDouble("unitCost")) }, optDouble("otherCosts"), optString("notes"), optJSONArray("images").strings(), enumOr(optString("status"), EconomicWorkStatus.OPEN))
    private fun JSONObject.toOperationalTask() = OperationalTask(optLong("id"), optString("createdDate"), optString("dueDate"), nullableLong("clientId"), optString("clientName"), nullableLong("workOrderId"), optString("title"), enumOr(optString("type"), OperationalTaskType.OTHER), optString("description"), enumOr(optString("priority"), Priority.NORMAL), enumOr(optString("status"), OperationalTaskStatus.TODO), optJSONArray("images").strings())
    private fun JSONObject.toSettings() = AppSettings(optDouble("dailyFixedCost", 760.0), optDouble("plotterX1Cost", .8), optDouble("plotterX2Cost", 1.0), optDouble("protectionPaperCost", .1), optString("themeKey", "spectro"), optDouble("zenthaSplitA", 50.0), optDouble("zenthaSplitB", 50.0), optDouble("chromiaRatePerMeter", 2.5))

    private fun JSONArray?.objects(): List<JSONObject> = if (this == null) emptyList() else List(length()) { optJSONObject(it) }.filterNotNull()
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
    private fun JSONObject.nullableString(key: String): String? = if (!has(key) || isNull(key)) null else optString(key)
    private inline fun <reified T : Enum<T>> enumOr(value: String, default: T): T = enumValues<T>().firstOrNull { it.name == value } ?: default
}
