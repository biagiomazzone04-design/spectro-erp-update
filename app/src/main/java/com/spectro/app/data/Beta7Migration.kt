package com.spectro.app.data

import android.database.sqlite.SQLiteDatabase

/** Additive, repeatable schema 5 -> 6 migration. Never guesses variants or historical stock. */
object Beta7Migration {
    fun apply(db: SQLiteDatabase) {
        addColumn(db, "zentha_products", "line", "TEXT NOT NULL DEFAULT ''")
        addColumn(db, "zentha_products", "printing_cost", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_products", "sewing_cost", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_products", "accessories_cost", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_products", "packaging_cost", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_products", "other_cost", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_orders", "contact", "TEXT NOT NULL DEFAULT ''")
        addColumn(db, "zentha_orders", "discount", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_orders", "shipping_revenue", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_orders", "other_revenue", "REAL NOT NULL DEFAULT 0")
        addColumn(db, "zentha_orders", "stock_posted", "INTEGER NOT NULL DEFAULT 0")
        addColumn(db, "zentha_order_items", "variant_id", "INTEGER")
        addColumn(db, "zentha_order_items", "variant_label", "TEXT NOT NULL DEFAULT ''")
        addColumn(db, "zentha_order_items", "size_snapshot", "TEXT NOT NULL DEFAULT ''")
        addColumn(db, "zentha_order_items", "sku_snapshot", "TEXT NOT NULL DEFAULT ''")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_variants(id INTEGER PRIMARY KEY AUTOINCREMENT, productId INTEGER NOT NULL, sku TEXT NOT NULL, size TEXT NOT NULL, color TEXT NOT NULL, fit TEXT NOT NULL, variantType TEXT NOT NULL, active INTEGER NOT NULL CHECK(active IN (0,1)))")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_balances(id INTEGER PRIMARY KEY AUTOINCREMENT, variantId INTEGER NOT NULL UNIQUE, quantityAvailable INTEGER NOT NULL CHECK(quantityAvailable>=0), minimumQuantity INTEGER NOT NULL CHECK(minimumQuantity>=0), location TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_movements(id INTEGER PRIMARY KEY AUTOINCREMENT, variantId INTEGER NOT NULL, type TEXT NOT NULL, quantity INTEGER NOT NULL, dateTime TEXT NOT NULL, orderId INTEGER, note TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_priceLists(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, description TEXT NOT NULL, active INTEGER NOT NULL CHECK(active IN (0,1)))")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_priceRules(id INTEGER PRIMARY KEY AUTOINCREMENT, priceListId INTEGER, productId INTEGER NOT NULL, variantId INTEGER, minQty INTEGER NOT NULL, maxQty INTEGER, unitPrice REAL NOT NULL, clientId INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_clientProfiles(id INTEGER PRIMARY KEY AUTOINCREMENT, clientId INTEGER NOT NULL UNIQUE, assignedPriceListId INTEGER, notes TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_roster(id INTEGER PRIMARY KEY AUTOINCREMENT, orderId INTEGER NOT NULL, surname TEXT NOT NULL, name TEXT NOT NULL, number TEXT NOT NULL, shirtSize TEXT NOT NULL, shortsSize TEXT NOT NULL, notes TEXT NOT NULL, productId INTEGER)")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_customizations(id INTEGER PRIMARY KEY AUTOINCREMENT, orderId INTEGER NOT NULL, orderItemId INTEGER, type TEXT NOT NULL, value TEXT NOT NULL, notes TEXT NOT NULL, approved INTEGER NOT NULL CHECK(approved IN (0,1)), attachments TEXT NOT NULL)")
        db.execSQL("CREATE TABLE IF NOT EXISTS zentha_events(id INTEGER PRIMARY KEY AUTOINCREMENT, orderId INTEGER NOT NULL, dateTime TEXT NOT NULL, fromStatus TEXT NOT NULL, toStatus TEXT NOT NULL, note TEXT NOT NULL)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_z_variant_product ON zentha_variants(productId)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_z_variant_sku ON zentha_variants(sku COLLATE NOCASE) WHERE sku <> ''")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_z_moves_order ON zentha_movements(orderId, variantId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_z_roster_order ON zentha_roster(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_z_custom_order ON zentha_customizations(orderId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_z_events_order ON zentha_events(orderId, dateTime)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_z_rule_product ON zentha_priceRules(productId, clientId, priceListId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_production_order ON production(work_order_id)")
        // Seed list names only, with no invented prices, variants, quantities or workflow timestamps.
        listOf("Standard", "Società Sportive", "Rivenditori", "VIP").forEach { name ->
            db.execSQL("INSERT INTO zentha_priceLists(name, description, active) SELECT ?, '', 1 WHERE NOT EXISTS (SELECT 1 FROM zentha_priceLists WHERE name=?)", arrayOf(name, name))
        }
    }
    private fun addColumn(db: SQLiteDatabase, table: String, column: String, definition: String) {
        val exists = db.rawQuery("PRAGMA table_info($table)", null).use { c ->
            var found = false
            while (c.moveToNext()) if (c.getString(c.getColumnIndexOrThrow("name")) == column) found = true
            found
        }
        if (!exists) db.execSQL("ALTER TABLE $table ADD COLUMN $column $definition")
    }
}
