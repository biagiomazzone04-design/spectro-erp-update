package com.spectro.app.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject

private fun List<String>.toJsonArrayString(): String = JSONArray(this).toString()
private fun String.toStringList(): List<String> = try {
    val a = JSONArray(this)
    List(a.length()) { a.optString(it) }.filter { it.isNotBlank() }
} catch (_: Exception) { emptyList() }

private fun List<EconomicMaterialUsage>.toEconomicMaterialsJson(): String = JSONArray().apply {
    this@toEconomicMaterialsJson.forEach { item ->
        put(JSONObject().apply {
            put("name", item.name)
            put("quantity", item.quantity)
            put("unitCost", item.unitCost)
        })
    }
}.toString()

private fun String.toEconomicMaterials(): List<EconomicMaterialUsage> = try {
    val a = JSONArray(this)
    List(a.length()) { index ->
        val o = a.optJSONObject(index) ?: JSONObject()
        EconomicMaterialUsage(o.optString("name"), o.optDouble("quantity"), o.optDouble("unitCost"))
    }.filter { it.name.isNotBlank() || it.quantity > 0 || it.unitCost > 0 }
} catch (_: Exception) { emptyList() }

class SpectroDatabase(context: Context) : SQLiteOpenHelper(context, "spectro_native.db", null, 11) {
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
                minimum_quantity_m REAL NOT NULL DEFAULT 0,
                supplier_name TEXT NOT NULL DEFAULT '',
                composition TEXT NOT NULL DEFAULT '',
                weight_gsm TEXT NOT NULL DEFAULT '',
                variant TEXT NOT NULL DEFAULT '',
                lot_code TEXT NOT NULL DEFAULT '',
                processing_cost_m REAL NOT NULL DEFAULT 0,
                default_sale_price_m REAL NOT NULL DEFAULT 0,
                archived INTEGER NOT NULL DEFAULT 0
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
                gross_profit REAL NOT NULL DEFAULT 0,
                work_order_id INTEGER,
                zentha_order_id INTEGER
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
                status TEXT NOT NULL DEFAULT 'IN_PROCESS',
                due_date TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                blocker TEXT NOT NULL DEFAULT 'NONE',
                client_id INTEGER,
                order_code TEXT NOT NULL DEFAULT '',
                fabric_inventory_id INTEGER,
                ordered_m REAL NOT NULL DEFAULT 0,
                print_price_m REAL NOT NULL DEFAULT 0,
                design_code TEXT NOT NULL DEFAULT '',
                production_record_id INTEGER,
                effective_delivery_date TEXT NOT NULL DEFAULT '',
                cancel_reason TEXT NOT NULL DEFAULT '',
                material_received_date TEXT NOT NULL DEFAULT '',
                material_receipt_notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        createIssueTables(db)
        createV3Tables(db)
        createV5Tables(db)
        createV6Tables(db)
        createV7Tables(db)
        db.execSQL("""
            CREATE TABLE settings(
                id INTEGER PRIMARY KEY CHECK (id = 1),
                daily_fixed_cost REAL NOT NULL,
                plotter_x1_cost REAL NOT NULL,
                plotter_x2_cost REAL NOT NULL,
                protection_cost REAL NOT NULL,
                theme_key TEXT NOT NULL,
                zentha_split_a REAL NOT NULL DEFAULT 50,
                zentha_split_b REAL NOT NULL DEFAULT 50,
                chromia_rate_m REAL NOT NULL DEFAULT 2.5
            )
        """.trimIndent())
        val s = AppSettings()
        db.insert("settings", null, ContentValues().apply {
            put("id", 1); put("daily_fixed_cost", s.dailyFixedCost); put("plotter_x1_cost", s.plotterX1Cost)
            put("plotter_x2_cost", s.plotterX2Cost); put("protection_cost", s.protectionPaperCost); put("theme_key", s.themeKey)
            put("zentha_split_a", s.zenthaSplitA); put("zentha_split_b", s.zenthaSplitB); put("chromia_rate_m", s.chromiaRatePerMeter)
        })
        createIndexes(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN minimum_quantity_m REAL NOT NULL DEFAULT 0")
            createIssueTables(db)
        }
        if (oldVersion < 3) {
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN supplier_name TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN composition TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN weight_gsm TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN variant TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN lot_code TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE production ADD COLUMN work_order_id INTEGER")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN client_id INTEGER")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN order_code TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN fabric_inventory_id INTEGER")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN ordered_m REAL NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN print_price_m REAL NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN design_code TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN production_record_id INTEGER")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN effective_delivery_date TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN cancel_reason TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE settings ADD COLUMN zentha_split_a REAL NOT NULL DEFAULT 50")
            safeAlter(db, "ALTER TABLE settings ADD COLUMN zentha_split_b REAL NOT NULL DEFAULT 50")
            createV3Tables(db)
            migrateLegacyInventoryToRolls(db)
            populateMissingOrderCodes(db)
        }
        if (oldVersion < 4) {
            safeAlter(db, "ALTER TABLE fabric_rolls ADD COLUMN images_json TEXT NOT NULL DEFAULT '[]'")
            safeAlter(db, "ALTER TABLE cotton_orders ADD COLUMN title TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE cotton_orders ADD COLUMN images_json TEXT NOT NULL DEFAULT '[]'")
        }
        if (oldVersion < 5) {
            createV5Tables(db)
        }
        if (oldVersion < 6) {
            createV6Tables(db)
        }
        if (oldVersion < 7) {
            createV7Tables(db)
        }
        if (oldVersion < 8) {
            // Some installations were already marked schema 7 before cloud
            // synchronization was introduced. Re-run the idempotent creator so
            // their existing ERP data stays untouched and only sync metadata is added.
            createV6Tables(db)
        }
        if (oldVersion < 9) {
            safeAlter(db, "ALTER TABLE cloud_sync_state ADD COLUMN pending_changes INTEGER NOT NULL DEFAULT 0")
        }
        if (oldVersion < 10) {
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN processing_cost_m REAL NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN default_sale_price_m REAL NOT NULL DEFAULT 0")
            safeAlter(db, "ALTER TABLE production ADD COLUMN zentha_order_id INTEGER")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN material_received_date TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE work_orders ADD COLUMN material_receipt_notes TEXT NOT NULL DEFAULT ''")
            safeAlter(db, "ALTER TABLE zentha_orders ADD COLUMN chromia_rate_m REAL NOT NULL DEFAULT 2.5")
            safeAlter(db, "ALTER TABLE settings ADD COLUMN chromia_rate_m REAL NOT NULL DEFAULT 2.5")
        }
        if (oldVersion < 11) {
            safeAlter(db, "ALTER TABLE inventory ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
            deduplicateWebWorkOrders(db)
        }
        createIndexes(db)
    }

    /** Ripara una sola volta le copie create dalle vecchie importazioni web.
     *  Mantiene il record più antico e riallaccia tutte le relazioni prima di
     *  rimuovere le copie, così produzione, timeline e consumi non si perdono. */
    private fun deduplicateWebWorkOrders(db: SQLiteDatabase) {
        val duplicateCodes = db.rawQuery(
            "SELECT order_code FROM work_orders WHERE order_code LIKE 'SPE-WEB-%' GROUP BY order_code HAVING COUNT(*) > 1",
            null
        ).use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        duplicateCodes.forEach { code ->
            val ids = db.query("work_orders", arrayOf("id"), "order_code=?", arrayOf(code), null, null, "id ASC")
                .use { cursor -> buildList { while (cursor.moveToNext()) add(cursor.getLong(0)) } }
            val keeper = ids.firstOrNull() ?: return@forEach
            ids.drop(1).forEach { duplicate ->
                db.execSQL("UPDATE production SET work_order_id=? WHERE work_order_id=?", arrayOf(keeper, duplicate))
                db.execSQL("UPDATE order_timeline SET work_order_id=? WHERE work_order_id=?", arrayOf(keeper, duplicate))
                db.execSQL("UPDATE operational_tasks SET work_order_id=? WHERE work_order_id=?", arrayOf(keeper, duplicate))
                db.execSQL("UPDATE roll_usages SET order_id=? WHERE order_kind='WORK' AND order_id=?", arrayOf(keeper, duplicate))
                db.execSQL("UPDATE stock_movements SET order_id=? WHERE order_kind='WORK' AND order_id=?", arrayOf(keeper, duplicate))
                db.delete("work_orders", "id=?", arrayOf(duplicate.toString()))
            }
        }
    }

    private fun safeAlter(db: SQLiteDatabase, sql: String) { runCatching { db.execSQL(sql) } }

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

    private fun createV6Tables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cloud_sync_state(
                id INTEGER PRIMARY KEY CHECK (id = 1),
                organization_id TEXT NOT NULL DEFAULT '',
                device_id TEXT NOT NULL DEFAULT '',
                base_revision INTEGER NOT NULL DEFAULT 0,
                last_seen_remote_revision INTEGER NOT NULL DEFAULT 0,
                last_synced_at TEXT NOT NULL DEFAULT '',
                last_error TEXT NOT NULL DEFAULT '',
                recovery_backup_path TEXT NOT NULL DEFAULT '',
                pending_changes INTEGER NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            INSERT OR IGNORE INTO cloud_sync_state(
                id, organization_id, device_id, base_revision,
                last_seen_remote_revision, last_synced_at, last_error,
                recovery_backup_path, pending_changes
            ) VALUES (1, '', '', 0, 0, '', '', '', 0)
        """.trimIndent())
    }

    private fun createV7Tables(db: SQLiteDatabase) {
        listOf(
            "ALTER TABLE zentha_products ADD COLUMN fabric_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN rib_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN lining_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN print_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN accessory_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN cutting_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN sewing_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN label_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN packaging_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN other_cost REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN price_list_name TEXT NOT NULL DEFAULT 'Listino standard'",
            "ALTER TABLE zentha_products ADD COLUMN markup_percent REAL NOT NULL DEFAULT 0",
            "ALTER TABLE zentha_products ADD COLUMN source_reference TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN sku_snapshot TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN product_description_snapshot TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN variant TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN color TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN size_breakdown TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN personalization TEXT NOT NULL DEFAULT ''",
            "ALTER TABLE zentha_order_items ADD COLUMN production_notes TEXT NOT NULL DEFAULT ''"
        ).forEach { safeAlter(db, it) }
        seedZenthaCatalog(db)
    }

    private fun seedZenthaCatalog(db: SQLiteDatabase) {
        ZenthaCatalog.products.forEach { product ->
            val exists = db.query(
                "zentha_products",
                arrayOf("id"),
                "UPPER(TRIM(sku)) = UPPER(TRIM(?))",
                arrayOf(product.sku),
                null,
                null,
                null,
                "1"
            ).use { it.moveToFirst() }
            if (!exists) db.insertOrThrow("zentha_products", null, product.zenthaProductValues(false))
        }
    }

    private fun createV3Tables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS fabric_rolls(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inventory_id INTEGER NOT NULL,
                roll_code TEXT NOT NULL,
                initial_m REAL NOT NULL DEFAULT 0,
                available_m REAL NOT NULL DEFAULT 0,
                created_date TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'INTACT',
                images_json TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS stock_movements(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                inventory_id INTEGER NOT NULL,
                roll_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                date_time TEXT NOT NULL,
                meters REAL NOT NULL DEFAULT 0,
                order_kind TEXT,
                order_id INTEGER,
                order_ref TEXT NOT NULL DEFAULT '',
                note TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS roll_usages(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_kind TEXT NOT NULL,
                order_id INTEGER NOT NULL,
                order_ref TEXT NOT NULL DEFAULT '',
                inventory_id INTEGER NOT NULL,
                roll_id INTEGER NOT NULL,
                meters REAL NOT NULL DEFAULT 0,
                created_at TEXT NOT NULL
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS order_timeline(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                work_order_id INTEGER NOT NULL,
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_ref TEXT NOT NULL,
                event_date TEXT NOT NULL,
                event_time TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT 'NOTE',
                responsibility TEXT NOT NULL DEFAULT 'NONE',
                severity TEXT NOT NULL DEFAULT 'INFO',
                status TEXT NOT NULL DEFAULT 'OPEN',
                title TEXT NOT NULL,
                description TEXT NOT NULL DEFAULT '',
                old_due_date TEXT NOT NULL DEFAULT '',
                new_due_date TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                resolution_note TEXT NOT NULL DEFAULT '',
                resolved_date TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS cotton_orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_code TEXT NOT NULL DEFAULT '',
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_date TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                fabric_name TEXT NOT NULL,
                print_type TEXT NOT NULL DEFAULT 'REACTIVE',
                ordered_m REAL NOT NULL DEFAULT 0,
                actual_produced_m REAL NOT NULL DEFAULT 0,
                delivered_m REAL NOT NULL DEFAULT 0,
                defective_m REAL NOT NULL DEFAULT 0,
                sample_m REAL NOT NULL DEFAULT 0,
                leftover_m REAL NOT NULL DEFAULT 0,
                production_cost_m REAL NOT NULL DEFAULT 0,
                print_price_m REAL NOT NULL DEFAULT 0,
                fabric_owner_type TEXT NOT NULL DEFAULT 'CLIENT',
                inventory_id INTEGER,
                fabric_cost_m REAL NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'IN_PROCESS',
                title TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS zentha_products(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                name TEXT NOT NULL,
                sku TEXT NOT NULL DEFAULT '',
                category TEXT NOT NULL DEFAULT '',
                standard_cost REAL NOT NULL DEFAULT 0,
                standard_price REAL NOT NULL DEFAULT 0,
                description TEXT NOT NULL DEFAULT '',
                image_path TEXT NOT NULL DEFAULT '',
                active INTEGER NOT NULL DEFAULT 1,
                fabric_cost REAL NOT NULL DEFAULT 0,
                rib_cost REAL NOT NULL DEFAULT 0,
                lining_cost REAL NOT NULL DEFAULT 0,
                print_cost REAL NOT NULL DEFAULT 0,
                accessory_cost REAL NOT NULL DEFAULT 0,
                cutting_cost REAL NOT NULL DEFAULT 0,
                sewing_cost REAL NOT NULL DEFAULT 0,
                label_cost REAL NOT NULL DEFAULT 0,
                packaging_cost REAL NOT NULL DEFAULT 0,
                other_cost REAL NOT NULL DEFAULT 0,
                price_list_name TEXT NOT NULL DEFAULT 'Listino standard',
                markup_percent REAL NOT NULL DEFAULT 0,
                source_reference TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS zentha_orders(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_code TEXT NOT NULL DEFAULT '',
                client_id INTEGER,
                client_name TEXT NOT NULL,
                order_date TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                status TEXT NOT NULL DEFAULT 'CONFIRMED',
                notes TEXT NOT NULL DEFAULT '',
                attachments_json TEXT NOT NULL DEFAULT '[]',
                split_a REAL NOT NULL DEFAULT 50,
                split_b REAL NOT NULL DEFAULT 50,
                chromia_rate_m REAL NOT NULL DEFAULT 2.5
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS zentha_order_items(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                product_id INTEGER,
                product_name TEXT NOT NULL,
                quantity INTEGER NOT NULL DEFAULT 0,
                unit_price REAL NOT NULL DEFAULT 0,
                unit_cost REAL NOT NULL DEFAULT 0,
                sku_snapshot TEXT NOT NULL DEFAULT '',
                product_description_snapshot TEXT NOT NULL DEFAULT '',
                variant TEXT NOT NULL DEFAULT '',
                color TEXT NOT NULL DEFAULT '',
                size_breakdown TEXT NOT NULL DEFAULT '',
                personalization TEXT NOT NULL DEFAULT '',
                production_notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS zentha_extra_costs(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                label TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS zentha_payments(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                order_id INTEGER NOT NULL,
                payment_date TEXT NOT NULL,
                amount REAL NOT NULL DEFAULT 0,
                method TEXT NOT NULL DEFAULT 'UNSPECIFIED',
                notes TEXT NOT NULL DEFAULT ''
            )
        """.trimIndent())
    }

    private fun createV5Tables(db: SQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS economic_works(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                work_date TEXT NOT NULL,
                client_id INTEGER,
                client_name TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL,
                quantity REAL NOT NULL DEFAULT 0,
                unit_price REAL NOT NULL DEFAULT 0,
                manual_gross REAL NOT NULL DEFAULT 0,
                materials_json TEXT NOT NULL DEFAULT '[]',
                other_costs REAL NOT NULL DEFAULT 0,
                notes TEXT NOT NULL DEFAULT '',
                images_json TEXT NOT NULL DEFAULT '[]',
                status TEXT NOT NULL DEFAULT 'OPEN'
            )
        """.trimIndent())
        db.execSQL("""
            CREATE TABLE IF NOT EXISTS operational_tasks(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                created_date TEXT NOT NULL,
                due_date TEXT NOT NULL DEFAULT '',
                client_id INTEGER,
                client_name TEXT NOT NULL DEFAULT '',
                work_order_id INTEGER,
                title TEXT NOT NULL,
                type TEXT NOT NULL DEFAULT 'OTHER',
                description TEXT NOT NULL DEFAULT '',
                priority TEXT NOT NULL DEFAULT 'NORMAL',
                status TEXT NOT NULL DEFAULT 'TODO',
                images_json TEXT NOT NULL DEFAULT '[]'
            )
        """.trimIndent())
    }

    private fun migrateLegacyInventoryToRolls(db: SQLiteDatabase) {
        db.execSQL("""
            INSERT INTO fabric_rolls(inventory_id, roll_code, initial_m, available_m, created_date, note, status)
            SELECT i.id, 'LEGACY-' || printf('%04d', i.id), i.quantity_m, i.quantity_m, i.created_date,
                   'Creato automaticamente durante aggiornamento Spectro 3.0',
                   CASE WHEN i.quantity_m <= 0 THEN 'DEPLETED' ELSE 'OPEN' END
            FROM inventory i
            WHERE NOT EXISTS (SELECT 1 FROM fabric_rolls r WHERE r.inventory_id=i.id)
        """.trimIndent())
        db.execSQL("""
            INSERT INTO stock_movements(inventory_id, roll_id, type, date_time, meters, order_ref, note)
            SELECT r.inventory_id, r.id, 'LOAD', r.created_date || 'T00:00', r.initial_m, '', 'Migrazione giacenza precedente'
            FROM fabric_rolls r
            WHERE r.roll_code LIKE 'LEGACY-%'
              AND NOT EXISTS (SELECT 1 FROM stock_movements m WHERE m.roll_id=r.id)
        """.trimIndent())
    }

    private fun populateMissingOrderCodes(db: SQLiteDatabase) {
        db.rawQuery("SELECT id FROM work_orders WHERE order_code='' OR order_code IS NULL ORDER BY id", null).use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                db.update("work_orders", ContentValues().apply { put("order_code", "SPE-LEGACY-${id.toString().padStart(4, '0')}") }, "id=?", arrayOf(id.toString()))
            }
        }
    }

    private fun createIndexes(db: SQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_date ON production(date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_client ON production(client_id, client_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_zentha ON production(zentha_order_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_inventory_owner ON inventory(owner_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_status ON work_orders(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_work_code ON work_orders(order_code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_issue_client ON client_issues(client_id, client_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_issue_status ON client_issues(status)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_issue_updates ON issue_updates(issue_id, date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_payments_due ON payments(paid, due_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_roll_inventory ON fabric_rolls(inventory_id, status)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_roll_code_inventory ON fabric_rolls(inventory_id, roll_code)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_stock_roll ON stock_movements(roll_id, date_time)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_order ON roll_usages(order_kind, order_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_timeline_order ON order_timeline(work_order_id, event_date, id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_cotton_date ON cotton_orders(order_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_zentha_order_date ON zentha_orders(order_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_zentha_items_order ON zentha_order_items(order_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_zentha_payments_order ON zentha_payments(order_id)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_economic_work_date ON economic_works(work_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_economic_work_client ON economic_works(client_id, client_name)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operational_task_status ON operational_tasks(status, due_date)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_operational_task_client ON operational_tasks(client_id, client_name)")
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
    fun setInventoryArchived(id: Long, archived: Boolean, db: SQLiteDatabase = writableDatabase) { db.update("inventory", ContentValues().apply { put("archived", if (archived) 1 else 0) }, "id=?", arrayOf(id.toString())) }
    fun setInventoryQuantity(id: Long, quantity: Double, db: SQLiteDatabase = writableDatabase) { db.update("inventory", ContentValues().apply { put("quantity_m", quantity) }, "id=?", arrayOf(id.toString())) }

    fun getFabricRolls(db: SQLiteDatabase = readableDatabase): List<FabricRoll> = db.query("fabric_rolls", null, null, null, null, null, "inventory_id, id").use { c -> buildList { while (c.moveToNext()) add(c.fabricRoll()) } }
    fun getFabricRollById(id: Long, db: SQLiteDatabase = readableDatabase): FabricRoll? = db.query("fabric_rolls", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.fabricRoll() else null }
    fun insertFabricRoll(item: FabricRoll, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("fabric_rolls", null, item.fabricRollValues(false))
    fun updateFabricRoll(item: FabricRoll, db: SQLiteDatabase = writableDatabase) { db.update("fabric_rolls", item.fabricRollValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteFabricRoll(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("fabric_rolls", "id=?", arrayOf(id.toString())) }

    fun getStockMovements(db: SQLiteDatabase = readableDatabase): List<StockMovement> = db.query("stock_movements", null, null, null, null, null, "date_time DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.stockMovement()) } }
    fun insertStockMovement(item: StockMovement, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("stock_movements", null, item.stockMovementValues(false))

    fun getRollUsages(db: SQLiteDatabase = readableDatabase): List<RollUsage> = db.query("roll_usages", null, null, null, null, null, "created_at DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.rollUsage()) } }
    fun insertRollUsage(item: RollUsage, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("roll_usages", null, item.rollUsageValues(false))
    fun deleteRollUsages(orderKind: OrderKind, orderId: Long, db: SQLiteDatabase = writableDatabase) { db.delete("roll_usages", "order_kind=? AND order_id=?", arrayOf(orderKind.name, orderId.toString())) }

    fun getProduction(db: SQLiteDatabase = readableDatabase): List<ProductionRecord> = db.query("production", null, null, null, null, null, "date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.production()) } }
    fun getProductionById(id: Long, db: SQLiteDatabase = readableDatabase): ProductionRecord? = db.query("production", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.production() else null }
    fun insertProduction(record: ProductionRecord, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("production", null, record.productionValues(false))
    fun upsertCloudProduction(record: ProductionRecord, db: SQLiteDatabase = writableDatabase): Long = db.insertWithOnConflict("production", null, record.productionValues(true), SQLiteDatabase.CONFLICT_REPLACE)
    fun updateProduction(record: ProductionRecord, db: SQLiteDatabase = writableDatabase) { db.update("production", record.productionValues(false), "id=?", arrayOf(record.id.toString())) }
    fun deleteProduction(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("production", "id=?", arrayOf(id.toString())) }

    fun getWorkOrders(db: SQLiteDatabase = readableDatabase): List<WorkOrder> = db.query("work_orders", null, null, null, null, null, "CASE status WHEN 'DELIVERED' THEN 1 WHEN 'COMPLETED' THEN 1 WHEN 'CLOSED' THEN 1 WHEN 'CANCELLED' THEN 1 ELSE 0 END, received_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.workOrder()) } }
    fun getWorkOrderById(id: Long, db: SQLiteDatabase = readableDatabase): WorkOrder? = db.query("work_orders", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.workOrder() else null }
    fun getWorkOrderByCode(code: String, db: SQLiteDatabase = readableDatabase): WorkOrder? = db.query("work_orders", null, "order_code=?", arrayOf(code), null, null, "id ASC", "1").use { c -> if (c.moveToFirst()) c.workOrder() else null }
    fun insertWorkOrder(item: WorkOrder, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("work_orders", null, item.workValues(false))
    fun upsertCloudWorkOrder(item: WorkOrder, db: SQLiteDatabase = writableDatabase): Long = db.insertWithOnConflict("work_orders", null, item.workValues(true), SQLiteDatabase.CONFLICT_REPLACE)
    fun updateWorkOrder(item: WorkOrder, db: SQLiteDatabase = writableDatabase) { db.update("work_orders", item.workValues(false), "id=?", arrayOf(item.id.toString())) }
    fun adoptCloudWorkOrderId(existingId: Long, cloudId: Long, db: SQLiteDatabase = writableDatabase) {
        if (existingId == cloudId || getWorkOrderById(cloudId, db) != null) return
        db.execSQL("UPDATE production SET work_order_id=? WHERE work_order_id=?", arrayOf(cloudId, existingId))
        db.execSQL("UPDATE order_timeline SET work_order_id=? WHERE work_order_id=?", arrayOf(cloudId, existingId))
        db.execSQL("UPDATE operational_tasks SET work_order_id=? WHERE work_order_id=?", arrayOf(cloudId, existingId))
        db.execSQL("UPDATE roll_usages SET order_id=? WHERE order_kind='WORK' AND order_id=?", arrayOf(cloudId, existingId))
        db.execSQL("UPDATE stock_movements SET order_id=? WHERE order_kind='WORK' AND order_id=?", arrayOf(cloudId, existingId))
        db.execSQL("UPDATE work_orders SET id=? WHERE id=?", arrayOf(cloudId, existingId))
    }
    fun deleteWorkOrder(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("work_orders", "id=?", arrayOf(id.toString())) }

    fun getTimelineEvents(db: SQLiteDatabase = readableDatabase): List<OrderTimelineEvent> = db.query("order_timeline", null, null, null, null, null, "event_date DESC, event_time DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.timelineEvent()) } }
    fun insertTimelineEvent(item: OrderTimelineEvent, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("order_timeline", null, item.timelineValues(false))
    fun updateTimelineEvent(item: OrderTimelineEvent, db: SQLiteDatabase = writableDatabase) { db.update("order_timeline", item.timelineValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteTimelineEvent(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("order_timeline", "id=?", arrayOf(id.toString())) }

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

    fun getCottonOrders(db: SQLiteDatabase = readableDatabase): List<CottonOrder> = db.query("cotton_orders", null, null, null, null, null, "order_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.cottonOrder()) } }
    fun getCottonOrderById(id: Long, db: SQLiteDatabase = readableDatabase): CottonOrder? = db.query("cotton_orders", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.cottonOrder() else null }
    fun insertCottonOrder(item: CottonOrder, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("cotton_orders", null, item.cottonValues(false))
    fun updateCottonOrder(item: CottonOrder, db: SQLiteDatabase = writableDatabase) { db.update("cotton_orders", item.cottonValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteCottonOrder(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("cotton_orders", "id=?", arrayOf(id.toString())) }

    fun getZenthaProducts(db: SQLiteDatabase = readableDatabase): List<ZenthaProduct> = db.query("zentha_products", null, null, null, null, null, "active DESC, name COLLATE NOCASE").use { c -> buildList { while (c.moveToNext()) add(c.zenthaProduct()) } }
    fun insertZenthaProduct(item: ZenthaProduct, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("zentha_products", null, item.zenthaProductValues(false))
    fun updateZenthaProduct(item: ZenthaProduct, db: SQLiteDatabase = writableDatabase) { db.update("zentha_products", item.zenthaProductValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteZenthaProduct(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("zentha_products", "id=?", arrayOf(id.toString())) }

    fun getZenthaOrders(db: SQLiteDatabase = readableDatabase): List<ZenthaOrder> = db.query("zentha_orders", null, null, null, null, null, "order_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.zenthaOrder()) } }
    fun getZenthaOrderById(id: Long, db: SQLiteDatabase = readableDatabase): ZenthaOrder? = db.query("zentha_orders", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.zenthaOrder() else null }
    fun insertZenthaOrder(item: ZenthaOrder, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("zentha_orders", null, item.zenthaOrderValues(false))
    fun upsertCloudZenthaOrder(item: ZenthaOrder, db: SQLiteDatabase = writableDatabase): Long = db.insertWithOnConflict("zentha_orders", null, item.zenthaOrderValues(true), SQLiteDatabase.CONFLICT_REPLACE)
    fun updateZenthaOrder(item: ZenthaOrder, db: SQLiteDatabase = writableDatabase) { db.update("zentha_orders", item.zenthaOrderValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteZenthaOrder(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("zentha_orders", "id=?", arrayOf(id.toString())) }

    fun getZenthaItems(db: SQLiteDatabase = readableDatabase): List<ZenthaOrderItem> = db.query("zentha_order_items", null, null, null, null, null, "order_id, id").use { c -> buildList { while (c.moveToNext()) add(c.zenthaItem()) } }
    fun insertZenthaItem(item: ZenthaOrderItem, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("zentha_order_items", null, item.zenthaItemValues(false))
    fun upsertCloudZenthaItem(item: ZenthaOrderItem, db: SQLiteDatabase = writableDatabase): Long = db.insertWithOnConflict("zentha_order_items", null, item.zenthaItemValues(true), SQLiteDatabase.CONFLICT_REPLACE)
    fun deleteZenthaItems(orderId: Long, db: SQLiteDatabase = writableDatabase) { db.delete("zentha_order_items", "order_id=?", arrayOf(orderId.toString())) }

    fun getZenthaExtraCosts(db: SQLiteDatabase = readableDatabase): List<ZenthaExtraCost> = db.query("zentha_extra_costs", null, null, null, null, null, "order_id, id").use { c -> buildList { while (c.moveToNext()) add(c.zenthaExtraCost()) } }
    fun insertZenthaExtraCost(item: ZenthaExtraCost, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("zentha_extra_costs", null, item.zenthaExtraCostValues(false))
    fun deleteZenthaExtraCosts(orderId: Long, db: SQLiteDatabase = writableDatabase) { db.delete("zentha_extra_costs", "order_id=?", arrayOf(orderId.toString())) }

    fun getZenthaPayments(db: SQLiteDatabase = readableDatabase): List<ZenthaPayment> = db.query("zentha_payments", null, null, null, null, null, "payment_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.zenthaPayment()) } }
    fun insertZenthaPayment(item: ZenthaPayment, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("zentha_payments", null, item.zenthaPaymentValues(false))
    fun deleteZenthaPayment(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("zentha_payments", "id=?", arrayOf(id.toString())) }

    fun getEconomicWorks(db: SQLiteDatabase = readableDatabase): List<EconomicWork> =
        db.query("economic_works", null, null, null, null, null, "work_date DESC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.economicWork()) } }
    fun getEconomicWorkById(id: Long, db: SQLiteDatabase = readableDatabase): EconomicWork? =
        db.query("economic_works", null, "id=?", arrayOf(id.toString()), null, null, null).use { c -> if (c.moveToFirst()) c.economicWork() else null }
    fun insertEconomicWork(item: EconomicWork, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("economic_works", null, item.economicWorkValues(false))
    fun updateEconomicWork(item: EconomicWork, db: SQLiteDatabase = writableDatabase) { db.update("economic_works", item.economicWorkValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteEconomicWork(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("economic_works", "id=?", arrayOf(id.toString())) }

    fun getOperationalTasks(db: SQLiteDatabase = readableDatabase): List<OperationalTask> =
        db.query("operational_tasks", null, null, null, null, null, "CASE status WHEN 'DONE' THEN 1 WHEN 'CANCELLED' THEN 1 ELSE 0 END, due_date ASC, id DESC").use { c -> buildList { while (c.moveToNext()) add(c.operationalTask()) } }
    fun insertOperationalTask(item: OperationalTask, db: SQLiteDatabase = writableDatabase): Long = db.insertOrThrow("operational_tasks", null, item.operationalTaskValues(false))
    fun updateOperationalTask(item: OperationalTask, db: SQLiteDatabase = writableDatabase) { db.update("operational_tasks", item.operationalTaskValues(false), "id=?", arrayOf(item.id.toString())) }
    fun deleteOperationalTask(id: Long, db: SQLiteDatabase = writableDatabase) { db.delete("operational_tasks", "id=?", arrayOf(id.toString())) }

    fun getSettings(db: SQLiteDatabase = readableDatabase): AppSettings = db.query("settings", null, "id=1", null, null, null, null).use { c ->
        if (!c.moveToFirst()) return AppSettings()
        AppSettings(c.double("daily_fixed_cost"), c.double("plotter_x1_cost"), c.double("plotter_x2_cost"), c.double("protection_cost"), c.string("theme_key"), c.optionalDouble("zentha_split_a", 50.0), c.optionalDouble("zentha_split_b", 50.0), c.optionalDouble("chromia_rate_m", 2.5))
    }
    fun saveSettings(settings: AppSettings, db: SQLiteDatabase = writableDatabase) { db.update("settings", ContentValues().apply { put("daily_fixed_cost", settings.dailyFixedCost); put("plotter_x1_cost", settings.plotterX1Cost); put("plotter_x2_cost", settings.plotterX2Cost); put("protection_cost", settings.protectionPaperCost); put("theme_key", settings.themeKey); put("zentha_split_a", settings.zenthaSplitA); put("zentha_split_b", settings.zenthaSplitB); put("chromia_rate_m", settings.chromiaRatePerMeter) }, "id=1", null) }

    fun getLocalSyncState(db: SQLiteDatabase = readableDatabase): LocalSyncState {
        // Defensive self-heal for databases produced by the affected schema-7 build.
        createV6Tables(db)
        return db.query("cloud_sync_state", null, "id=1", null, null, null, null).use { c ->
            if (!c.moveToFirst()) return LocalSyncState()
            LocalSyncState(
                organizationId = c.string("organization_id"),
                deviceId = c.string("device_id"),
                baseRevision = c.long("base_revision"),
                lastSeenRemoteRevision = c.long("last_seen_remote_revision"),
                lastSyncedAt = c.string("last_synced_at"),
                lastError = c.string("last_error"),
                recoveryBackupPath = c.string("recovery_backup_path"),
                pendingChanges = c.optionalInt("pending_changes", 0) == 1
            )
        }
    }

    fun saveLocalSyncState(state: LocalSyncState, db: SQLiteDatabase = writableDatabase) {
        createV6Tables(db)
        db.insertWithOnConflict("cloud_sync_state", null, ContentValues().apply {
            put("id", 1)
            put("organization_id", state.organizationId)
            put("device_id", state.deviceId)
            put("base_revision", state.baseRevision)
            put("last_seen_remote_revision", state.lastSeenRemoteRevision)
            put("last_synced_at", state.lastSyncedAt)
            put("last_error", state.lastError)
            put("recovery_backup_path", state.recoveryBackupPath)
            put("pending_changes", if (state.pendingChanges) 1 else 0)
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun clearAll(db: SQLiteDatabase = writableDatabase, restoreDefaultCatalog: Boolean = true) {
        listOf("operational_tasks", "economic_works", "zentha_payments", "zentha_extra_costs", "zentha_order_items", "zentha_orders", "zentha_products", "cotton_orders", "order_timeline", "roll_usages", "stock_movements", "fabric_rolls", "issue_updates", "client_issues", "payments", "production", "work_orders", "inventory", "clients").forEach { db.delete(it, null, null) }
        saveSettings(AppSettings(), db)
        if (restoreDefaultCatalog) seedZenthaCatalog(db)
    }

    fun replaceAll(snapshot: SpectroSnapshot) {
        writableDatabase.runInTransaction {
            clearAll(this, restoreDefaultCatalog = false)
            snapshot.clients.forEach { c -> insertOrThrow("clients", null, ContentValues().apply { put("id", c.id); put("name", c.name); put("default_print_price", c.defaultPrintPrice) }) }
            snapshot.inventory.forEach { insertOrThrow("inventory", null, it.inventoryValues(true)) }
            snapshot.production.forEach { insertOrThrow("production", null, it.productionValues(true)) }
            snapshot.workOrders.forEach { insertOrThrow("work_orders", null, it.workValues(true)) }
            snapshot.clientIssues.forEach { insertOrThrow("client_issues", null, it.issueValues(true)) }
            snapshot.issueUpdates.forEach { insertOrThrow("issue_updates", null, it.issueUpdateValues(true)) }
            snapshot.payments.forEach { insertOrThrow("payments", null, it.paymentValues(true)) }
            snapshot.fabricRolls.forEach { insertOrThrow("fabric_rolls", null, it.fabricRollValues(true)) }
            snapshot.stockMovements.forEach { insertOrThrow("stock_movements", null, it.stockMovementValues(true)) }
            snapshot.rollUsages.forEach { insertOrThrow("roll_usages", null, it.rollUsageValues(true)) }
            snapshot.timelineEvents.forEach { insertOrThrow("order_timeline", null, it.timelineValues(true)) }
            snapshot.cottonOrders.forEach { insertOrThrow("cotton_orders", null, it.cottonValues(true)) }
            snapshot.zenthaProducts.forEach { insertOrThrow("zentha_products", null, it.zenthaProductValues(true)) }
            snapshot.zenthaOrders.forEach { insertOrThrow("zentha_orders", null, it.zenthaOrderValues(true)) }
            snapshot.zenthaItems.forEach { insertOrThrow("zentha_order_items", null, it.zenthaItemValues(true)) }
            snapshot.zenthaExtraCosts.forEach { insertOrThrow("zentha_extra_costs", null, it.zenthaExtraCostValues(true)) }
            snapshot.zenthaPayments.forEach { insertOrThrow("zentha_payments", null, it.zenthaPaymentValues(true)) }
            snapshot.economicWorks.forEach { insertOrThrow("economic_works", null, it.economicWorkValues(true)) }
            snapshot.operationalTasks.forEach { insertOrThrow("operational_tasks", null, it.operationalTaskValues(true)) }
            seedZenthaCatalog(this)
            if (snapshot.fabricRolls.isEmpty() && snapshot.inventory.isNotEmpty()) migrateLegacyInventoryToRolls(this)
            populateMissingOrderCodes(this)
            saveSettings(snapshot.settings, this)
        }
    }

    fun <T> transaction(block: (SQLiteDatabase) -> T): T {
        val db = writableDatabase; db.beginTransaction()
        try { val result = block(db); db.setTransactionSuccessful(); return result } finally { db.endTransaction() }
    }
    private inline fun <T> SQLiteDatabase.runInTransaction(block: SQLiteDatabase.() -> T): T { beginTransaction(); try { val r = block(); setTransactionSuccessful(); return r } finally { endTransaction() } }

    private fun Cursor.client() = Client(long("id"), string("name"), double("default_print_price"))
    private fun Cursor.inventory() = InventoryItem(long("id"), enumValueOrDefault(string("owner_type"), OwnerType.CLIENT), string("owner_name"), string("fabric_name"), double("quantity_m"), double("purchase_price_m"), string("created_date"), string("images_json").toStringList(), optionalDouble("minimum_quantity_m"), optionalString("supplier_name"), optionalString("composition"), optionalString("weight_gsm"), optionalString("variant"), optionalString("lot_code"), optionalDouble("processing_cost_m"), optionalDouble("default_sale_price_m"), optionalInt("archived") == 1)
    private fun Cursor.fabricRoll() = FabricRoll(long("id"), long("inventory_id"), string("roll_code"), double("initial_m"), double("available_m"), string("created_date"), string("note"), enumValueOrDefault(string("status"), RollStatus.OPEN), optionalString("images_json").toStringList())
    private fun Cursor.stockMovement() = StockMovement(long("id"), long("inventory_id"), long("roll_id"), enumValueOrDefault(string("type"), StockMovementType.ADJUSTMENT), string("date_time"), double("meters"), nullableString("order_kind")?.let { enumValueOrDefault(it, OrderKind.WORK) }, nullableLong("order_id"), string("order_ref"), string("note"))
    private fun Cursor.rollUsage() = RollUsage(long("id"), enumValueOrDefault(string("order_kind"), OrderKind.WORK), long("order_id"), string("order_ref"), long("inventory_id"), long("roll_id"), double("meters"), string("created_at"))
    private fun Cursor.production() = ProductionRecord(
        id = long("id"), type = enumValueOrDefault(string("type"), RecordType.PRODUCTION), date = string("date"), clientId = nullableLong("client_id"), clientName = string("client_name"), orderRef = string("order_ref"),
        designs = string("designs_json").toStringList(), printPricePerMeter = double("print_price_m"), plotter = string("plotter"), printedPaperMeters = double("printed_paper_m"), protectionPaperMeters = double("protection_paper_m"), paperWasteMeters = double("paper_waste_m"),
        fabricMode = enumValueOrDefault(string("fabric_mode"), FabricMode.NONE), fabricId = nullableLong("fabric_id"), fabricUsedMeters = double("fabric_used_m"), fabricWasteMeters = double("fabric_waste_m"), fabricSalePricePerMeter = double("fabric_sale_price_m"), manualFabricName = string("manual_fabric_name"),
        fabricDisplayName = string("fabric_display_name"), technicalNotes = string("technical_notes"), images = string("images_json").toStringList(), refundCost = double("refund_cost"), fabricLossCost = double("fabric_loss_cost"), applyFixedCosts = int("apply_fixed_costs") == 1,
        paperPrintCost = double("paper_print_cost"), protectionPaperCost = double("protection_paper_cost"), fabricUsedCost = double("fabric_used_cost"), materialCostTotal = double("material_cost_total"), grossRevenue = double("gross_revenue"), grossProfit = double("gross_profit"), workOrderId = optionalNullableLong("work_order_id"), zenthaOrderId = optionalNullableLong("zentha_order_id")
    )
    private fun Cursor.workOrder() = WorkOrder(long("id"), string("received_date"), string("client"), string("title"), string("description"), string("images_json").toStringList(), enumValueOrDefault(string("status"), WorkStatus.IN_PROCESS), string("due_date"), enumValueOrDefault(string("priority"), Priority.NORMAL), enumValueOrDefault(string("blocker"), Blocker.NONE), optionalNullableLong("client_id"), optionalString("order_code"), optionalNullableLong("fabric_inventory_id"), optionalDouble("ordered_m"), optionalDouble("print_price_m"), optionalString("design_code"), optionalNullableLong("production_record_id"), optionalString("effective_delivery_date"), optionalString("cancel_reason"), optionalString("material_received_date"), optionalString("material_receipt_notes"))
    private fun Cursor.timelineEvent() = OrderTimelineEvent(long("id"), long("work_order_id"), nullableLong("client_id"), string("client_name"), string("order_ref"), string("event_date"), string("event_time"), enumValueOrDefault(string("category"), TimelineCategory.NOTE), enumValueOrDefault(string("responsibility"), TimelineResponsibility.NONE), enumValueOrDefault(string("severity"), TimelineSeverity.INFO), enumValueOrDefault(string("status"), TimelineStatus.OPEN), string("title"), string("description"), string("old_due_date"), string("new_due_date"), string("images_json").toStringList(), string("resolution_note"), string("resolved_date"))
    private fun Cursor.clientIssue() = ClientIssue(long("id"), nullableLong("client_id"), string("client_name"), string("order_ref"), string("title"), enumValueOrDefault(string("category"), IssueCategory.OTHER), enumValueOrDefault(string("responsibility"), IssueResponsibility.TO_VERIFY), enumValueOrDefault(string("severity"), IssueSeverity.MEDIUM), enumValueOrDefault(string("status"), IssueStatus.OPEN), string("opened_date"), string("expected_delivery_date"), string("revised_delivery_date"), int("delay_days"), string("description"), string("internal_notes"), string("evidence_images_json").toStringList())
    private fun Cursor.issueUpdate() = IssueUpdate(long("id"), long("issue_id"), string("date"), string("note"), string("images_json").toStringList())
    private fun Cursor.payment() = PaymentDeadline(long("id"), nullableLong("client_id"), string("client_name"), string("order_ref"), double("amount"), string("due_date"), int("paid") == 1, string("paid_date"), enumValueOrDefault(string("method"), PaymentMethod.UNSPECIFIED), string("notes"))
    private fun Cursor.cottonOrder() = CottonOrder(long("id"), string("order_code"), nullableLong("client_id"), string("client_name"), string("order_date"), string("due_date"), string("fabric_name"), enumValueOrDefault(string("print_type"), CottonPrintType.REACTIVE), double("ordered_m"), double("actual_produced_m"), double("delivered_m"), double("defective_m"), double("sample_m"), double("leftover_m"), double("production_cost_m"), double("print_price_m"), enumValueOrDefault(string("fabric_owner_type"), OwnerType.CLIENT), nullableLong("inventory_id"), double("fabric_cost_m"), string("notes"), enumValueOrDefault(string("status"), WorkStatus.IN_PROCESS), optionalString("title"), optionalString("images_json").toStringList())
    private fun Cursor.zenthaProduct() = ZenthaProduct(
        id = long("id"), name = string("name"), sku = string("sku"), category = string("category"),
        standardCost = double("standard_cost"), standardPrice = double("standard_price"), description = string("description"),
        imagePath = string("image_path"), active = int("active") == 1, fabricCost = optionalDouble("fabric_cost"),
        ribCost = optionalDouble("rib_cost"), liningCost = optionalDouble("lining_cost"), printCost = optionalDouble("print_cost"),
        accessoryCost = optionalDouble("accessory_cost"), cuttingCost = optionalDouble("cutting_cost"), sewingCost = optionalDouble("sewing_cost"),
        labelCost = optionalDouble("label_cost"), packagingCost = optionalDouble("packaging_cost"), otherCost = optionalDouble("other_cost"),
        priceListName = optionalString("price_list_name").ifBlank { "Listino standard" }, markupPercent = optionalDouble("markup_percent"),
        sourceReference = optionalString("source_reference")
    )
    private fun Cursor.zenthaOrder() = ZenthaOrder(long("id"), string("order_code"), nullableLong("client_id"), string("client_name"), string("order_date"), string("due_date"), enumValueOrDefault(string("status"), ZenthaOrderStatus.CONFIRMED), string("notes"), string("attachments_json").toStringList(), double("split_a"), double("split_b"), optionalDouble("chromia_rate_m", 2.5))
    private fun Cursor.zenthaItem() = ZenthaOrderItem(
        id = long("id"), orderId = long("order_id"), productId = nullableLong("product_id"), productName = string("product_name"),
        quantity = int("quantity"), unitPrice = double("unit_price"), unitCost = double("unit_cost"),
        skuSnapshot = optionalString("sku_snapshot"), productDescriptionSnapshot = optionalString("product_description_snapshot"),
        variant = optionalString("variant"), color = optionalString("color"), sizeBreakdown = optionalString("size_breakdown"),
        personalization = optionalString("personalization"), productionNotes = optionalString("production_notes")
    )
    private fun Cursor.zenthaExtraCost() = ZenthaExtraCost(long("id"), long("order_id"), string("label"), double("amount"))
    private fun Cursor.zenthaPayment() = ZenthaPayment(long("id"), long("order_id"), string("payment_date"), double("amount"), enumValueOrDefault(string("method"), PaymentMethod.UNSPECIFIED), string("notes"))
    private fun Cursor.economicWork() = EconomicWork(
        id = long("id"), date = string("work_date"), clientId = optionalNullableLong("client_id"), clientName = string("client_name"),
        title = string("title"), quantity = double("quantity"), unitPrice = double("unit_price"), manualGrossRevenue = double("manual_gross"),
        materials = string("materials_json").toEconomicMaterials(), otherCosts = double("other_costs"), notes = string("notes"),
        images = string("images_json").toStringList(), status = enumValueOrDefault(string("status"), EconomicWorkStatus.OPEN)
    )
    private fun Cursor.operationalTask() = OperationalTask(
        id = long("id"), createdDate = string("created_date"), dueDate = string("due_date"), clientId = optionalNullableLong("client_id"),
        clientName = string("client_name"), workOrderId = optionalNullableLong("work_order_id"), title = string("title"),
        type = enumValueOrDefault(string("type"), OperationalTaskType.OTHER), description = string("description"),
        priority = enumValueOrDefault(string("priority"), Priority.NORMAL), status = enumValueOrDefault(string("status"), OperationalTaskStatus.TODO),
        images = string("images_json").toStringList()
    )

    private fun InventoryItem.inventoryValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("owner_type", ownerType.name); put("owner_name", ownerName); put("fabric_name", fabricName); put("quantity_m", quantityMeters); put("purchase_price_m", purchasePricePerMeter); put("created_date", createdDate); put("images_json", images.toJsonArrayString()); put("minimum_quantity_m", minimumQuantityMeters); put("supplier_name", supplierName); put("composition", composition); put("weight_gsm", weightGsm); put("variant", variant); put("lot_code", lotCode); put("processing_cost_m", processingCostPerMeter); put("default_sale_price_m", defaultSalePricePerMeter); put("archived", if (archived) 1 else 0) }
    private fun FabricRoll.fabricRollValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("inventory_id", inventoryId); put("roll_code", rollCode); put("initial_m", initialMeters); put("available_m", availableMeters); put("created_date", createdDate); put("note", note); put("status", status.name); put("images_json", images.toJsonArrayString()) }
    private fun StockMovement.stockMovementValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("inventory_id", inventoryId); put("roll_id", rollId); put("type", type.name); put("date_time", dateTime); put("meters", meters); if (orderKind == null) putNull("order_kind") else put("order_kind", orderKind.name); if (orderId == null) putNull("order_id") else put("order_id", orderId); put("order_ref", orderRef); put("note", note) }
    private fun RollUsage.rollUsageValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("order_kind", orderKind.name); put("order_id", orderId); put("order_ref", orderRef); put("inventory_id", inventoryId); put("roll_id", rollId); put("meters", meters); put("created_at", createdAt) }
    private fun ProductionRecord.productionValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id); put("type", type.name); put("date", date); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("designs_json", designs.toJsonArrayString()); put("print_price_m", printPricePerMeter)
        put("plotter", plotter); put("printed_paper_m", printedPaperMeters); put("protection_paper_m", protectionPaperMeters); put("paper_waste_m", paperWasteMeters); put("fabric_mode", fabricMode.name); if (fabricId == null) putNull("fabric_id") else put("fabric_id", fabricId)
        put("fabric_used_m", fabricUsedMeters); put("fabric_waste_m", fabricWasteMeters); put("fabric_sale_price_m", fabricSalePricePerMeter); put("manual_fabric_name", manualFabricName); put("fabric_display_name", fabricDisplayName); put("technical_notes", technicalNotes); put("images_json", images.toJsonArrayString())
        put("refund_cost", refundCost); put("fabric_loss_cost", fabricLossCost); put("apply_fixed_costs", if (applyFixedCosts) 1 else 0); put("paper_print_cost", paperPrintCost); put("protection_paper_cost", protectionPaperCost); put("fabric_used_cost", fabricUsedCost); put("material_cost_total", materialCostTotal); put("gross_revenue", grossRevenue); put("gross_profit", grossProfit); if (workOrderId == null) putNull("work_order_id") else put("work_order_id", workOrderId); if (zenthaOrderId == null) putNull("zentha_order_id") else put("zentha_order_id", zenthaOrderId)
    }
    private fun WorkOrder.workValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("received_date", receivedDate); put("client", client); put("title", title); put("description", description); put("images_json", images.toJsonArrayString()); put("status", status.name); put("due_date", dueDate); put("priority", priority.name); put("blocker", blocker.name); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("order_code", orderCode); if (fabricInventoryId == null) putNull("fabric_inventory_id") else put("fabric_inventory_id", fabricInventoryId); put("ordered_m", orderedMeters); put("print_price_m", printPricePerMeter); put("design_code", designCode); if (productionRecordId == null) putNull("production_record_id") else put("production_record_id", productionRecordId); put("effective_delivery_date", effectiveDeliveryDate); put("cancel_reason", cancelReason); put("material_received_date", materialReceivedDate); put("material_receipt_notes", materialReceiptNotes) }
    private fun OrderTimelineEvent.timelineValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("work_order_id", workOrderId); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("event_date", date); put("event_time", time); put("category", category.name); put("responsibility", responsibility.name); put("severity", severity.name); put("status", status.name); put("title", title); put("description", description); put("old_due_date", oldDueDate); put("new_due_date", newDueDate); put("images_json", images.toJsonArrayString()); put("resolution_note", resolutionNote); put("resolved_date", resolvedDate) }
    private fun ClientIssue.issueValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("title", title); put("category", category.name); put("responsibility", responsibility.name); put("severity", severity.name); put("status", status.name); put("opened_date", openedDate); put("expected_delivery_date", expectedDeliveryDate); put("revised_delivery_date", revisedDeliveryDate); put("delay_days", delayDays); put("description", description); put("internal_notes", internalNotes); put("evidence_images_json", evidenceImages.toJsonArrayString()) }
    private fun IssueUpdate.issueUpdateValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("issue_id", issueId); put("date", date); put("note", note); put("images_json", images.toJsonArrayString()) }
    private fun PaymentDeadline.paymentValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_ref", orderRef); put("amount", amount); put("due_date", dueDate); put("paid", if (paid) 1 else 0); put("paid_date", paidDate); put("method", method.name); put("notes", notes) }
    private fun CottonOrder.cottonValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("order_code", orderCode); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_date", orderDate); put("due_date", dueDate); put("fabric_name", fabricName); put("print_type", printType.name); put("ordered_m", orderedMeters); put("actual_produced_m", actualProducedMeters); put("delivered_m", deliveredMeters); put("defective_m", defectiveMeters); put("sample_m", sampleMeters); put("leftover_m", leftoverMeters); put("production_cost_m", productionCostPerMeter); put("print_price_m", printPricePerMeter); put("fabric_owner_type", fabricOwnerType.name); if (inventoryId == null) putNull("inventory_id") else put("inventory_id", inventoryId); put("fabric_cost_m", fabricCostPerMeter); put("notes", notes); put("status", status.name); put("title", title); put("images_json", images.toJsonArrayString()) }
    private fun ZenthaProduct.zenthaProductValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id); put("name", name); put("sku", sku); put("category", category); put("standard_cost", standardCost); put("standard_price", standardPrice)
        put("description", description); put("image_path", imagePath); put("active", if (active) 1 else 0); put("fabric_cost", fabricCost); put("rib_cost", ribCost)
        put("lining_cost", liningCost); put("print_cost", printCost); put("accessory_cost", accessoryCost); put("cutting_cost", cuttingCost); put("sewing_cost", sewingCost)
        put("label_cost", labelCost); put("packaging_cost", packagingCost); put("other_cost", otherCost); put("price_list_name", priceListName); put("markup_percent", markupPercent); put("source_reference", sourceReference)
    }
    private fun ZenthaOrder.zenthaOrderValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("order_code", orderCode); if (clientId == null) putNull("client_id") else put("client_id", clientId); put("client_name", clientName); put("order_date", orderDate); put("due_date", dueDate); put("status", status.name); put("notes", notes); put("attachments_json", attachments.toJsonArrayString()); put("split_a", splitPercentA); put("split_b", splitPercentB); put("chromia_rate_m", chromiaRatePerMeter) }
    private fun ZenthaOrderItem.zenthaItemValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id); put("order_id", orderId); if (productId == null) putNull("product_id") else put("product_id", productId)
        put("product_name", productName); put("quantity", quantity); put("unit_price", unitPrice); put("unit_cost", unitCost); put("sku_snapshot", skuSnapshot)
        put("product_description_snapshot", productDescriptionSnapshot); put("variant", variant); put("color", color); put("size_breakdown", sizeBreakdown)
        put("personalization", personalization); put("production_notes", productionNotes)
    }
    private fun ZenthaExtraCost.zenthaExtraCostValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("order_id", orderId); put("label", label); put("amount", amount) }
    private fun ZenthaPayment.zenthaPaymentValues(includeId: Boolean) = ContentValues().apply { if (includeId) put("id", id); put("order_id", orderId); put("payment_date", date); put("amount", amount); put("method", method.name); put("notes", notes) }
    private fun EconomicWork.economicWorkValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id); put("work_date", date); if (clientId == null) putNull("client_id") else put("client_id", clientId)
        put("client_name", clientName); put("title", title); put("quantity", quantity); put("unit_price", unitPrice); put("manual_gross", manualGrossRevenue)
        put("materials_json", materials.toEconomicMaterialsJson()); put("other_costs", otherCosts); put("notes", notes); put("images_json", images.toJsonArrayString()); put("status", status.name)
    }
    private fun OperationalTask.operationalTaskValues(includeId: Boolean) = ContentValues().apply {
        if (includeId) put("id", id); put("created_date", createdDate); put("due_date", dueDate); if (clientId == null) putNull("client_id") else put("client_id", clientId)
        put("client_name", clientName); if (workOrderId == null) putNull("work_order_id") else put("work_order_id", workOrderId); put("title", title)
        put("type", type.name); put("description", description); put("priority", priority.name); put("status", status.name); put("images_json", images.toJsonArrayString())
    }

    private fun Cursor.string(name: String) = getString(getColumnIndexOrThrow(name)) ?: ""
    private fun Cursor.optionalString(name: String): String { val i = getColumnIndex(name); return if (i < 0 || isNull(i)) "" else getString(i) ?: "" }
    private fun Cursor.nullableString(name: String): String? { val i = getColumnIndex(name); return if (i < 0 || isNull(i)) null else getString(i) }
    private fun Cursor.double(name: String) = getDouble(getColumnIndexOrThrow(name))
    private fun Cursor.optionalDouble(name: String, default: Double = 0.0): Double { val i = getColumnIndex(name); return if (i < 0 || isNull(i)) default else getDouble(i) }
    private fun Cursor.long(name: String) = getLong(getColumnIndexOrThrow(name))
    private fun Cursor.int(name: String) = getInt(getColumnIndexOrThrow(name))
    private fun Cursor.optionalInt(name: String, default: Int = 0): Int { val i = getColumnIndex(name); return if (i < 0 || isNull(i)) default else getInt(i) }
    private fun Cursor.nullableLong(name: String): Long? { val i = getColumnIndexOrThrow(name); return if (isNull(i)) null else getLong(i) }
    private fun Cursor.optionalNullableLong(name: String): Long? { val i = getColumnIndex(name); return if (i < 0 || isNull(i)) null else getLong(i) }
    private inline fun <reified T : Enum<T>> enumValueOrDefault(value: String, default: T): T = enumValues<T>().firstOrNull { it.name == value } ?: default
}
