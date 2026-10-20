package com.spectro.app.util

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.spectro.app.R
import com.spectro.app.data.*
import java.io.File
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.max

object ReportExporter {
    private val BG = Color.rgb(6, 6, 9)
    private val PANEL = Color.rgb(18, 18, 24)
    private val PANEL_2 = Color.rgb(28, 28, 36)
    private val WHITE = Color.rgb(248, 248, 250)
    private val MUTED = Color.rgb(205, 205, 216)
    private val RED = Color.rgb(255, 48, 48)
    private val ORANGE = Color.rgb(255, 122, 0)
    private val MAGENTA = Color.rgb(255, 43, 214)
    private val GREEN = Color.rgb(34, 197, 94)
    private val CYAN = Color.rgb(0, 220, 255)
    private val YELLOW = Color.rgb(245, 158, 11)

    private class DarkPdf(private val context: Context, private val reportTitle: String, private val subtitle: String = "", private val appearance: PdfAppearance = PdfAppearance.DARK) {
        val pdf = PdfDocument()
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: android.graphics.Canvas
        var y = 0f
        private val left = 34f
        private val right = 561f
        private val width = right - left
        private val white = paint(WHITE, 14f)
        private val muted = paint(MUTED, 12f)
        private val bold = paint(WHITE, 15f, true)
        private val section = paint(WHITE, 18f, true)
        private val tiny = paint(MUTED, 10.5f)

        init { newPage() }

        private fun tone(color: Int): Int = if (appearance == PdfAppearance.DARK) color else when (color) {
            BG -> Color.WHITE
            PANEL -> Color.rgb(247, 248, 250)
            PANEL_2 -> Color.rgb(236, 239, 244)
            WHITE -> Color.rgb(22, 25, 32)
            MUTED -> Color.rgb(82, 89, 102)
            else -> color
        }

        private fun paint(color: Int, size: Float, bold: Boolean = false) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = tone(color); textSize = size; isFakeBoldText = bold
        }

        fun newPage() {
            if (pageNo > 0) pdf.finishPage(page)
            pageNo++
            page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNo).create())
            c = page.canvas
            c.drawColor(tone(BG))
            y = 30f
            drawHeader()
        }

        private fun drawHeader() {
            val logo = runCatching { BitmapFactory.decodeResource(context.resources, R.drawable.spectro_header_logo) }.getOrNull()
            if (logo != null) {
                val ratio = logo.width.toFloat() / max(1, logo.height).toFloat()
                val h = 38f
                val w = (h * ratio).coerceAtMost(92f)
                c.drawBitmap(logo, null, RectF(left, y, left + w, y + h), null)
            }
            val titlePaint = paint(WHITE, 20f, true)
            val subPaint = paint(ORANGE, 10f, true)
            c.drawText("SPECTRO ERP", 140f, y + 17f, titlePaint)
            c.drawText(reportTitle.uppercase(), 140f, y + 35f, subPaint)
            if (subtitle.isNotBlank()) c.drawText(subtitle.take(62), left, y + 56f, tiny)
            y += 72f
            c.drawRect(left, y, right, y + 2f, paint(ORANGE, 1f))
            y += 18f
        }

        fun ensure(h: Float) { if (y + h > 808f) newPage() }

        fun section(title: String, accent: Int = ORANGE) {
            ensure(38f); y += 7f
            c.drawRect(left, y - 12f, left + 5f, y + 8f, paint(accent, 1f))
            c.drawText(title.uppercase(), left + 14f, y + 4f, section)
            y += 27f
        }

        fun infoRow(label: String, value: String, accent: Int? = null) {
            ensure(28f)
            val lp = paint(MUTED, 11f, true)
            val vp = paint(accent ?: WHITE, 12.5f, accent != null)
            c.drawText(label.uppercase(), left, y, lp)
            val lines = wrap(value.ifBlank { "-" }, 48)
            c.drawText(lines.firstOrNull().orEmpty(), 205f, y, vp)
            y += 22f
            lines.drop(1).take(2).forEach { c.drawText(it, 205f, y, vp); y += 18f }
        }

        fun paragraph(label: String, text: String, accent: Int = WHITE) {
            if (text.isBlank()) return
            ensure(44f)
            c.drawText(label.uppercase(), left, y, paint(accent, 11f, true)); y += 19f
            wrap(text, 78).forEach { line -> ensure(18f); c.drawText(line, left, y, white); y += 18f }
            y += 7f
        }

        fun metricRow(metrics: List<Triple<String, String, Int>>) {
            if (metrics.isEmpty()) return
            ensure(83f)
            val gap = 8f
            val count = metrics.size.coerceAtMost(3)
            val cardW = (width - gap * (count - 1)) / count
            metrics.take(3).forEachIndexed { idx, (label, value, accent) ->
                val x = left + idx * (cardW + gap)
                c.drawRoundRect(RectF(x, y, x + cardW, y + 72f), 12f, 12f, paint(PANEL, 1f))
                c.drawText(label.uppercase().take(18), x + 12f, y + 21f, paint(MUTED, 9f, true))
                c.drawText(value.take(20), x + 12f, y + 51f, paint(accent, 18f, true))
            }
            y += 84f
        }

        fun barChart(title: String, values: List<Pair<String, Double>>, currency: Boolean = false) {
            val filtered = values.filter { it.second != 0.0 }.take(8)
            if (filtered.isEmpty()) return
            val h = 42f + filtered.size * 30f
            ensure(h)
            c.drawRoundRect(RectF(left, y, right, y + h - 8f), 14f, 14f, paint(PANEL, 1f))
            c.drawText(title.uppercase(), left + 14f, y + 22f, paint(WHITE, 12f, true))
            val maximum = filtered.maxOf { kotlin.math.abs(it.second) }.coerceAtLeast(0.01)
            val colors = intArrayOf(ORANGE, MAGENTA, GREEN, CYAN, YELLOW, RED)
            var rowY = y + 42f
            filtered.forEachIndexed { idx, (label, value) ->
                c.drawText(label.take(22), left + 14f, rowY + 10f, paint(MUTED, 9.5f, true))
                val bx = left + 145f
                val bw = 275f * (kotlin.math.abs(value) / maximum).toFloat()
                c.drawRoundRect(RectF(bx, rowY, bx + bw.coerceAtLeast(4f), rowY + 12f), 6f, 6f, paint(colors[idx % colors.size], 1f))
                val formatted = if (currency) eur(value) else "%.1f".format(Locale.ITALY, value)
                c.drawText(formatted, left + 430f, rowY + 10f, paint(WHITE, 9.5f, true))
                rowY += 30f
            }
            y += h
        }

        fun timelineEvent(event: OrderTimelineEvent) {
            val accent = when (event.severity) { TimelineSeverity.INFO -> CYAN; TimelineSeverity.LOW -> GREEN; TimelineSeverity.MEDIUM -> YELLOW; TimelineSeverity.HIGH, TimelineSeverity.BLOCKING -> RED }
            val descLines = wrap(localizeStatusTokens(event.description), 72).take(8)
            val h = 68f + descLines.size * 17f + if (event.images.isNotEmpty()) 84f else 0f
            ensure(h)
            c.drawRoundRect(RectF(left, y, right, y + h - 7f), 13f, 13f, paint(PANEL, 1f))
            c.drawRect(left, y, left + 5f, y + h - 7f, paint(accent, 1f))
            c.drawText("${event.date}${if (event.time.isNotBlank()) " • ${event.time}" else ""}", left + 15f, y + 20f, paint(MUTED, 10f, true))
            c.drawText(event.title.take(55), left + 15f, y + 43f, paint(WHITE, 14f, true))
            c.drawText("${timelineCategoryIt(event.category)} • ${timelineResponsibilityIt(event.responsibility)} • ${timelineSeverityIt(event.severity)}", left + 15f, y + 61f, paint(accent, 9.5f, true))
            var yy = y + 81f
            descLines.forEach { c.drawText(it, left + 15f, yy, paint(WHITE, 10.5f)); yy += 17f }
            if (event.oldDueDate.isNotBlank() || event.newDueDate.isNotBlank()) { c.drawText("Consegna: ${event.oldDueDate.ifBlank { "-" }} → ${event.newDueDate.ifBlank { "-" }}", left + 15f, yy, paint(YELLOW, 9.5f, true)); yy += 18f }
            if (event.images.isNotEmpty()) imageStrip(event.images, yy, left + 15f)
            y += h
        }

        fun detailCard(title: String, subtitle: String = "", value: String = "", accent: Int = ORANGE) {
            ensure(60f)
            c.drawRoundRect(RectF(left, y, right, y + 52f), 12f, 12f, paint(PANEL_2, 1f))
            c.drawText(title.take(50), left + 12f, y + 20f, paint(WHITE, 11.5f, true))
            if (subtitle.isNotBlank()) c.drawText(subtitle.take(62), left + 12f, y + 39f, paint(MUTED, 9.5f))
            if (value.isNotBlank()) c.drawText(value.take(20), right - 125f, y + 29f, paint(accent, 12f, true))
            y += 60f
        }

        fun images(paths: List<String>) {
            if (paths.isEmpty()) return
            section("Allegati", MAGENTA)
            paths.chunked(3).forEach { group -> ensure(110f); imageStrip(group, y, left); y += 108f }
        }

        private fun imageStrip(paths: List<String>, top: Float, startX: Float) {
            var x = startX
            paths.take(3).forEach { path ->
                val bmp = runCatching { BitmapFactory.decodeFile(path) }.getOrNull()
                if (bmp != null) {
                    val w = 105f; val h = 76f
                    c.drawBitmap(bmp, null, RectF(x, top, x + w, top + h), null)
                }
                x += 115f
            }
        }

        fun finish(file: File) {
            pdf.finishPage(page)
            file.outputStream().use { pdf.writeTo(it) }
            pdf.close()
        }
    }

    fun share(context: Context, r: ProductionRecord) {
        val file = outFile(context, "Spectro_Report_${r.date}_${r.id}.pdf")
        val d = DarkPdf(context, if (r.type == RecordType.PRODUCTION) "Report produzione" else "Evento negativo", "${r.date} • ${r.clientName.ifBlank { "Evento interno" }}")
        if (r.type == RecordType.PRODUCTION) {
            d.metricRow(listOf(Triple("Fatturato", eur(r.grossRevenue), GREEN), Triple("Costi", eur(r.materialCostTotal), YELLOW), Triple("Utile", eur(r.grossProfit), if (r.grossProfit >= 0) GREEN else RED)))
            d.barChart("Economia ordine", listOf("Fatturato" to r.grossRevenue, "Costi materie" to r.materialCostTotal, "Utile lordo" to r.grossProfit), true)
            d.section("Dati produttivi")
            d.infoRow("Cliente", r.clientName); d.infoRow("Ordine", r.orderRef); d.infoRow("Carta stampata", "${r.printedPaperMeters} mt • ${r.plotter}")
            d.infoRow("Carta protezione", "${r.protectionPaperMeters} mt"); d.infoRow("Tessuto", r.fabricDisplayName); d.infoRow("Tessuto lavorato", "${r.fabricUsedMeters} mt")
            d.infoRow("Disegni", r.designs.joinToString(", ").ifBlank { "-" })
            d.paragraph("Note tecniche", r.technicalNotes, CYAN)
        } else {
            d.metricRow(listOf(Triple("Rimborsi", eur(r.refundCost), RED), Triple("Tessuto", eur(r.fabricLossCost), YELLOW), Triple("Perdita", eur(r.materialCostTotal), RED)))
            d.paragraph("Descrizione evento", r.technicalNotes, RED)
        }
        d.images(r.images); d.finish(file); sharePdf(context, file, "Condividi report Spectro")
    }

    fun shareWorkOrder(context: Context, w: WorkOrder) {
        val file = outFile(context, "Spectro_Ordine_${w.receivedDate}_${w.id}.pdf")
        val d = DarkPdf(context, "Ordine", "${w.title.ifBlank { w.orderCode }} • ${w.client}")
        d.metricRow(listOf(Triple("Metri", if (w.orderedMeters > 0) "${w.orderedMeters} mt" else "-", CYAN), Triple("Prezzo", if (w.printPricePerMeter > 0) eur(w.printPricePerMeter) + "/mt" else "-", GREEN), Triple("Stato", workStatusIt(w.status), ORANGE)))
        d.section("Informazioni ordine")
        d.infoRow("Codice interno", w.orderCode); d.infoRow("Cliente", w.client); d.infoRow("Data ordine", w.receivedDate); d.infoRow("Consegna materiale", w.materialReceivedDate); d.infoRow("Consegna ordine", w.dueDate)
        d.paragraph("Note consegna materiale", w.materialReceiptNotes, CYAN)
        d.infoRow("Disegno", w.designCode); d.infoRow("Priorità", if (w.priority == Priority.HIGH) "Alta" else "Normale", if (w.priority == Priority.HIGH) RED else GREEN)
        d.paragraph("Descrizione / note", w.description)
        d.images(w.images); d.finish(file); sharePdf(context, file, "Condividi ordine Spectro")
    }

    fun shareCottonOrder(context: Context, order: CottonOrder) {
        val file = outFile(context, "Spectro_Cotone_${order.orderDate}_${order.id}.pdf")
        val d = DarkPdf(context, "Ordine cotone", "${order.title.ifBlank { order.orderCode }} • ${order.clientName}")
        d.metricRow(listOf(Triple("Ricavo", eur(order.grossRevenue), GREEN), Triple("Costi", eur(order.totalCost), YELLOW), Triple("Margine", eur(order.margin), if (order.margin >= 0) GREEN else RED)))
        d.barChart("Economia cotone", listOf("Ricavo" to order.grossRevenue, "Costo produzione" to order.productionCost, "Costo tessuto" to order.fabricCost, "Margine" to order.margin), true)
        d.section("Ordine")
        d.infoRow("Stato", workStatusIt(order.status)); d.infoRow("Tessuto", order.fabricName); d.infoRow("Metri ordinati", "${order.orderedMeters} mt")
        d.infoRow("Metri prodotti", "${order.actualProducedMeters} mt"); d.infoRow("Metri consegnati", "${order.deliveredMeters} mt"); d.infoRow("Difettosi", "${order.defectiveMeters} mt")
        d.infoRow("Campioni", "${order.sampleMeters} mt"); d.infoRow("Avanzati", "${order.leftoverMeters} mt"); d.infoRow("Consegna", order.dueDate)
        d.paragraph("Note", order.notes, CYAN); d.images(order.images); d.finish(file); sharePdf(context, file, "Condividi ordine cotone")
    }

    fun shareEconomicWork(context: Context, work: EconomicWork) {
        val file = outFile(context, "Spectro_AltroRicavo_${work.date}_${work.id}.pdf")
        val d = DarkPdf(context, "Lavoro economico", "${work.title} • ${work.clientName.ifBlank { "Senza cliente" }}")
        d.metricRow(listOf(Triple("Fatturato lordo", eur(work.grossRevenue), GREEN), Triple("Costi", eur(work.totalCosts), YELLOW), Triple("Utile netto", eur(work.netProfit), if (work.netProfit >= 0) GREEN else RED)))
        d.barChart("Economia lavoro", listOf("Fatturato" to work.grossRevenue, "Materiali" to work.materialCosts, "Altri costi" to work.otherCosts, "Utile" to work.netProfit), true)
        d.section("Dettaglio")
        d.infoRow("Data", work.date); d.infoRow("Cliente", work.clientName); d.infoRow("Stato", economicStatusIt(work.status)); d.infoRow("Quantità", if (work.quantity > 0) "${work.quantity} mt/unità" else "-")
        d.infoRow("Prezzo unitario", if (work.unitPrice > 0) eur(work.unitPrice) else "-")
        if (work.materials.isNotEmpty()) { d.section("Materiali utilizzati", MAGENTA); work.materials.forEach { d.detailCard(it.name, "${it.quantity} × ${eur(it.unitCost)}", eur(it.totalCost), YELLOW) } }
        d.paragraph("Note", work.notes, CYAN); d.images(work.images); d.finish(file); sharePdf(context, file, "Condividi lavoro economico")
    }

    fun shareOrderTimeline(context: Context, order: WorkOrder, events: List<OrderTimelineEvent>, productions: List<ProductionRecord> = emptyList()) {
        val file = outFile(context, "Spectro_Storico_${order.orderCode.ifBlank { order.id.toString() }}.pdf")
        val critical = events.filter { it.severity != TimelineSeverity.INFO }
        val d = DarkPdf(context, "Storico ordine", "${order.title.ifBlank { order.orderCode }} • ${order.client}")
        d.metricRow(listOf(Triple("Aggiornamenti", events.size.toString(), CYAN), Triple("Criticità", critical.size.toString(), YELLOW), Triple("Produzioni", productions.size.toString(), MAGENTA)))
        d.section("Dati ordine")
        d.infoRow("Stato", workStatusIt(order.status)); d.infoRow("Data ordine", order.receivedDate); d.infoRow("Consegna materiale", order.materialReceivedDate); d.infoRow("Consegna ordine", order.dueDate); d.infoRow("Metri ordine", "${order.orderedMeters} mt"); d.infoRow("Disegno", order.designCode)
        d.paragraph("Note consegna materiale", order.materialReceiptNotes, CYAN)
        d.paragraph("Note ordine", order.description)
        d.images(order.images)

        if (productions.isNotEmpty()) {
            val totalPaper = productions.sumOf { it.printedPaperMeters }
            val totalFabric = productions.sumOf { it.fabricUsedMeters }
            val totalRevenue = productions.sumOf { it.grossRevenue }
            val totalProfit = productions.sumOf { it.grossProfit }
            d.section("Produzione complessiva", CYAN)
            d.metricRow(listOf(Triple("Carta", "${"%.1f".format(Locale.ITALY, totalPaper)} mt", CYAN), Triple("Tessuto", "${"%.1f".format(Locale.ITALY, totalFabric)} mt", MAGENTA), Triple("Utile", eur(totalProfit), if (totalProfit >= 0) GREEN else RED)))
            d.infoRow("Fatturato produzioni", eur(totalRevenue), GREEN)
            productions.sortedWith(compareBy<ProductionRecord> { it.date }.thenBy { it.id }).forEachIndexed { index, production ->
                d.detailCard(
                    "Produzione ${index + 1} • ${production.date}",
                    "${production.fabricDisplayName.uppercase()} • Carta ${"%.1f".format(Locale.ITALY, production.printedPaperMeters)} mt • Tessuto ${"%.1f".format(Locale.ITALY, production.fabricUsedMeters)} mt",
                    eur(production.grossRevenue),
                    GREEN
                )
                d.paragraph("Nota produzione ${index + 1}", production.technicalNotes, CYAN)
                d.images(production.images)
            }
        }

        d.section("Timeline completa", MAGENTA)
        if (events.isEmpty()) d.detailCard("Nessun aggiornamento registrato") else events.sortedWith(compareBy<OrderTimelineEvent> { it.date }.thenBy { it.time }.thenBy { it.id }).forEach { d.timelineEvent(it) }
        d.finish(file); sharePdf(context, file, "Condividi storico ordine Spectro")
    }

    fun shareMonthlyStatistics(
        context: Context,
        stats: MonthlyStats,
        productions: List<ProductionRecord>,
        cottonOrders: List<CottonOrder>,
        economicWorks: List<EconomicWork> = emptyList(),
        timelineEvents: List<OrderTimelineEvent> = emptyList(),
        clientIssues: List<ClientIssue> = emptyList(),
        workOrders: List<WorkOrder> = emptyList()
    ) {
        val file = outFile(context, "Spectro_Statistiche_${stats.month}.pdf")
        val d = DarkPdf(context, "Report mensile", stats.month)
        d.metricRow(listOf(Triple("Fatturato", eur(stats.totalRevenue), GREEN), Triple("Costi", eur(stats.totalCosts), YELLOW), Triple("Utile netto", eur(stats.netProfit), if (stats.netProfit >= 0) GREEN else RED)))
        d.metricRow(listOf(Triple("Carta stampata", "%.1f mt".format(Locale.ITALY, stats.printedPaperMeters), CYAN), Triple("Cotone", "%.1f mt".format(Locale.ITALY, stats.cottonMeters), MAGENTA), Triple("Criticità", stats.criticalityCount.toString(), if (stats.criticalityCount > 0) YELLOW else GREEN)))
        d.infoRow("Ordini completati / evasi", stats.completedOrderCount.toString(), GREEN)
        d.barChart("Composizione fatturato", listOf("Sublimazione" to stats.productionRevenue, "Cotone" to stats.cottonRevenue, "Ulteriori lavorazioni" to stats.otherRevenue), true)
        d.barChart("Fatturato per cliente", stats.clients.take(8).map { it.clientName to it.revenue }, true)
        d.barChart("Metri carta per cliente", stats.clients.filter { it.paperMeters > 0 }.take(8).map { it.clientName to it.paperMeters })
        d.section("Riepilogo economico")
        d.infoRow("Sublimazione", eur(stats.productionRevenue)); d.infoRow("Cotone", eur(stats.cottonRevenue)); d.infoRow("Ulteriori lavorazioni", eur(stats.otherRevenue), GREEN)
        d.infoRow("Costi materie sublimazione", eur(stats.materialCosts)); d.infoRow("Costi cotone", eur(stats.cottonCosts)); d.infoRow("Costi altri lavori", eur(stats.otherCosts)); d.infoRow("Costi fissi", eur(stats.fixedCosts))
        d.section("Dettaglio clienti", MAGENTA)
        stats.clients.forEach { c -> d.detailCard(c.clientName, "Carta ${"%.1f".format(Locale.ITALY, c.paperMeters)} mt • Cotone ${"%.1f".format(Locale.ITALY, c.cottonMeters)} mt • Evasi ${c.completedOrders} • Criticità ${c.criticalities}", "${eur(c.revenue)} / ${eur(c.profit)}", if (c.profit >= 0) GREEN else RED) }
        val archivedOrders = workOrders.filter { it.status.isArchivedOrder() }
        if (archivedOrders.isNotEmpty()) {
            d.section("Ordini evasi", GREEN)
            archivedOrders.sortedBy { it.effectiveDeliveryDate }.forEach { o ->
                val critCount = timelineEvents.count { it.workOrderId == o.id && it.severity != TimelineSeverity.INFO }
                d.detailCard(
                    "${o.effectiveDeliveryDate.ifBlank { o.receivedDate }} • ${o.client}",
                    "${o.title.ifBlank { o.orderCode }} • ${workStatusIt(o.status)} • Criticità $critCount",
                    if (o.orderedMeters > 0) "${o.orderedMeters} mt" else "",
                    GREEN
                )
            }
        }
        d.section("Produzioni sublimazione", ORANGE)
        productions.filter { it.type == RecordType.PRODUCTION }.sortedBy { it.date }.forEach { r -> d.detailCard("${r.date} • ${r.clientName}", "${r.orderRef.ifBlank { "Senza riferimento" }} • Carta ${"%.1f".format(Locale.ITALY, r.printedPaperMeters)} mt", eur(r.grossRevenue), GREEN) }
        d.section("Ordini cotone", MAGENTA)
        cottonOrders.sortedBy { it.orderDate }.forEach { o -> d.detailCard("${o.orderDate} • ${o.clientName}", "${o.title.ifBlank { o.orderCode }} • ${o.fabricName} • ${"%.1f".format(Locale.ITALY, if (o.actualProducedMeters > 0) o.actualProducedMeters else o.orderedMeters)} mt", eur(o.margin), if (o.margin >= 0) GREEN else RED) }
        if (economicWorks.isNotEmpty()) { d.section("Altri lavori economici", CYAN); economicWorks.sortedBy { it.date }.forEach { w -> d.detailCard("${w.date} • ${w.clientName.ifBlank { "Senza cliente" }}", w.title, eur(w.netProfit), if (w.netProfit >= 0) GREEN else RED) } }
        val critical = timelineEvents.filter { it.severity != TimelineSeverity.INFO }
        if (critical.isNotEmpty() || clientIssues.isNotEmpty()) {
            d.section("Criticità del mese", RED)
            critical.sortedBy { it.date }.forEach { e ->
                d.detailCard("${e.date} • ${e.clientName}", "${e.orderRef} • ${e.title} • ${timelineResponsibilityIt(e.responsibility)}", timelineSeverityIt(e.severity), if (e.status == TimelineStatus.OPEN) RED else GREEN)
            }
            clientIssues.sortedBy { it.openedDate }.forEach { issue ->
                d.detailCard("${issue.openedDate} • ${issue.clientName}", "${issue.orderRef} • ${issue.title} • Criticità CRM", issue.severity.name, if (issue.status !in listOf(IssueStatus.RESOLVED, IssueStatus.CLOSED)) RED else GREEN)
            }
        }
        d.finish(file); sharePdf(context, file, "Condividi statistiche Spectro ${stats.month}")
    }

    fun shareIssue(context: Context, issue: ClientIssue, updates: List<IssueUpdate>) {
        val file = outFile(context, "Spectro_Criticita_${issue.openedDate}_${issue.id}.pdf")
        val d = DarkPdf(context, "Dossier criticità", "${issue.clientName} • ${issue.title}")
        d.metricRow(listOf(Triple("Ritardo", "${issue.delayDays} gg", if (issue.delayDays > 0) RED else GREEN), Triple("Gravità", issue.severity.name, YELLOW), Triple("Stato", issue.status.name, CYAN)))
        d.section("Informazioni")
        d.infoRow("Ordine", issue.orderRef); d.infoRow("Apertura", issue.openedDate); d.infoRow("Consegna iniziale", issue.expectedDeliveryDate); d.infoRow("Nuova consegna", issue.revisedDeliveryDate)
        d.infoRow("Categoria", issue.category.name); d.infoRow("Responsabilità", issue.responsibility.name)
        d.paragraph("Descrizione dei fatti", issue.description, RED); d.paragraph("Note interne", issue.internalNotes, CYAN); d.images(issue.evidenceImages)
        if (updates.isNotEmpty()) { d.section("Cronologia", MAGENTA); updates.sortedBy { it.date }.forEach { u -> d.detailCard(u.date, u.note, "${u.images.size} allegati", CYAN); d.images(u.images) } }
        d.finish(file); sharePdf(context, file, "Condividi dossier criticità")
    }

    fun shareZenthaOrder(
        context: Context,
        order: ZenthaOrder,
        items: List<ZenthaOrderItem>,
        extras: List<ZenthaExtraCost>,
        payments: List<ZenthaPayment>,
        products: List<ZenthaProduct> = emptyList(),
        profile: ZenthaExportProfile = ZenthaExportProfile.COMPLETE,
        productions: List<ProductionRecord> = emptyList(),
        appearance: PdfAppearance = PdfAppearance.DARK
    ) {
        val revenue = items.sumOf { it.revenue }
        val productCosts = items.sumOf { it.cost }
        val extra = extras.sumOf { it.amount }
        val totalCosts = productCosts + extra
        val profit = revenue - totalCosts
        val paid = payments.sumOf { it.amount }
        val quantity = items.sumOf { it.quantity }
        val paperMeters = productions.filter { it.zenthaOrderId == order.id }.sumOf { it.printedPaperMeters }
        val chromiaAmount = paperMeters * order.chromiaRatePerMeter
        val profileKey = when (profile) {
            ZenthaExportProfile.ACCOUNTING -> "CONTABILITA"
            ZenthaExportProfile.PRODUCTION -> "PRODUZIONE"
            ZenthaExportProfile.COMPLETE -> "COMPLETO"
        }
        val safeCode = order.orderCode.ifBlank { order.id.toString() }.replace(Regex("[^A-Za-z0-9_-]"), "_")
        val file = outFile(context, "Spectro_Zentha_${safeCode}_$profileKey.pdf")
        val title = when (profile) {
            ZenthaExportProfile.ACCOUNTING -> "Ordine Zentha • Contabilità"
            ZenthaExportProfile.PRODUCTION -> "Ordine Zentha • Produzione"
            ZenthaExportProfile.COMPLETE -> "Ordine Zentha • Dossier completo"
        }
        val d = DarkPdf(context, title, "${order.orderCode} • ${order.clientName}", appearance)

        d.section("Identificazione ordine", ORANGE)
        d.infoRow("Codice ordine", order.orderCode)
        d.infoRow("Cliente / società", order.clientName)
        d.infoRow("Data ordine", order.orderDate)
        d.infoRow("Consegna prevista", order.dueDate.ifBlank { "Da definire" }, if (order.dueDate.isNotBlank()) ORANGE else YELLOW)
        d.infoRow("Stato", zenthaStatusIt(order.status))

        when (profile) {
            ZenthaExportProfile.ACCOUNTING -> {
                d.metricRow(listOf(
                    Triple("Articoli", items.size.toString(), CYAN),
                    Triple("Pezzi", quantity.toString(), MAGENTA),
                    Triple("Totale ordine", eur(revenue), GREEN)
                ))
                d.section("Righe per fatturazione", GREEN)
                items.forEachIndexed { index, item ->
                    val product = products.firstOrNull { it.id == item.productId }
                    val code = item.skuSnapshot.ifBlank { product?.sku.orEmpty() }
                    val description = item.productDescriptionSnapshot.ifBlank { product?.description.orEmpty() }
                    d.detailCard(
                        "${index + 1}. ${listOf(code, item.productName).filter { it.isNotBlank() }.joinToString(" • ")}",
                        "Q.tà ${item.quantity} × ${eur(item.unitPrice)}${item.sizeBreakdown.takeIf { it.isNotBlank() }?.let { " • Taglie $it" }.orEmpty()}",
                        eur(item.revenue),
                        GREEN
                    )
                    d.paragraph("Descrizione / tessuto", description, CYAN)
                    val commercialDetails = listOf(
                        "Variante" to item.variant,
                        "Colore" to item.color,
                        "Personalizzazioni" to item.personalization
                    ).filter { it.second.isNotBlank() }.joinToString(" • ") { "${it.first}: ${it.second}" }
                    d.paragraph("Dettagli articolo", commercialDetails)
                }
                d.section("Totali e incassi", CYAN)
                d.infoRow("Totale articoli", eur(revenue), GREEN)
                d.infoRow("Stampa Chromia", "${"%.1f".format(Locale.ITALY, paperMeters)} mt × ${eur(order.chromiaRatePerMeter)}/mt = ${eur(chromiaAmount)}", MAGENTA)
                d.infoRow("Incassato", eur(paid), GREEN)
                d.infoRow("Da incassare", eur((revenue - paid).coerceAtLeast(0.0)), if (revenue > paid) YELLOW else GREEN)
                payments.sortedBy { it.date }.forEach { payment ->
                    d.detailCard(payment.date, "${paymentMethodIt(payment.method)}${payment.notes.takeIf { it.isNotBlank() }?.let { " • $it" }.orEmpty()}", eur(payment.amount), GREEN)
                }
                d.paragraph("Note amministrative", order.notes)
                d.paragraph("Uso del documento", "Riepilogo gestionale di supporto alla fatturazione. Verificare aliquota IVA, dati fiscali e condizioni commerciali prima di emettere la fattura.", ORANGE)
            }

            ZenthaExportProfile.PRODUCTION -> {
                d.metricRow(listOf(
                    Triple("Articoli", items.size.toString(), CYAN),
                    Triple("Pezzi totali", quantity.toString(), MAGENTA),
                    Triple("Consegna", order.dueDate.ifBlank { "Da definire" }, ORANGE)
                ))
                d.paragraph("Riservatezza", "Scheda operativa priva di prezzi, costi, margini e pagamenti.", GREEN)
                d.section("Distinta di produzione", MAGENTA)
                items.forEachIndexed { index, item ->
                    val product = products.firstOrNull { it.id == item.productId }
                    val code = item.skuSnapshot.ifBlank { product?.sku.orEmpty() }
                    val description = item.productDescriptionSnapshot.ifBlank { product?.description.orEmpty() }
                    d.detailCard(
                        "${index + 1}. ${listOf(code, item.productName).filter { it.isNotBlank() }.joinToString(" • ")}",
                        listOf("Variante: ${item.variant}", "Colore: ${item.color}").filterNot { it.endsWith(": ") }.joinToString(" • "),
                        "Q.TÀ ${item.quantity}",
                        ORANGE
                    )
                    d.paragraph("Tessuto / articolo", description, CYAN)
                    d.paragraph("Taglie e quantità", item.sizeBreakdown, MAGENTA)
                    d.paragraph("Nomi, numeri e personalizzazioni", item.personalization, ORANGE)
                    d.paragraph("Note tecniche di produzione", item.productionNotes, YELLOW)
                }
                d.section("Controllo finale", GREEN)
                d.infoRow("Totale pezzi", quantity.toString(), GREEN)
                d.infoRow("Carta stampata collegata", "${"%.1f".format(Locale.ITALY, paperMeters)} mt", MAGENTA)
                d.infoRow("Allegati disponibili in app", order.attachments.size.toString())
                d.paragraph("Verifiche", "Controllare quantità, taglie, colori, nomi, numeri, sponsor, loghi, grafica approvata e confezionamento prima di avviare la produzione.", GREEN)
            }

            ZenthaExportProfile.COMPLETE -> {
                d.metricRow(listOf(
                    Triple("Ricavi", eur(revenue), GREEN),
                    Triple("Costi", eur(totalCosts), YELLOW),
                    Triple("Utile", eur(profit), if (profit >= 0) GREEN else RED)
                ))
                d.barChart("Analisi economica ordine", listOf("Ricavi" to revenue, "Costi prodotti" to productCosts, "Costi extra" to extra, "Utile" to profit), true)
                d.infoRow("Margine sul venduto", if (revenue > 0) "${"%.1f".format(Locale.ITALY, profit / revenue * 100.0)}%" else "-")
                d.infoRow("Quota A (${order.splitPercentA}%)", eur(profit * order.splitPercentA / 100.0))
                d.infoRow("Quota B (${order.splitPercentB}%)", eur(profit * order.splitPercentB / 100.0))
                d.infoRow("Stampa Chromia", "${"%.1f".format(Locale.ITALY, paperMeters)} mt × ${eur(order.chromiaRatePerMeter)}/mt = ${eur(chromiaAmount)}", MAGENTA)
                d.section("Analisi articoli", MAGENTA)
                items.forEachIndexed { index, item ->
                    val product = products.firstOrNull { it.id == item.productId }
                    val code = item.skuSnapshot.ifBlank { product?.sku.orEmpty() }
                    val description = item.productDescriptionSnapshot.ifBlank { product?.description.orEmpty() }
                    d.detailCard(
                        "${index + 1}. ${listOf(code, item.productName).filter { it.isNotBlank() }.joinToString(" • ")}",
                        "Q.tà ${item.quantity} • vendita ${eur(item.unitPrice)} • costo ${eur(item.unitCost)} • margine ${eur(item.margin)}",
                        eur(item.revenue),
                        if (item.margin >= 0) GREEN else RED
                    )
                    d.paragraph("Descrizione / tessuto", description, CYAN)
                    d.paragraph("Taglie", item.sizeBreakdown, MAGENTA)
                    d.paragraph("Variante e colore", listOf(item.variant, item.color).filter { it.isNotBlank() }.joinToString(" • "))
                    d.paragraph("Personalizzazioni", item.personalization, ORANGE)
                    d.paragraph("Note produzione", item.productionNotes, YELLOW)
                    if (product?.hasCostBreakdown == true) {
                        d.section("Costo unitario • ${product.sku}", YELLOW)
                        product.costComponents().forEach { (label, value) -> d.infoRow(label, eur(value)) }
                        d.infoRow("Totale costo unitario", eur(product.standardCost), YELLOW)
                        d.infoRow("Listino", "${product.priceListName} • ${eur(product.standardPrice)}")
                    }
                }
                if (extras.isNotEmpty()) {
                    d.section("Costi extra", YELLOW)
                    extras.forEach { d.detailCard(it.label, "Costo interno ordine", eur(it.amount), YELLOW) }
                }
                d.section("Incassi", CYAN)
                d.infoRow("Totale ordine", eur(revenue), GREEN)
                d.infoRow("Incassato", eur(paid), GREEN)
                d.infoRow("Residuo", eur((revenue - paid).coerceAtLeast(0.0)), if (revenue > paid) YELLOW else GREEN)
                payments.sortedBy { it.date }.forEach { payment -> d.detailCard(payment.date, paymentMethodIt(payment.method), eur(payment.amount), GREEN) }
                d.paragraph("Note ordine", order.notes)
                d.images(order.attachments)
            }
        }
        d.finish(file)
        sharePdf(context, file, "Esporta ordine Zentha • ${profileLabel(profile)}")
    }

    private fun workStatusIt(v: WorkStatus): String = when (v) {
        WorkStatus.DRAFT -> "Bozza"; WorkStatus.RECEIVED, WorkStatus.IN_PROCESS -> "In processo"; WorkStatus.TRANSFERRED_TO_PRODUCTION -> "Trasferito in produzione"; WorkStatus.WAITING_MATERIAL -> "Attesa materiale"; WorkStatus.WAITING_CLIENT -> "Attesa cliente"; WorkStatus.IN_PRODUCTION, WorkStatus.IN_PROGRESS -> "In produzione"; WorkStatus.PRINTED -> "Stampato"; WorkStatus.TRANSFERRED -> "Trasferito"; WorkStatus.READY -> "Pronto"; WorkStatus.COMPLETED -> "Completato"; WorkStatus.DELIVERED -> "Evaso"; WorkStatus.CLOSED -> "Chiuso"; WorkStatus.ON_HOLD -> "Sospeso"; WorkStatus.BLOCKED -> "Bloccato"; WorkStatus.CANCELLED -> "Annullato"
    }
    private fun timelineCategoryIt(v: TimelineCategory) = when (v) { TimelineCategory.NOTE -> "Nota"; TimelineCategory.CLIENT -> "Cliente"; TimelineCategory.INTERNAL_PROBLEM -> "Problema interno"; TimelineCategory.GRAPHICS -> "Grafica"; TimelineCategory.FILE_ERROR -> "File errato"; TimelineCategory.MATERIAL -> "Materiale"; TimelineCategory.SAMPLE -> "Campionatura"; TimelineCategory.APPROVAL -> "Approvazione"; TimelineCategory.PRODUCTION -> "Produzione"; TimelineCategory.ORDER_CHANGE -> "Modifica ordine"; TimelineCategory.DELAY -> "Ritardo"; TimelineCategory.DELIVERY -> "Consegna"; TimelineCategory.STATUS_CHANGE -> "Cambio stato" }
    private fun timelineResponsibilityIt(v: TimelineResponsibility) = when (v) { TimelineResponsibility.CLIENT -> "Cliente"; TimelineResponsibility.INTERNAL -> "Interna"; TimelineResponsibility.SUPPLIER -> "Fornitore"; TimelineResponsibility.NONE -> "Nessuna" }
    private fun timelineSeverityIt(v: TimelineSeverity) = when (v) { TimelineSeverity.INFO -> "Informativa"; TimelineSeverity.LOW -> "Bassa"; TimelineSeverity.MEDIUM -> "Media"; TimelineSeverity.HIGH -> "Alta"; TimelineSeverity.BLOCKING -> "Bloccante" }
    private fun economicStatusIt(v: EconomicWorkStatus) = when (v) { EconomicWorkStatus.OPEN -> "Aperto"; EconomicWorkStatus.COMPLETED -> "Completato"; EconomicWorkStatus.PAID -> "Incassato"; EconomicWorkStatus.CANCELLED -> "Annullato" }
    private fun zenthaStatusIt(v: ZenthaOrderStatus) = when (v) {
        ZenthaOrderStatus.DRAFT -> "Bozza"
        ZenthaOrderStatus.CONFIRMED -> "Confermato"
        ZenthaOrderStatus.GRAPHICS -> "Grafica"
        ZenthaOrderStatus.WAITING_APPROVAL -> "Attesa approvazione"
        ZenthaOrderStatus.PRODUCTION -> "In produzione"
        ZenthaOrderStatus.READY -> "Pronto"
        ZenthaOrderStatus.DELIVERED -> "Consegnato"
        ZenthaOrderStatus.CLOSED -> "Chiuso"
        ZenthaOrderStatus.CANCELLED -> "Annullato"
    }
    private fun paymentMethodIt(v: PaymentMethod) = when (v) {
        PaymentMethod.CASH -> "Contanti"
        PaymentMethod.BANK_TRANSFER -> "Bonifico"
        PaymentMethod.CARD -> "Carta"
        PaymentMethod.CHECK -> "Assegno"
        PaymentMethod.OTHER -> "Altro"
        PaymentMethod.UNSPECIFIED -> "Metodo non specificato"
    }
    private fun profileLabel(v: ZenthaExportProfile) = when (v) {
        ZenthaExportProfile.ACCOUNTING -> "Contabilità"
        ZenthaExportProfile.PRODUCTION -> "Produzione"
        ZenthaExportProfile.COMPLETE -> "Completo analitico"
    }
    private fun localizeStatusTokens(text: String): String { var result = text; WorkStatus.entries.sortedByDescending { it.name.length }.forEach { st -> result = result.replace(Regex("\\b${Regex.escape(st.name)}\\b"), workStatusIt(st)) }; return result }

    private fun wrap(text: String, width: Int): List<String> {
        if (text.isBlank()) return emptyList()
        val out = mutableListOf<String>(); var line = ""
        text.replace('\n', ' ').trim().split(Regex("\\s+")).forEach { word -> val next = if (line.isBlank()) word else "$line $word"; if (next.length > width && line.isNotBlank()) { out += line; line = word } else line = next }
        if (line.isNotBlank()) out += line; return out
    }
    private fun outFile(context: Context, name: String): File = File(context.cacheDir, "shared").apply { mkdirs() }.resolve(name)
    private fun sharePdf(context: Context, file: File, chooser: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, chooser))
    }
    private fun eur(v: Double) = NumberFormat.getCurrencyInstance(Locale.ITALY).format(v)
}
