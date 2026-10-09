package com.spectro.app.data

import android.content.ContentValues
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import org.json.JSONObject

object ZenthaStore {
    val tables = listOf("zentha_variants", "zentha_balances", "zentha_movements", "zentha_priceLists", "zentha_priceRules", "zentha_clientProfiles", "zentha_roster", "zentha_customizations", "zentha_events")
    fun variants(db: SQLiteDatabase): List<ZenthaProductVariant> = read(db, "zentha_variants", ZenthaCodec::variants)
    fun save(db: SQLiteDatabase, value: ZenthaProductVariant): Long = save(db, "zentha_variants", ZenthaCodec.encode(value))
    fun balances(db: SQLiteDatabase): List<ZenthaStockBalance> = read(db, "zentha_balances", ZenthaCodec::balances)
    fun save(db: SQLiteDatabase, value: ZenthaStockBalance): Long = save(db, "zentha_balances", ZenthaCodec.encode(value))
    fun movements(db: SQLiteDatabase): List<ZenthaStockMovement> = read(db, "zentha_movements", ZenthaCodec::movements)
    fun save(db: SQLiteDatabase, value: ZenthaStockMovement): Long = save(db, "zentha_movements", ZenthaCodec.encode(value))
    fun priceLists(db: SQLiteDatabase): List<ZenthaPriceList> = read(db, "zentha_priceLists", ZenthaCodec::priceLists)
    fun save(db: SQLiteDatabase, value: ZenthaPriceList): Long = save(db, "zentha_priceLists", ZenthaCodec.encode(value))
    fun priceRules(db: SQLiteDatabase): List<ZenthaPriceListRule> = read(db, "zentha_priceRules", ZenthaCodec::priceRules)
    fun save(db: SQLiteDatabase, value: ZenthaPriceListRule): Long = save(db, "zentha_priceRules", ZenthaCodec.encode(value))
    fun clientProfiles(db: SQLiteDatabase): List<ZenthaClientProfile> = read(db, "zentha_clientProfiles", ZenthaCodec::clientProfiles)
    fun save(db: SQLiteDatabase, value: ZenthaClientProfile): Long = save(db, "zentha_clientProfiles", ZenthaCodec.encode(value))
    fun roster(db: SQLiteDatabase): List<ZenthaRosterEntry> = read(db, "zentha_roster", ZenthaCodec::roster)
    fun save(db: SQLiteDatabase, value: ZenthaRosterEntry): Long = save(db, "zentha_roster", ZenthaCodec.encode(value))
    fun customizations(db: SQLiteDatabase): List<ZenthaCustomization> = read(db, "zentha_customizations", ZenthaCodec::customizations)
    fun save(db: SQLiteDatabase, value: ZenthaCustomization): Long = save(db, "zentha_customizations", ZenthaCodec.encode(value))
    fun events(db: SQLiteDatabase): List<ZenthaOrderEvent> = read(db, "zentha_events", ZenthaCodec::events)
    fun save(db: SQLiteDatabase, value: ZenthaOrderEvent): Long = save(db, "zentha_events", ZenthaCodec.encode(value))
    fun snapshot(db: SQLiteDatabase) = ZenthaData(variants = variants(db), balances = balances(db), movements = movements(db), priceLists = priceLists(db), priceRules = priceRules(db), clientProfiles = clientProfiles(db), roster = roster(db), customizations = customizations(db), events = events(db))
    fun restore(db: SQLiteDatabase, data: ZenthaData) {
        data.variants.forEach { insert(db, "zentha_variants", ZenthaCodec.encode(it)) }
        data.balances.forEach { insert(db, "zentha_balances", ZenthaCodec.encode(it)) }
        data.movements.forEach { insert(db, "zentha_movements", ZenthaCodec.encode(it)) }
        data.priceLists.forEach { insert(db, "zentha_priceLists", ZenthaCodec.encode(it)) }
        data.priceRules.forEach { insert(db, "zentha_priceRules", ZenthaCodec.encode(it)) }
        data.clientProfiles.forEach { insert(db, "zentha_clientProfiles", ZenthaCodec.encode(it)) }
        data.roster.forEach { insert(db, "zentha_roster", ZenthaCodec.encode(it)) }
        data.customizations.forEach { insert(db, "zentha_customizations", ZenthaCodec.encode(it)) }
        data.events.forEach { insert(db, "zentha_events", ZenthaCodec.encode(it)) }
    }
    private fun <T> read(db: SQLiteDatabase, table: String, decode: (JSONObject) -> T): List<T> =
        db.query(table, null, null, null, null, null, "id").use { c -> buildList {
            while (c.moveToNext()) {
                val json = JSONObject()
                c.columnNames.forEachIndexed { i, name -> json.put(name, when(c.getType(i)) {
                    Cursor.FIELD_TYPE_NULL -> JSONObject.NULL
                    Cursor.FIELD_TYPE_INTEGER -> if (name in setOf("active", "approved")) c.getInt(i) == 1 else c.getLong(i)
                    Cursor.FIELD_TYPE_FLOAT -> c.getDouble(i)
                    else -> if (name == "attachments") org.json.JSONArray(c.getString(i)) else c.getString(i)
                }) }
                add(decode(json))
            }
        } }
    private fun values(json: JSONObject) = ContentValues().apply {
        json.keys().forEach { key -> when(val v = json.get(key)) {
            JSONObject.NULL -> putNull(key)
            is Boolean -> put(key, if (v) 1 else 0)
            is Int -> put(key, v)
            is Long -> put(key, v)
            is Double -> put(key, v)
            else -> put(key, v.toString())
        } }
    }
    private fun insert(db: SQLiteDatabase, table: String, json: JSONObject): Long = db.insertOrThrow(table, null, values(json))
    private fun save(db: SQLiteDatabase, table: String, json: JSONObject): Long {
        val id = json.getLong("id")
        val values = values(json).apply { remove("id") }
        return if (id == 0L) db.insertOrThrow(table, null, values) else {
            require(db.update(table, values, "id=?", arrayOf(id.toString())) == 1) { "Elemento non più disponibile." }
            id
        }
    }
}
