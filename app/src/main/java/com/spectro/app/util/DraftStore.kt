package com.spectro.app.util

import android.content.Context
import com.spectro.app.data.CottonPrintType
import com.spectro.app.data.FabricMode
import com.spectro.app.data.OwnerType
import com.spectro.app.data.TimelineCategory
import com.spectro.app.data.TimelineResponsibility
import com.spectro.app.data.TimelineSeverity
import com.spectro.app.data.WorkStatus
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS = "spectro_drafts_v2"
private const val LEGACY_PREFS = "spectro_drafts_v1"
private const val KEY_CREATE_MODE = "create_mode"

private fun scoped(prefix: String, key: String) = "$prefix:$key"

data class WorkDraft(
    val date: String = "", val clientId: Long? = null, val client: String = "", val title: String = "",
    val due: String = "", val priority: String = "NORMAL", val blocker: String = "NONE", val status: String = "IN_PROCESS",
    val description: String = "", val images: List<String> = emptyList(), val orderedMeters: String = "",
    val printPrice: String = "", val designCode: String = "", val fabricId: Long? = null
)

data class ProductionDraft(
    val step: Int = 1, val workOrderId: Long? = null, val date: String = "", val clientId: Long? = null,
    val clientName: String = "", val orderRef: String = "", val designs: String = "", val printPrice: String = "",
    val plotter: String = "X1", val paper: String = "", val protection: String = "", val paperWaste: String = "",
    val fabricMode: String = FabricMode.NONE.name, val fabricId: Long? = null, val fabricUsed: String = "",
    val fabricWaste: String = "", val salePrice: String = "", val manualFabric: String = "", val notes: String = "",
    val images: List<String> = emptyList(), val allocations: Map<Long, Double> = emptyMap()
)

data class CottonDraft(
    val title: String = "", val clientId: Long? = null, val clientName: String = "", val date: String = "",
    val due: String = "", val fabric: String = "", val printType: String = CottonPrintType.REACTIVE.name,
    val ordered: String = "", val produced: String = "", val delivered: String = "", val defective: String = "",
    val samples: String = "", val leftover: String = "", val productionCost: String = "", val printPrice: String = "",
    val owner: String = OwnerType.CLIENT.name, val inventoryId: Long? = null, val fabricCost: String = "",
    val notes: String = "", val status: String = WorkStatus.IN_PROCESS.name, val images: List<String> = emptyList(),
    val allocations: Map<Long, Double> = emptyMap()
)

data class TimelineDraft(
    val date: String = "", val category: String = TimelineCategory.NOTE.name,
    val responsibility: String = TimelineResponsibility.NONE.name, val severity: String = TimelineSeverity.INFO.name,
    val title: String = "", val description: String = "", val newDue: String = "", val images: List<String> = emptyList()
)

object DraftStore {
    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun legacyPrefs(context: Context) = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)

    fun setLastCreateMode(context: Context, mode: String) = prefs(context).edit().putString(KEY_CREATE_MODE, mode).apply()
    fun lastCreateMode(context: Context): String? = prefs(context).getString(KEY_CREATE_MODE, null) ?: legacyPrefs(context).getString(KEY_CREATE_MODE, null)

    fun saveWork(context: Context, d: WorkDraft, key: String = "new") = prefs(context).edit().putString(scoped("work", key), JSONObject().apply {
        put("date", d.date); putNullable("clientId", d.clientId); put("client", d.client); put("title", d.title); put("due", d.due)
        put("priority", d.priority); put("blocker", d.blocker); put("status", d.status); put("description", d.description)
        put("images", JSONArray(d.images)); put("orderedMeters", d.orderedMeters); put("printPrice", d.printPrice); put("designCode", d.designCode); putNullable("fabricId", d.fabricId)
    }.toString()).apply()
    fun loadWork(context: Context, key: String = "new"): WorkDraft? = raw(context, scoped("work", key), if (key == "new") "work" else null)?.let { o ->
        WorkDraft(o.optString("date"), o.nullableLong("clientId"), o.optString("client"), o.optString("title"), o.optString("due"), o.optString("priority", "NORMAL"), o.optString("blocker", "NONE"), o.optString("status", "IN_PROCESS"), o.optString("description"), o.optJSONArray("images").strings(), o.optString("orderedMeters"), o.optString("printPrice"), o.optString("designCode"), o.nullableLong("fabricId"))
    }
    fun clearWork(context: Context, key: String = "new") { prefs(context).edit().remove(scoped("work", key)).apply(); if(key == "new") legacyPrefs(context).edit().remove("work").apply() }
    fun hasWork(context: Context, key: String = "new") = prefs(context).contains(scoped("work", key)) || (key == "new" && legacyPrefs(context).contains("work"))

    fun saveProduction(context: Context, d: ProductionDraft, key: String = "new") = prefs(context).edit().putString(scoped("production", key), JSONObject().apply {
        put("step", d.step); putNullable("workOrderId", d.workOrderId); put("date", d.date); putNullable("clientId", d.clientId); put("clientName", d.clientName)
        put("orderRef", d.orderRef); put("designs", d.designs); put("printPrice", d.printPrice); put("plotter", d.plotter); put("paper", d.paper); put("protection", d.protection); put("paperWaste", d.paperWaste)
        put("fabricMode", d.fabricMode); putNullable("fabricId", d.fabricId); put("fabricUsed", d.fabricUsed); put("fabricWaste", d.fabricWaste); put("salePrice", d.salePrice); put("manualFabric", d.manualFabric); put("notes", d.notes)
        put("images", JSONArray(d.images)); put("allocations", JSONObject().apply { d.allocations.forEach { (k,v) -> put(k.toString(), v) } })
    }.toString()).apply()
    fun loadProduction(context: Context, key: String = "new"): ProductionDraft? = raw(context, scoped("production", key), if (key == "new") "production" else null)?.let { o ->
        ProductionDraft(o.optInt("step",1), o.nullableLong("workOrderId"), o.optString("date"), o.nullableLong("clientId"), o.optString("clientName"), o.optString("orderRef"), o.optString("designs"), o.optString("printPrice"), o.optString("plotter","X1"), o.optString("paper"), o.optString("protection"), o.optString("paperWaste"), o.optString("fabricMode",FabricMode.NONE.name), o.nullableLong("fabricId"), o.optString("fabricUsed"), o.optString("fabricWaste"), o.optString("salePrice"), o.optString("manualFabric"), o.optString("notes"), o.optJSONArray("images").strings(), o.optJSONObject("allocations").doubleMap())
    }
    fun clearProduction(context: Context, key: String = "new") { prefs(context).edit().remove(scoped("production", key)).apply(); if(key == "new") legacyPrefs(context).edit().remove("production").apply() }
    fun hasProduction(context: Context, key: String = "new") = prefs(context).contains(scoped("production", key)) || (key == "new" && legacyPrefs(context).contains("production"))

    fun saveCotton(context: Context, d: CottonDraft, key: String = "new") = prefs(context).edit().putString(scoped("cotton", key), JSONObject().apply {
        put("title", d.title); putNullable("clientId", d.clientId); put("clientName", d.clientName); put("date", d.date); put("due", d.due); put("fabric", d.fabric); put("printType", d.printType)
        put("ordered", d.ordered); put("produced", d.produced); put("delivered", d.delivered); put("defective", d.defective); put("samples", d.samples); put("leftover", d.leftover); put("productionCost", d.productionCost); put("printPrice", d.printPrice)
        put("owner", d.owner); putNullable("inventoryId", d.inventoryId); put("fabricCost", d.fabricCost); put("notes", d.notes); put("status", d.status); put("images", JSONArray(d.images)); put("allocations", JSONObject().apply { d.allocations.forEach { (k,v) -> put(k.toString(), v) } })
    }.toString()).apply()
    fun loadCotton(context: Context, key: String = "new"): CottonDraft? = raw(context, scoped("cotton", key), if (key == "new") "cotton" else null)?.let { o ->
        CottonDraft(o.optString("title"), o.nullableLong("clientId"), o.optString("clientName"), o.optString("date"), o.optString("due"), o.optString("fabric"), o.optString("printType",CottonPrintType.REACTIVE.name), o.optString("ordered"), o.optString("produced"), o.optString("delivered"), o.optString("defective"), o.optString("samples"), o.optString("leftover"), o.optString("productionCost"), o.optString("printPrice"), o.optString("owner",OwnerType.CLIENT.name), o.nullableLong("inventoryId"), o.optString("fabricCost"), o.optString("notes"), o.optString("status",WorkStatus.IN_PROCESS.name), o.optJSONArray("images").strings(), o.optJSONObject("allocations").doubleMap())
    }
    fun clearCotton(context: Context, key: String = "new") = prefs(context).edit().remove(scoped("cotton", key)).apply()
    fun hasCotton(context: Context, key: String = "new") = prefs(context).contains(scoped("cotton", key)) || (key == "new" && legacyPrefs(context).contains("cotton"))

    fun saveTimeline(context: Context, workOrderId: Long, d: TimelineDraft) = prefs(context).edit().putString(scoped("timeline", workOrderId.toString()), JSONObject().apply {
        put("date", d.date); put("category", d.category); put("responsibility", d.responsibility); put("severity", d.severity)
        put("title", d.title); put("description", d.description); put("newDue", d.newDue); put("images", JSONArray(d.images))
    }.toString()).apply()
    fun loadTimeline(context: Context, workOrderId: Long): TimelineDraft? = raw(context, scoped("timeline", workOrderId.toString()), null)?.let { o ->
        TimelineDraft(o.optString("date"), o.optString("category", TimelineCategory.NOTE.name), o.optString("responsibility", TimelineResponsibility.NONE.name), o.optString("severity", TimelineSeverity.INFO.name), o.optString("title"), o.optString("description"), o.optString("newDue"), o.optJSONArray("images").strings())
    }
    fun clearTimeline(context: Context, workOrderId: Long) = prefs(context).edit().remove(scoped("timeline", workOrderId.toString())).apply()

    private fun raw(context: Context, key: String, legacyKey: String?): JSONObject? {
        val current = prefs(context).getString(key, null)
        val legacy = legacyKey?.let { legacyPrefs(context).getString(it, null) }
        return (current ?: legacy)?.let { runCatching { JSONObject(it) }.getOrNull() }
    }

    private fun JSONObject.putNullable(key: String, value: Long?) { if (value == null) put(key, JSONObject.NULL) else put(key, value) }
    private fun JSONObject.nullableLong(key: String): Long? = if (!has(key) || isNull(key)) null else optLong(key)
    private fun JSONArray?.strings(): List<String> = if (this == null) emptyList() else List(length()) { optString(it) }.filter { it.isNotBlank() }
    private fun JSONObject?.doubleMap(): Map<Long, Double> {
        if (this == null) return emptyMap()
        val out = linkedMapOf<Long, Double>()
        keys().forEach { key -> key.toLongOrNull()?.let { out[it] = optDouble(key) } }
        return out
    }
}
