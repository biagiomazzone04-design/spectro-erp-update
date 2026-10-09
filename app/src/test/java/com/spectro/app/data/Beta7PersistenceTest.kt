package com.spectro.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.spectro.app.util.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class Beta7PersistenceTest {
    private lateinit var context: Context
    private lateinit var db: SpectroDatabase
    @Before fun setup() { context = RuntimeEnvironment.getApplication(); context.deleteDatabase("spectro_native.db"); db = SpectroDatabase(context) }
    @After fun close() { db.close() }
    private fun rawRows(sql: SQLiteDatabase, table: String, columns: List<String>? = null): List<List<String?>> = sql.query(table, columns?.toTypedArray(), null, null, null, null, "id").use { c -> buildList { while(c.moveToNext()) add((0 until c.columnCount).map { if(c.isNull(it)) null else c.getString(it) }) } }

    @Test fun schema5UpgradePreservesEveryOldColumnAndIsRepeatable() {
        val file = context.getDatabasePath("spectro_native.db"); file.parentFile!!.mkdirs()
        val sql = SQLiteDatabase.openOrCreateDatabase(file, null)
        javaClass.getResourceAsStream("/beta6-schema5.sql")!!.bufferedReader().readText().split(';').filter { it.isNotBlank() }.forEach { sql.execSQL(it) }
        val tables = sql.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%' AND name != 'android_metadata'", null).use { c -> buildList { while(c.moveToNext()) add(c.getString(0)) } }
        val columns = tables.associateWith { table -> sql.rawQuery("PRAGMA table_info($table)", null).use { c -> buildList { while(c.moveToNext()) add(c.getString(1)) } } }
        // Seed every original table, including non-Zentha modules, notes and attachment references.
        tables.forEach { table ->
            val values = ContentValues()
            sql.rawQuery("PRAGMA table_info($table)", null).use { c -> while(c.moveToNext()) {
                val name = c.getString(1); val type = c.getString(2)
                when { name == "id" -> values.put(name, if(table == "settings") 1 else 41)
                    name.endsWith("_json") -> values.put(name, if(name == "materials_json") "[]" else "[\"/existing/photo.jpg\"]")
                    type == "TEXT" -> values.put(name, "preserved $table $name")
                    type == "REAL" -> values.put(name, 12.75)
                    else -> values.put(name, 41)
                }
            } }
            sql.insertOrThrow(table, null, values)
        }
        val before = tables.associateWith { rawRows(sql, it, columns.getValue(it)) }
        sql.version = 5; sql.close()
        val upgraded = db.writableDatabase
        assertEquals(6, upgraded.version)
        tables.forEach { assertEquals("Old columns changed in $it", before[it], rawRows(upgraded, it, columns.getValue(it))) }
        assertEquals(4, ZenthaStore.priceLists(upgraded).size)
        assertTrue(ZenthaStore.variants(upgraded).isEmpty()); assertTrue(ZenthaStore.movements(upgraded).isEmpty())
        assertFalse(db.getZenthaOrders().single().stockPosted)
        assertEquals(12.75, db.getZenthaProducts().single().realCost, .001)
        val state = ZenthaStore.snapshot(upgraded)
        Beta7Migration.apply(upgraded)
        assertEquals(state, ZenthaStore.snapshot(upgraded))
        tables.forEach { assertEquals(before[it], rawRows(upgraded, it, columns.getValue(it))) }
    }

    private fun fixture(): SpectroSnapshot {
        val attachment = File(context.filesDir, "mockup.pdf").apply { writeBytes(byteArrayOf(37,80,68,70,0,1,2,127)) }.absolutePath
        return SpectroSnapshot(
            clients = listOf(Client(1, "Club", 2.0)),
            production = listOf(ProductionRecord(id = 30, date = "2026-10-01", clientId = 1, clientName = "Club", images = listOf(attachment), designs = listOf("Fiori"), grossRevenue = 55.5)),
            workOrders = listOf(WorkOrder(id = 31, receivedDate = "2026-10-01", client = "Club", title = "Legacy Spectro", orderCode = "SP-31", images = listOf(attachment))),
            zenthaProducts = listOf(ZenthaProduct(1, "Kit", "KIT", "Sport", 5.0, 20.0, imagePath = attachment, printingCost = 2.0)),
            zenthaOrders = listOf(ZenthaOrder(id = 1, orderCode = "ZEN-1", clientId = 1, clientName = "Club", orderDate = "2026-10-01", contact = "Allenatore", attachments = listOf(attachment), discount = 1.0, shippingRevenue = 2.0)),
            zenthaItems = listOf(ZenthaOrderItem(1, 1, 1, "Kit", 3, 18.0, 7.0, 1, "M Blu Uomo Standard", "M", "KIT-M")),
            zenthaExtraCosts = listOf(ZenthaExtraCost(1, 1, "Sponsor", 4.0)),
            zenthaPayments = listOf(ZenthaPayment(1, 1, "2026-10-01", 10.0, notes = "Acconto")),
            settings = AppSettings(dailyFixedCost = 123.45),
            zentha = ZenthaData(
                variants = listOf(ZenthaProductVariant(1, 1, "KIT-M", "M", "Blu")),
                balances = listOf(ZenthaStockBalance(1, 1, 10, 2, "A1")),
                movements = listOf(ZenthaStockMovement(1, 1, ZenthaMovementType.LOAD, 10, "2026-10-01T09:00", note = "Carico")),
                priceLists = listOf(ZenthaPriceList(1, "Standard")), priceRules = listOf(ZenthaPriceListRule(1, 1, 1, minQty = 3, unitPrice = 18.0)),
                clientProfiles = listOf(ZenthaClientProfile(1, 1, 1, "Società")),
                roster = listOf(ZenthaRosterEntry(1, 1, "Rossi", "Mario", "7", "M", "S", "Capitano", 1)),
                customizations = listOf(ZenthaCustomization(1, 1, 1, "Logo", "Sponsor", "Petto", true, listOf(attachment))),
                events = listOf(ZenthaOrderEvent(1, 1, "2026-10-01T10:00", toStatus = "RECEIVED", note = "Ricevuto"))
            )
        )
    }
    @Test fun backup8JsonAndDatabaseRoundTripEveryNewEntityWithoutChangingIds() {
        val original = fixture()
        assertEquals(original, BackupManager.decodeSnapshot(BackupManager.encodeSnapshot(original)))
        db.replaceAll(original)
        val repo = SpectroRepository(context)
        assertEquals(original, repo.snapshot())
        assertThrows(Exception::class.java) { db.replaceAll(original.copy(zenthaItems = original.zenthaItems + original.zenthaItems)) }
        assertEquals(original, repo.snapshot())
    }
    @Test fun backup7WithoutBeta7FieldsImportsWithCompatibleDefaults() {
        val original = fixture()
        val json = JSONObject(BackupManager.encodeSnapshot(original)); json.put("version", 7); json.remove("zentha")
        listOf("line", "printingCost", "sewingCost", "accessoriesCost", "packagingCost", "otherCost").forEach { json.getJSONArray("zenthaProducts").getJSONObject(0).remove(it) }
        listOf("contact", "discount", "shippingRevenue", "otherRevenue", "stockPosted").forEach { json.getJSONArray("zenthaOrders").getJSONObject(0).remove(it) }
        listOf("variantId", "variantLabel", "sizeSnapshot", "skuSnapshot").forEach { json.getJSONArray("zenthaItems").getJSONObject(0).remove(it) }
        val restored = BackupManager.decodeSnapshot(json.toString())
        db.replaceAll(restored)
        assertEquals(1L, db.getZenthaItems().single().id); assertNull(db.getZenthaItems().single().variantId)
        assertEquals(18.0, db.getZenthaItems().single().unitPrice, .001); assertEquals(7.0, db.getZenthaItems().single().unitCost, .001)
        assertEquals(5.0, db.getZenthaProducts().single().realCost, .001)
        assertEquals(original.zenthaPayments, db.getZenthaPayments()); assertFalse(db.getZenthaOrders().single().stockPosted)
        assertEquals(4, ZenthaStore.priceLists(db.readableDatabase).size)
    }
    @Test fun portableBackupRestoresAttachmentBytesToNewPathsAndRejectsMissingFiles() {
        val original = fixture(); val file = File(context.cacheDir, "test.spectrobackup")
        BackupManager.writePortableBackup(file, original)
        val restored = BackupManager.readBackup(context, Uri.fromFile(file))
        val source = original.zentha.customizations.single().attachments.single()
        val target = restored.zentha.customizations.single().attachments.single()
        assertNotEquals(source, target); assertArrayEquals(File(source).readBytes(), File(target).readBytes())
        assertEquals(target, restored.production.single().images.single())
        assertEquals(target, restored.zenthaOrders.single().attachments.single())
        File(source).delete()
        assertThrows(IllegalArgumentException::class.java) { BackupManager.writePortableBackup(file, original) }
        assertFalse(file.exists()); assertTrue(File(target).exists())
    }
    @Test fun incompleteArchiveCannotReplaceExistingData() {
        val s = fixture(); db.replaceAll(s)
        val broken = File(context.cacheDir, "broken.zip")
        ZipOutputStream(broken.outputStream()).use { z -> z.putNextEntry(ZipEntry("data.json")); z.write(BackupManager.encodeSnapshot(s.copy(zenthaOrders = s.zenthaOrders.map { it.copy(attachments = listOf("attachment://attachments/missing")) })).toByteArray()); z.closeEntry() }
        assertThrows(IllegalArgumentException::class.java) { BackupManager.readBackup(context, Uri.fromFile(broken)) }
        assertEquals(s.zenthaOrders, db.getZenthaOrders())
    }
    @Test fun stockPostingIsAtomicIdempotentAndCancellationPreservesHistory() {
        val s = fixture(); db.replaceAll(s); val z = ZenthaRepository(db)
        z.postStock(1); z.postStock(1)
        assertEquals(7, ZenthaStore.balances(db.readableDatabase).single().quantityAvailable)
        assertEquals(2, ZenthaStore.movements(db.readableDatabase).size)
        val saved = db.getZenthaOrders().single(); val item = db.getZenthaItems().single()
        assertThrows(IllegalArgumentException::class.java) { z.saveOrder(saved, listOf(item.copy(quantity = 11)), s.zenthaExtraCosts) }
        assertEquals(3, db.getZenthaItems().single().quantity)
        assertEquals(7, ZenthaStore.balances(db.readableDatabase).single().quantityAvailable)
        z.saveOrder(saved, listOf(item.copy(quantity = 5)), s.zenthaExtraCosts)
        assertEquals(5, ZenthaStore.balances(db.readableDatabase).single().quantityAvailable)
        assertEquals(1L, db.getZenthaItems().single().id); assertEquals(1L, db.getZenthaExtraCosts().single().id)
        z.changeStatus(1, ZenthaOrderStatus.CANCELLED); z.changeStatus(1, ZenthaOrderStatus.CANCELLED)
        assertEquals(10, ZenthaStore.balances(db.readableDatabase).single().quantityAvailable)
        assertEquals(s.zenthaPayments, db.getZenthaPayments()); assertEquals(s.zentha.customizations, ZenthaStore.customizations(db.readableDatabase))
        assertEquals(s.zenthaOrders.single().attachments, db.getZenthaOrders().single().attachments)
        assertTrue(ZenthaStore.events(db.readableDatabase).size >= 4)
    }
    @Test fun catalogChangesDoNotRepriceSavedOrdersOrEraseVariantDescriptions() {
        val s = fixture(); db.replaceAll(s); val z = ZenthaRepository(db)
        z.saveProduct(s.zenthaProducts.single().copy(name = "Kit nuovo", standardCost = 500.0, standardPrice = 900.0))
        z.saveVariant(s.zentha.variants.single().copy(size = "XL", color = "Rosso"))
        z.saveOrder(s.zenthaOrders.single(), s.zenthaItems, s.zenthaExtraCosts)
        assertEquals(s.zenthaItems, db.getZenthaItems())
        assertThrows(IllegalArgumentException::class.java) { z.deleteProduct(1) }
        assertThrows(IllegalArgumentException::class.java) { z.addPayment(ZenthaPayment(orderId = 1, date = "2026-10-01", amount = .001)) }
    }
    @Test fun clearedLegacyDraftsDoNotReappear() {
        context.getSharedPreferences("spectro_drafts_v1", Context.MODE_PRIVATE).edit().putString("work", "{\"title\":\"Old\"}").putString("production", "{\"clientName\":\"Old\"}").commit()
        assertNotNull(DraftStore.loadWork(context)); assertNotNull(DraftStore.loadProduction(context))
        DraftStore.clearWork(context); DraftStore.clearProduction(context)
        assertNull(DraftStore.loadWork(context)); assertNull(DraftStore.loadProduction(context))
    }
    @Test fun attachmentImportPreservesDocumentExtensionAndBytes() {
        val file = File(context.cacheDir, "distinta_squadra.xlsx").apply { writeBytes(byteArrayOf(1,2,3,4)) }
        val imported = File(AttachmentStore.importToPrivateStorage(context, Uri.fromFile(file)))
        assertEquals("xlsx", imported.extension); assertArrayEquals(file.readBytes(), imported.readBytes())
    }
    @Test fun inconsistentStockBackupIsRejectedWithoutChangingDatabase() {
        val s = fixture(); db.replaceAll(s)
        val broken = s.copy(zentha = s.zentha.copy(balances = s.zentha.balances.map { it.copy(quantityAvailable = 100) }))
        assertThrows(IllegalArgumentException::class.java) { db.replaceAll(broken) }
        assertEquals(s.zentha, ZenthaStore.snapshot(db.readableDatabase))
    }
    @Test fun sevenPdfContentsPreserveDetailsAndKeepInternalCostsInEconomyReport() {
        val base = fixture()
        val notes = "Nota lunga ".repeat(700) + "FINE_DOCUMENTO"
        val s = base.copy(zenthaOrders = base.zenthaOrders.map { it.copy(notes = notes) })
        ZenthaDocument.entries.forEach { type ->
            val report = ZenthaReports.create(s, 1, type)
            assertEquals(type.title, report.title)
            val paragraphs = report.blocks.filterIsInstance<ZenthaReportBlock.Paragraph>()
            val info = report.blocks.filterIsInstance<ZenthaReportBlock.Info>()
            assertEquals(notes, paragraphs.first { it.label == "Note" }.text)
            assertTrue(info.any { it.value == "ZEN-1" }); assertTrue(info.any { it.value == "Club" })
            assertEquals(type == ZenthaDocument.ECONOMY, info.any { it.label == "Quote utile A / B" })
            assertEquals(type == ZenthaDocument.ECONOMY, info.any { it.label == "Costo / margine" })
            if(type == ZenthaDocument.ROSTER) assertTrue(paragraphs.any { it.label == "Rossi Mario" && it.text.contains("Numero 7") && it.text.contains("Maglia M") && it.text.contains("Pantalone S") })
            if(type == ZenthaDocument.PRODUCTION) {
                assertTrue(paragraphs.any { it.label == "Logo • Approvato" && it.text.contains("Sponsor") })
                assertTrue(report.blocks.filterIsInstance<ZenthaReportBlock.Images>().any { it.paths == s.zentha.customizations.single().attachments })
            }
            if(type == ZenthaDocument.SIZES) assertTrue(paragraphs.any { it.label == "Kit • M" && it.text == "3 pezzi" })
            if(type == ZenthaDocument.HISTORY) assertTrue(paragraphs.any { it.label == "2026-10-01T10:00" && it.text.contains("Ricevuto") })
            if(type == ZenthaDocument.CONFIRMATION) assertTrue(info.any { it.label == "Prezzo / totale" })
            if(type == ZenthaDocument.PACKING) assertFalse(info.any { it.label == "Prezzo / totale" })
        }
    }
    @Test fun productionWithoutExplicitRollsKeepsAggregateAndRollStockAligned() {
        val repo = SpectroRepository(context)
        val inventoryId = repo.saveInventory(InventoryItem(ownerName = "Club", fabricName = "Lycra", quantityMeters = 100.0, createdDate = "2026-10-01"))
        val record = ProductionRecord(date = "2026-10-01", clientName = "Club", fabricMode = FabricMode.INVENTORY, fabricId = inventoryId, fabricUsedMeters = 10.0)
        val id = repo.saveProduction(record)
        assertEquals(90.0, repo.snapshot().inventory.single().quantityMeters, .001)
        assertEquals(90.0, repo.snapshot().fabricRolls.sumOf { it.availableMeters }, .001)
        repo.saveProduction(record.copy(id = id, fabricUsedMeters = 15.0))
        assertEquals(85.0, repo.snapshot().fabricRolls.sumOf { it.availableMeters }, .001)
        repo.deleteProduction(id)
        assertEquals(100.0, repo.snapshot().inventory.single().quantityMeters, .001)
        assertEquals(100.0, repo.snapshot().fabricRolls.sumOf { it.availableMeters }, .001)
    }
    @Test fun editingMigratedLegacyProductionRestoresPreviouslyUntrackedConsumption() {
        val s = SpectroSnapshot(inventory = listOf(InventoryItem(id = 1, ownerName = "Club", fabricName = "Lycra", quantityMeters = 90.0, createdDate = "2026-10-01")), production = listOf(ProductionRecord(id = 1, date = "2026-10-01", clientName = "Club", fabricMode = FabricMode.INVENTORY, fabricId = 1, fabricUsedMeters = 10.0)))
        db.replaceAll(s)
        val repo = SpectroRepository(context)
        repo.saveProduction(s.production.single().copy(fabricUsedMeters = 15.0))
        val result = repo.snapshot()
        assertEquals(85.0, result.inventory.single().quantityMeters, .001)
        assertEquals(85.0, result.fabricRolls.sumOf { it.availableMeters }, .001)
        assertEquals(15.0, result.rollUsages.sumOf { it.meters }, .001)
    }

    @Test fun legacyJsonBackupAndAtomicTimelineChangesRemainSupported() {
        val json = """{"clients":[{"id":"9","nome":"Goccia","prezzoDefault":2.5}],"notes":[{"id":"10","data":"2025-01-01","clienteId":"9","clienteNome":"Goccia","ordineStampa":"Fiori","cartaStampataMt":12,"fatturatoLordo":30}],"workOrders":[],"inventory":[]}"""
        val imported = BackupManager.decodeSnapshot(json)
        assertEquals(9L, imported.clients.single().id); assertEquals(30.0, imported.production.single().grossRevenue, .001)
        assertTrue(imported.production.single().grossProfit.isFinite())
        db.replaceAll(imported)
        val repo = SpectroRepository(context)
        val id = repo.saveWorkOrder(WorkOrder(receivedDate = "2026-10-01", client = "Goccia", title = "Test", dueDate = "2026-10-10"))
        val event = OrderTimelineEvent(workOrderId = id, clientName = "Goccia", orderRef = "Test", date = "2026-10-02", title = "Nuova consegna", description = "Cliente", newDueDate = "2026-10-12")
        repo.saveTimelineEvent(event)
        assertEquals("2026-10-12", repo.snapshot().workOrders.single().dueDate)
        val previous = repo.snapshot()
        assertThrows(IllegalArgumentException::class.java) { repo.saveTimelineEvent(event.copy(newDueDate = "invalid")) }
        assertEquals(previous, repo.snapshot())
    }

}
