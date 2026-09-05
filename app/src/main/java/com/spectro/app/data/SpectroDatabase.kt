package com.spectro.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray

private fun List<String>.toJsonArrayString(): String = JSONArray(this).toString()
private fun String.toStringList(): List<String> = try {
    val a = JSONArray(this)
    List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
} catch (_: Exception) { emptyList() }

class SpectroDatabase(context: Context) : SQLiteOpenHelper(context, "spectro_native.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE clients(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                default_print_price REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE inventory(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                owner_type TEXT NOT NULL,
                owner_name TEXT NOT NULL,
                fabric_name TEXT NOT NULL,
                quantity_m REAL NOT NULL DEFAULT 0,
                purchase_price_m REAL NOT NULL DEFAULT 0,
                created_date TEXT NOT NULL,
                images_json TEXT NOT NULL DEFAULT '[]',
                minimum_quantity_m REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE production(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                type TEXT NOT NULL,
                date TEXT NOT NULL,
                client_id INTEGER,
                client_name TEXT NOT NULL DEFAULT '',
                order_ref TEXT NOT NULL DEFAULT '',
                designs_json TEXT NOT NULL DEFAULT '[]',
                print_price_m REAL NOT NULL DEFAULT 0,
                plotter TEXT NOT NULL DEFAULT 'X1',
                printed_paper_m REAL NOT NULL DEFAULT 0,
                protection_paper_m REAL NOT NULL DEFAULT 0,
                paper_waste_m REAL NOT NULL DEFAULT 0,
                fabric_mode TEXT NOT NULL DEFAULT 'NONE',
                fabric_id INTEGER,
                fabric_used_m REAL NOT NULL DEFAULT 0,
                fabric_waste_m REAL NOT NULL DEFAULT 0,
                fabric_sale_price_m REAL NOT NULL DEFAULT 0,
                manual_fabric_name TEXT NOT NULL DEFAULT '',
                fabric_display_name TEXT NOT NULL DEFAULT 'Nessuno',
                technical_notes TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                refund_cost REAL NOT NULL DEFAULT 0,
                fabric_loss_cost REAL NOT NULL DEFAULT 0,
                apply_fixed_costs INTEGER NOT NULL DEFAULT 1,
                paper_print_cost REAL NOT NULL DEFAULT 0,
                protection_paper_cost REAL NOT NULL DEFAULT 0,
                fabric_used_cost REAL NOT NULL DEFAULT 0,
                material_cost_total REAL NOT NULL DEFAULT 0,
                gross_revenue REAL NOT NULL DEFAULT 0,
                gross_profit REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE work_orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                received_date TEXT NOT NULL,
                client TEXT NOT NULL,
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL DEFAULT 'RECEIVED',
                due_date TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                blocker TEXT NOT NULL DEFAULT 'NONE'
            )
        """.trimIndent())
        createIssueTables(db)
        db.execSQL("""
            CREATE TABLE settings(
                id INTEGER PRIMARY KEY CHECK (id = 1),
                daily_fixed_cost REAL NOT NULL,
                plotter_x1_cost REAL NOT NULL,
                plotter_x2_cost REAL NOT NULL,
                protection_cost REAL NOT NULL,
                theme_key TEXT NOT NULL
            )
        """.trimIndent())
        val s = AppSettings()
        db.insert("settings", null, ContentValues().apply {
            put("id", 1); put("daily_fixed_cost", s.dailyFixedCost); put("plotter_x1_cost", s.plotterX1Cost)
            put("plotter_x2_cost", s.plotterX2Cost); put("protection_cost", s.protectionPaperCost); put("theme_key", s.themeKey)
        })
        createIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            runCatching { db.execSQL("ALTER TABLE inventory ADD COLUMN minimum_quantity_m REAL NOT NULL DEFAULT 0") }
            createIssueTables(db)
            createIndexes(db)
        }
    }

    private fun createIssueTables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS client_issues(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_ref TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                category TEXT NOT NULL DEFAULT 'OTHER',
                responsibility TEXT NOT NULL DEFAULT 'TO_VERIFY',
                severity TEXT NOT NULL DEFAULT 'MEDIUM',
                status TEXT NOT NULL DEFAULT 'OPEN',
                opened_date TEXT NOT NULL,
                expected_delivery_date TEXT NOT NULL DEFAULT '',
                revised_delivery_date TEXT NOT NULL DEFAULT '',
                delay_days INTEGER NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                internal_notes TEXT NOT NULL DEFAULT '',
                evidence_images_json TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS issue_updates(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                issue_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                note TEXT NOT NULL,
                images_json TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS payments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_ref TEXT NOT NULL DEFAULT '',
                amount REAL NOT NULL DEFAULT 0,
                due_date TEXT NOT NULL,
                paid INTEGER NOT NULL DEFAULT 0,
                paid_date TEXT NOT NULL DEFAULT '',
                method TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_date ON production(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_client ON production(client_id, client_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_owner ON inventory(owner_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_status ON work_orders(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_issue_client ON client_issues(client_id, client_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_issue_status ON client_issues(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_issue_updates ON issue_updates(issue_id, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_due ON payments(paid, due_date)")
    }

    fun getClients(db: SQLiteDatabase = readableDatabase): List<Client> =
        db.query("clients", null, null, null, null, null, "name COLLATE NOCASE ASC").use { c -> buildList { while (c.moveToNext()) add(c.client()) } }
    fun insertClient(client: Client, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("clients", null, ContentValues().apply { put("name", client.name.trim()); put("default_print_price", client.defaultPrintPrice) })
    fun updateClient(client: Client, db: SQLiteDatabase = writableDatabase) { db.update("clients", ContentValues().apply { put("name", client.name.trim()); put("default_print_price", client.defaultPrintPrice) }, "id=?", arrayOf(client.id.toString())) }
    fun deleteClient(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("clients", "id=?", arrayOf(id.toString())) }

    fun getInventory(db: SQLiteDatabase = readableDatabase): List<InventoryItem> = db.query("inventory", null, null, null, null, null, "owner_name COLLATE NOCASE, fabric_name COLLATE NOCASE").use { c -> buildList { while (c.moveToNext()) add(c.inventory()) } }
    fun getInventoryById(id: Long, db: SQLiteDatabase = readableDatabase): InventoryItem? = db.query("inventory", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.inventory() else null }
    fun insertInventory(item: InventoryItem, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("inventory", null, item.inventoryValues(false))
    fun updateInventory(item: InventoryItem, db: SQLiteDatabase = writableDatabase) { db.update("inventory", item.inventoryValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteInventory(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("inventory", "id=?", arrayOf(id.toString())) }
    fun setInventoryQuantity(id: Long, quantity: Double, db: SQLiteDatabase = writableDatabase) { db.update("inventory", ContentValues().apply { put("quantity_m", quantity) }, "id=?", arrayOf(id.toString())) }

    fun getProduction(db: SQLiteDatabase = readableDatabase): List<ProductionRecord> = db.query("production", null, null, null, null, null, "date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.production()) } }
    fun getProductionById(id: Long, db: SQLiteDatabase = readableDatabase): ProductionRecord? = db.query("production", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.production() else null }
    fun insertProduction(record: ProductionRecord, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("production", null, record.productionValues(false))
    fun updateProduction(record: ProductionRecord, db: SQLiteDatabase = writableDatabase) { db.update("production", record.productionValues(false), "id=?", arrayOf(record.id.toString())) }
    fun deleteProduction(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("production", "id=?", arrayOf(id.toString())) }

    fun getWorkOrders(db: SQLiteDatabase = readableDatabase): List<WorkOrder> = db.query("work_orders", null, null, null, null, null, "CASE status WHEN 'DELIVERED' THEN 1 WHEN 'COMPLETED' THEN 1 ELSE 0 END, received_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.workOrder()) } }
    fun insertWorkOrder(item: WorkOrder, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("work_orders", null, item.workValues())
    fun updateWorkOrder(item: WorkOrder, db: SQLiteDatabase = writableDatabase) { db.update("work_orders", item.workValues(), "id=?", arrayOf(item.id.toString())) }
    fun deleteWorkOrder(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("work_orders", "id=?", arrayOf(id.toString())) }

    fun getClientIssues(db: SQLiteDatabase = readableDatabase): List<ClientIssue> = db.query("client_issues", null, null, null, null, null, "CASE status WHEN 'RESOLVED' THEN 1 WHEN 'CLOSED' THEN 1 ELSE 0 END, opened_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.clientIssue()) } }
    fun insertClientIssue(item: ClientIssue, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("client_issues", null, item.issueValues(false))
    fun updateClientIssue(item: ClientIssue, db: SQLiteDatabase = writableDatabase) { db.update("client_issues", item.issueValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteClientIssue(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("issue_updates", "issue_id=?", arrayOf(id.toString())); db.delete("client_issues", "id=?", arrayOf(id.toString())) }

    fun getIssueUpdates(db: SQLiteDatabase = readableDatabase): List<IssueUpdate> = db.query("issue_updates", null, null, null, null, null, "date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.issueUpdate()) } }
    fun insertIssueUpdate(item: IssueUpdate, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("issue_updates", null, item.issueUpdateValues(false))
    fun deleteIssueUpdate(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("issue_updates", "id=?", arrayOf(id.toString())) }

    fun getPayments(db: SQLiteDatabase = readableDatabase): List<PaymentDeadline> = db.query("payments", null, null, null, null, null, "paid ASC, due_date ASC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.payment()) } }
    fun insertPayment(item: PaymentDeadline, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("payments", null, item.paymentValues(false))
    fun updatePayment(item: PaymentDeadline, db: SQLiteDatabase = writableDatabase) { db.update("payments", item.paymentValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deletePayment(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("payments", "id=?", arrayOf(id.toString())) }

    fun getSettings(db: SQLiteDatabase = readableDatabase): AppSettings = db.query("settings", null, "id=1", null, null, null, null).use { c ->
        if (!c.moveToFirst()) return AppSettings()
        AppSettings(c.double("daily_fixed_cost"), c.double("plotter_x1_cost"), c.double("plotter_x2_cost"), c.double("protection_cost"), c.string("theme_key"))
    }
    fun saveSettings(settings: AppSettings, db: SQLiteDatabase = writableDatabase) { db.update("settings", ContentValues().apply { put("daily_fixed_cost", settings.dailyFixedCost); put("plotter_x1_cost", settings.plotterX1Cost); put("plotter_x2_cost", settings.plotterX2Cost); put("protection_cost", settings.protectionPaperCost); put("theme_key", settings.themeKey) }, "id=1", null) }

    fun clearAll(db: SQLiteDatabase = writableDatabase) {
        db.delete("issue_updates", null, null); db.delete("client_issues", null, null); db.delete("payments", null, null)
        db.delete("production", null, null); db.delete("work_orders", null, null); db.delete("inventory", null, null); db.delete("clients", null, null)
        saveSettings(AppSettings(), db)
    }

    fun replaceAll(snapshot: SpectroSnapshot) {
        writableDatabase.runInTransaction {
            clearAll(this)
            snapshot.clients.forEach { c -> insertOrThrow("clients", null, ContentValues().apply { put("id", c.id); put("name", c.name); put("default_print_price", c.defaultPrintPrice) }) }
            snapshot.inventory.forEach { insertOrThrow("inventory", null, it.inventoryValues(true)) }
            snapshot.production.forEach { insertOrThrow("production", null, it.productionValues(true)) }
            snapshot.workOrders.forEach { insertOrThrow("work_orders", null, it.workValues().apply { put("id", it.id) }) }
            snapshot.clientIssues.forEach { insertOrThrow("client_issues", null, it.issueValues(true)) }
            snapshot.issueUpdates.forEach { insertOrThrow("issue_updates", null, it.issueUpdateValues(true)) }
            snapshot.payments.forEach { insertOrThrow("payments", null, it.paymentValues(true)) }
            saveSettings(snapshot.settings, this)
        }
    }

    fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase; db.beginTransaction()
        try { val result = block(db); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    private inline fun <T> SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> T): T { beginTransaction(); try { val r = block(); setTransactionSuccessful(); return r } finally { endTransaction() } }

    private fun Cursor.client() = Client(long("id"), string("name"), double("default_print_price"))
    private fun Cursor.inventory() = InventoryItem(long("id"), enumValueOrDefault(string("owner_type"), OwnerType.CLIENT), string("owner_name"), string("fabric_name"), double("quantity_m"), double("purchase_price_m"), string("created_date"), string("images_json").toStringList(), optionalDouble("minimum_quantity_m"))
    private fun Cursor.production() = ProductionRecord(
        id = long("id"), type = enumValueOrDefault(string("type"), RecordType.PRODUCTION), date = string("date"), clientId = nullableLong("client_id"), clientName = string("client_name"), orderRef = string("order_ref"),
        designs = string("designs_json").toStringList(), printPricePerMeter = double("print_price_m"), plotter = string("plotter"), printedPaperMeters = double("printed_paper_m"), protectionPaperMeters = double("protection_paper_m"), paperWasteMeters = double("paper_waste_m"),
        fabricMode = enumValueOrDefault(string("fabric_mode"), FabricMode.NONE), fabricId = nullableLong("fabric_id"), fabricUsedMeters = double("fabric_used_m"), fabricWasteMeters = double("fabric_waste_m"), fabricSalePricePerMeter = double("fabric_sale_price_m"), manualFabricName = string("manual_fabric_name"),
        fabricDisplayName = string("fabric_display_name"), technicalNotes = string("technical_notes"), images = string("images_json").toStringList(), refundCost = double("refund_cost"), fabricLossCost = double("fabric_loss_cost"), applyFixedCosts = int("apply_fixed_costs") == 1,
        paperPrintCost = double("paper_print_cost"), protectionPaperCost = double("protection_paper_cost"), fabricUsedCost = double("fabric_used_cost"), materialCostTotal = double("material_cost_total"), grossRevenue = double("gross_revenue"), grossProfit = double("gross_profit")
    )
    private fun Cursor.workOrder() = WorkOrder(long("id"), string("received_date"), string("client"), string("title"), string("description"), string("images_json").toStringList(), enumValueOrDefault(string("status"), WorkStatus.RECEIVED), string("due_date"), enumValueOrDefault(string("priority"), Priority.NORMAL), enumValueOrDefault(string("blocker"), Blocker.NONE))
    private fun Cursor.clientIssue() = ClientIssue(long("id"), nullableLong("client_id"), string("client_name"), string("order_ref"), string("title"), enumValueOrDefault(string("category"), IssueCategory.OTHER), enumValueOrDefault(string("responsibility"), IssueResponsibility.TO_VERIFY), enumValueOrDefault(string("severity"), IssueSeverity.MEDIUM), enumValueOrDefault(string("status"), IssueStatus.OPEN), string("opened_date"), string("expected_delivery_date"), string("revised_delivery_date"), int("delay_days"), string("description"), string("internal_notes"), string("evidence_images_json").toStringList())
    private fun Cursor.issueUpdate() = IssueUpdate(long("id"), long("issue_id"), string("date"), string("note"), string("images_json").toStringList())
    private fun Cursor.payment() = PaymentDeadline(long("id"), nullableLong("client_id"), string("client_name"), string("order_ref"), double("amount"), string("due_date"), int("paid") == 1, string("paid_date"), enumValueOrDefault(string("method"), PaymentMethod.UNSPECIFIED), string("notes"))

    private fun InventoryItem.inventoryValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("owner_type", ownerType.name); put("owner_name", ownerName); put("fabric_name", fabricName); put("quantity_m", quantityMeters); put("purchase_price_m", purchasePricePerMeter); put("created_date", createdDate); put("images_json", images.toJsonArrayString()); put("minimum_quantity_m", minimumQuantityMeters) }
    private fun ProductionRecord.productionValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id); put("type", type.name); put("date", date); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("designs_json", designs.toJsonArrayString()); put("print_price_m", printPricePerMeter)
        put("plotter", plotter); put("printed_paper_m", printedPaperMeters); put("protection_paper_m", protectionPaperMeters); put("paper_waste_m", paperWasteMeters); put("fabric_mode", fabricMode.name); if (fabricId == null) putNull("fabric_id") else put("fabric_id", fabricId)
        put("fabric_used_m", fabricUsedMeters); put("fabric_waste_m", fabricWasteMeters); put("fabric_sale_price_m", fabricSalePricePerMeter); put("manual_fabric_name", manualFabricName); put("fabric_display_name", fabricDisplayName); put("technical_notes", technicalNotes); put("images_json", images.toJsonArrayString())
        put("refund_cost", refundCost); put("fabric_loss_cost", fabricLossCost); put("apply_fixed_costs", if (applyFixedCosts) 1 else 0); put("paper_print_cost", paperPrintCost); put("protection_paper_cost", protectionPaperCost); put("fabric_used_cost", fabricUsedCost); put("material_cost_total", materialCostTotal); put("gross_revenue", grossRevenue); put("gross_profit", grossProfit)
    }
    private fun WorkOrder.workValues() = ContentValues().apply { put("received_date", receivedDate); put("client", client); put("title", title); put("description", description); put("images_json", images.toJsonArrayString()); put("status", status.name); put("due_date", dueDate); put("priority", priority.name); put("blocker", blocker.name) }
    private fun ClientIssue.issueValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("title", title); put("category", category.name); put("responsibility", responsibility.name); put("severity", severity.name); put("status", status.name); put("opened_date", openedDate); put("expected_delivery_date", expectedDeliveryDate); put("revised_delivery_date", revisedDeliveryDate); put("delay_days", delayDays); put("description", description); put("internal_notes", internalNotes); put("evidence_images_json", evidenceImages.toJsonArrayString()) }
    private fun IssueUpdate.issueUpdateValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("issue_id", issueId); put("date", date); put("note", note); put("images_json", images.toJsonArrayString()) }
    private fun PaymentDeadline.paymentValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("amount", amount); put("due_date", dueDate); put("paid", if (paid) 1 else 0); put("paid_date", paidDate); put("method", method.name); put("notes", notes) }

    private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name)) ?: ""
    private fun Cursor.double(name: String) = getDouble(getColumnIndexOrThrow(name))
    private fun Cursor.optionalDouble(name: String): Double { val i = getColumnIndex(name); return if (i < 0 || isNull(i)) 0.0 else getDouble(i) }
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
    private fun Cursor.nullableLong(name: String): Long? { val i = getColumnIndexOrThrow(name); return if (isNull(i)) null else getLong(i) }
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T = enumValues<T>().firstOrNull { it.name == value } ?: default
}
