package com.spectro.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Shared explicit codec for new SQLite rows and portable backup v8. */
object ZenthaCodec {
    fun encode(v: ZenthaProductVariant): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("productId", v.productId)
        put("sku", v.sku)
        put("size", v.size)
        put("color", v.color)
        put("fit", v.fit)
        put("variantType", v.variantType)
        put("active", v.active)
    }
    fun variants(o: JSONObject) = ZenthaProductVariant(
        id = o.getLong("id"),
        productId = o.getLong("productId"),
        sku = o.getString("sku"),
        size = o.getString("size"),
        color = o.getString("color"),
        fit = o.getString("fit"),
        variantType = o.getString("variantType"),
        active = o.getBoolean("active")
    )
    fun encode(v: ZenthaStockBalance): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("variantId", v.variantId)
        put("quantityAvailable", v.quantityAvailable)
        put("minimumQuantity", v.minimumQuantity)
        put("location", v.location)
    }
    fun balances(o: JSONObject) = ZenthaStockBalance(
        id = o.getLong("id"),
        variantId = o.getLong("variantId"),
        quantityAvailable = o.getInt("quantityAvailable"),
        minimumQuantity = o.getInt("minimumQuantity"),
        location = o.getString("location")
    )
    fun encode(v: ZenthaStockMovement): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("variantId", v.variantId)
        put("type", v.type.name)
        put("quantity", v.quantity)
        put("dateTime", v.dateTime)
        put("orderId", v.orderId ?: JSONObject.NULL)
        put("note", v.note)
    }
    fun movements(o: JSONObject) = ZenthaStockMovement(
        id = o.getLong("id"),
        variantId = o.getLong("variantId"),
        type = ZenthaMovementType.valueOf(o.getString("type")),
        quantity = o.getInt("quantity"),
        dateTime = o.getString("dateTime"),
        orderId = if (!o.has("orderId") || o.isNull("orderId")) null else o.getLong("orderId"),
        note = o.getString("note")
    )
    fun encode(v: ZenthaPriceList): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("name", v.name)
        put("description", v.description)
        put("active", v.active)
    }
    fun priceLists(o: JSONObject) = ZenthaPriceList(
        id = o.getLong("id"),
        name = o.getString("name"),
        description = o.getString("description"),
        active = o.getBoolean("active")
    )
    fun encode(v: ZenthaPriceListRule): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("priceListId", v.priceListId ?: JSONObject.NULL)
        put("productId", v.productId)
        put("variantId", v.variantId ?: JSONObject.NULL)
        put("minQty", v.minQty)
        put("maxQty", v.maxQty ?: JSONObject.NULL)
        put("unitPrice", v.unitPrice)
        put("clientId", v.clientId ?: JSONObject.NULL)
    }
    fun priceRules(o: JSONObject) = ZenthaPriceListRule(
        id = o.getLong("id"),
        priceListId = if (!o.has("priceListId") || o.isNull("priceListId")) null else o.getLong("priceListId"),
        productId = o.getLong("productId"),
        variantId = if (!o.has("variantId") || o.isNull("variantId")) null else o.getLong("variantId"),
        minQty = o.getInt("minQty"),
        maxQty = if (!o.has("maxQty") || o.isNull("maxQty")) null else o.getInt("maxQty"),
        unitPrice = o.getDouble("unitPrice"),
        clientId = if (!o.has("clientId") || o.isNull("clientId")) null else o.getLong("clientId")
    )
    fun encode(v: ZenthaClientProfile): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("clientId", v.clientId)
        put("assignedPriceListId", v.assignedPriceListId ?: JSONObject.NULL)
        put("notes", v.notes)
    }
    fun clientProfiles(o: JSONObject) = ZenthaClientProfile(
        id = o.getLong("id"),
        clientId = o.getLong("clientId"),
        assignedPriceListId = if (!o.has("assignedPriceListId") || o.isNull("assignedPriceListId")) null else o.getLong("assignedPriceListId"),
        notes = o.getString("notes")
    )
    fun encode(v: ZenthaRosterEntry): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("orderId", v.orderId)
        put("surname", v.surname)
        put("name", v.name)
        put("number", v.number)
        put("shirtSize", v.shirtSize)
        put("shortsSize", v.shortsSize)
        put("notes", v.notes)
        put("productId", v.productId ?: JSONObject.NULL)
    }
    fun roster(o: JSONObject) = ZenthaRosterEntry(
        id = o.getLong("id"),
        orderId = o.getLong("orderId"),
        surname = o.getString("surname"),
        name = o.getString("name"),
        number = o.getString("number"),
        shirtSize = o.getString("shirtSize"),
        shortsSize = o.getString("shortsSize"),
        notes = o.getString("notes"),
        productId = if (!o.has("productId") || o.isNull("productId")) null else o.getLong("productId")
    )
    fun encode(v: ZenthaCustomization): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("orderId", v.orderId)
        put("orderItemId", v.orderItemId ?: JSONObject.NULL)
        put("type", v.type)
        put("value", v.value)
        put("notes", v.notes)
        put("approved", v.approved)
        put("attachments", JSONArray(v.attachments))
    }
    fun customizations(o: JSONObject) = ZenthaCustomization(
        id = o.getLong("id"),
        orderId = o.getLong("orderId"),
        orderItemId = if (!o.has("orderItemId") || o.isNull("orderItemId")) null else o.getLong("orderItemId"),
        type = o.getString("type"),
        value = o.getString("value"),
        notes = o.getString("notes"),
        approved = o.getBoolean("approved"),
        attachments = o.optJSONArray("attachments")?.let { a -> List(a.length()) { a.getString(it) } }.orEmpty()
    )
    fun encode(v: ZenthaOrderEvent): JSONObject = JSONObject().apply {
        put("id", v.id)
        put("orderId", v.orderId)
        put("dateTime", v.dateTime)
        put("fromStatus", v.fromStatus)
        put("toStatus", v.toStatus)
        put("note", v.note)
    }
    fun events(o: JSONObject) = ZenthaOrderEvent(
        id = o.getLong("id"),
        orderId = o.getLong("orderId"),
        dateTime = o.getString("dateTime"),
        fromStatus = o.getString("fromStatus"),
        toStatus = o.getString("toStatus"),
        note = o.getString("note")
    )
    fun encode(data: ZenthaData) = JSONObject().apply {
        put("variants", JSONArray().apply { data.variants.forEach { put(encode(it)) } })
        put("balances", JSONArray().apply { data.balances.forEach { put(encode(it)) } })
        put("movements", JSONArray().apply { data.movements.forEach { put(encode(it)) } })
        put("priceLists", JSONArray().apply { data.priceLists.forEach { put(encode(it)) } })
        put("priceRules", JSONArray().apply { data.priceRules.forEach { put(encode(it)) } })
        put("clientProfiles", JSONArray().apply { data.clientProfiles.forEach { put(encode(it)) } })
        put("roster", JSONArray().apply { data.roster.forEach { put(encode(it)) } })
        put("customizations", JSONArray().apply { data.customizations.forEach { put(encode(it)) } })
        put("events", JSONArray().apply { data.events.forEach { put(encode(it)) } })
    }
    fun decode(o: JSONObject?) = ZenthaData(
        variants = o?.optJSONArray("variants")?.let { a -> List(a.length()) { variants(a.getJSONObject(it)) } }.orEmpty(),
        balances = o?.optJSONArray("balances")?.let { a -> List(a.length()) { balances(a.getJSONObject(it)) } }.orEmpty(),
        movements = o?.optJSONArray("movements")?.let { a -> List(a.length()) { movements(a.getJSONObject(it)) } }.orEmpty(),
        priceLists = o?.optJSONArray("priceLists")?.let { a -> List(a.length()) { priceLists(a.getJSONObject(it)) } }.orEmpty(),
        priceRules = o?.optJSONArray("priceRules")?.let { a -> List(a.length()) { priceRules(a.getJSONObject(it)) } }.orEmpty(),
        clientProfiles = o?.optJSONArray("clientProfiles")?.let { a -> List(a.length()) { clientProfiles(a.getJSONObject(it)) } }.orEmpty(),
        roster = o?.optJSONArray("roster")?.let { a -> List(a.length()) { roster(a.getJSONObject(it)) } }.orEmpty(),
        customizations = o?.optJSONArray("customizations")?.let { a -> List(a.length()) { customizations(a.getJSONObject(it)) } }.orEmpty(),
        events = o?.optJSONArray("events")?.let { a -> List(a.length()) { events(a.getJSONObject(it)) } }.orEmpty()
    )
}
