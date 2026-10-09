package com.spectro.app.util

import com.spectro.app.data.*
import java.io.File
import java.text.NumberFormat
import java.util.Locale

/** The same complete document content feeds both Android PDF rendering and JVM acceptance tests. */
enum class ReportAccent { PRIMARY, POSITIVE, ATTENTION }
sealed interface ZenthaReportBlock {
    data class Section(val title: String) : ZenthaReportBlock
    data class Info(val label: String, val value: String) : ZenthaReportBlock
    data class Paragraph(val label: String, val text: String) : ZenthaReportBlock
    data class Metrics(val values: List<Triple<String, String, ReportAccent>>) : ZenthaReportBlock
    data class Chart(val title: String, val values: List<Pair<String, Double>>, val currency: Boolean) : ZenthaReportBlock
    data class Images(val paths: List<String>) : ZenthaReportBlock
}
data class ZenthaReport(val title: String, val subtitle: String, val blocks: List<ZenthaReportBlock>)
private class ZenthaReportBuilder(val title: String, val subtitle: String) {
    private val blocks = mutableListOf<ZenthaReportBlock>()
    fun section(title: String) { blocks += ZenthaReportBlock.Section(title) }
    fun infoRow(label: String, value: String) { blocks += ZenthaReportBlock.Info(label, value) }
    fun paragraph(label: String, text: String) { blocks += ZenthaReportBlock.Paragraph(label, text) }
    fun metricRow(values: List<Triple<String, String, ReportAccent>>) { blocks += ZenthaReportBlock.Metrics(values) }
    fun barChart(title: String, values: List<Pair<String, Double>>, currency: Boolean = false) { blocks += ZenthaReportBlock.Chart(title, values, currency) }
    fun images(paths: List<String>) { blocks += ZenthaReportBlock.Images(paths) }
    fun build() = ZenthaReport(title, subtitle, blocks.toList())
}
object ZenthaReports {
    private fun eur(value: Double) = NumberFormat.getCurrencyInstance(Locale.ITALY).format(value)
    fun create(s: SpectroSnapshot, orderId: Long, type: ZenthaDocument): ZenthaReport {
        val o = s.zenthaOrders.first { it.id == orderId }
        val rows = s.zenthaItems.filter { it.orderId == orderId }
        val roster = s.zentha.roster.filter { it.orderId == orderId }
        val custom = s.zentha.customizations.filter { it.orderId == orderId }
        val t = ZenthaBusiness.totals(o, s.zenthaItems, s.zenthaExtraCosts, s.zenthaPayments)
        val d = ZenthaReportBuilder(type.title, "${o.orderCode} • ${o.clientName}")
        d.infoRow("Ordine", o.orderCode); d.infoRow("Cliente", o.clientName); d.infoRow("Contatto", o.contact)
        d.infoRow("Data / consegna", "${o.orderDate} / ${o.dueDate.ifBlank { "—" }}"); d.infoRow("Stato", o.status.label())
        if(type in listOf(ZenthaDocument.CONFIRMATION, ZenthaDocument.ECONOMY)) {
            d.metricRow(listOf(Triple("Totale", eur(t.revenue), ReportAccent.PRIMARY), Triple("Pagato", eur(t.paid), ReportAccent.POSITIVE), Triple("Residuo", eur(t.outstanding), ReportAccent.ATTENTION)))
        } else d.metricRow(listOf(Triple("Pezzi", rows.sumOf { it.quantity.toLong() }.toString(), ReportAccent.PRIMARY), Triple("Atleti", roster.size.toString(), ReportAccent.ATTENTION)))
        if(type in listOf(ZenthaDocument.CONFIRMATION, ZenthaDocument.PRODUCTION, ZenthaDocument.PACKING, ZenthaDocument.ECONOMY, ZenthaDocument.HISTORY)) {
            d.section("Righe ordine")
            rows.forEach { r ->
                d.paragraph(r.productName, "${r.variantLabel.ifBlank { "Variante non specificata (legacy)" }} • SKU ${r.skuSnapshot} • ${r.quantity} pezzi")
                if(type in listOf(ZenthaDocument.CONFIRMATION, ZenthaDocument.ECONOMY)) d.infoRow("Prezzo / totale", "${eur(r.unitPrice)} / ${eur(ZenthaBusiness.money(r.revenue))}")
                if(type == ZenthaDocument.ECONOMY) d.infoRow("Costo / margine", "${eur(r.cost)} / ${eur(r.margin)}")
            }
        }
        if(type in listOf(ZenthaDocument.ROSTER, ZenthaDocument.PRODUCTION, ZenthaDocument.HISTORY)) {
            d.section("Distinta squadra")
            roster.forEach { r -> d.paragraph("${r.surname} ${r.name}", "Numero ${r.number} • Maglia ${r.shirtSize} • Pantalone ${r.shortsSize}\n${s.zenthaProducts.firstOrNull { it.id == r.productId }?.name.orEmpty()}\n${r.notes}") }
            if(roster.isEmpty()) d.paragraph("Distinta", "Nessun atleta registrato.")
        }
        if(type in listOf(ZenthaDocument.SIZES, ZenthaDocument.PACKING, ZenthaDocument.PRODUCTION)) {
            d.section("Quantità per prodotto e taglia")
            val sizes = rows.groupBy { "${it.productName} • ${it.sizeSnapshot.ifBlank { "Non specificata (legacy)" }}" }.map { (label, list) -> label to list.sumOf { it.quantity.toLong() }.toDouble() }
            sizes.forEach { (label, count) -> d.paragraph(label, "${count.toLong()} pezzi") }
            d.barChart("Taglie ordine", sizes)
            if(type == ZenthaDocument.SIZES) {
                d.section("Taglie distinta (atleti)")
                roster.groupBy { "Maglia ${it.shirtSize} / pantalone ${it.shortsSize}" }.forEach { (label, list) -> d.infoRow(label, "${list.size} atleti") }
            }
        }
        if(type in listOf(ZenthaDocument.PRODUCTION, ZenthaDocument.HISTORY)) {
            d.section("Personalizzazioni")
            custom.forEach { c ->
                d.paragraph("${c.type} • ${if(c.approved) "Approvato" else "Da approvare"}", "${c.value}\n${c.notes}")
                c.orderItemId?.let { id -> rows.firstOrNull { it.id == id }?.let { d.paragraph("Riga", "${it.productName} • ${it.variantLabel}") } }
                c.attachments.forEach { d.paragraph("Documento", File(it).name) }; d.images(c.attachments)
            }
        }
        if(type == ZenthaDocument.ECONOMY) {
            d.section("Economia")
            d.infoRow("Sconto", eur(o.discount)); d.infoRow("Trasporto / altro", "${eur(o.shippingRevenue)} / ${eur(o.otherRevenue)}")
            s.zenthaExtraCosts.filter { it.orderId == orderId }.forEach { d.paragraph(it.label, eur(it.amount)) }
            d.metricRow(listOf(Triple("Costi", eur(t.productCosts + t.extraCosts), ReportAccent.ATTENTION), Triple("Utile", eur(t.profit), ReportAccent.PRIMARY), Triple("Margine", "%.1f%%".format(t.marginPercent), ReportAccent.POSITIVE)))
            d.infoRow("Quote utile A / B", "${o.splitPercentA}% ${eur(t.shareA)} / ${o.splitPercentB}% ${eur(t.shareB)}")
            d.barChart("Economia ordine", listOf("Ricavi" to t.revenue, "Costi prodotti" to t.productCosts, "Costi extra" to t.extraCosts, "Utile" to t.profit), true)
        }
        if(type in listOf(ZenthaDocument.ECONOMY, ZenthaDocument.HISTORY, ZenthaDocument.CONFIRMATION)) {
            d.section("Pagamenti • ${t.paymentState.label()}")
            s.zenthaPayments.filter { it.orderId == orderId }.sortedBy { it.date }.forEach { d.paragraph("${it.date} • ${eur(it.amount)}", "${it.method.name} • ${it.notes}") }
            if(t.overpayment > 0) d.infoRow("Eccedenza", eur(t.overpayment))
        }
        if(type == ZenthaDocument.HISTORY) {
            d.section("Cronologia")
            s.zentha.events.filter { it.orderId == orderId }.sortedBy { it.dateTime }.forEach { e -> d.paragraph(e.dateTime, "${runCatching { ZenthaOrderStatus.valueOf(e.toStatus).label() }.getOrDefault(e.toStatus)} • ${e.note}") }
            d.section("Movimenti magazzino")
            s.zentha.movements.filter { it.orderId == orderId }.sortedBy { it.dateTime }.forEach { m -> d.paragraph(m.dateTime, "${m.type.label()} • ${m.quantity} pezzi • ${s.zentha.variants.firstOrNull { it.id == m.variantId }?.label.orEmpty()} • ${m.note}") }
        }
        d.paragraph("Note", o.notes)
        if(type in listOf(ZenthaDocument.PRODUCTION, ZenthaDocument.HISTORY, ZenthaDocument.CONFIRMATION)) { o.attachments.forEach { d.paragraph("Documento allegato", File(it).name) }; d.images(o.attachments) }
        return d.build()
    }
}
