package com.spectro.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.spectro.app.data.ClientIssue
import com.spectro.app.data.IssueUpdate
import com.spectro.app.data.ProductionRecord
import com.spectro.app.data.RecordType
import com.spectro.app.data.WorkOrder
import java.io.File
import java.text.NumberFormat
import java.util.Locale

object ReportExporter {
    fun share(context: Context, r: ProductionRecord) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Spectro_Report_${r.date}_${r.id}.pdf")
        val pdf = PdfDocument(); val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 70, 35); textSize = 30f; isFakeBoldText = true }
        val h = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 15f; isFakeBoldText = true }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 13f }
        var y = 62f
        fun line(label: String, value: String) { c.drawText(label, 42f, y, h); c.drawText(value.take(58), 220f, y, p); y += 28f }
        c.drawColor(Color.WHITE); c.drawText("SPECTRO ERP", 42f, y, title); y += 45f
        line("Data", r.date); line("Cliente", r.clientName.ifBlank { "Evento interno" })
        if (r.type == RecordType.PRODUCTION) {
            line("Rif. ordine", r.orderRef.ifBlank { "-" }); line("Carta stampata", "${r.printedPaperMeters} mt (${r.plotter})"); line("Tessuto", r.fabricDisplayName); line("Tessuto lavorato", "${r.fabricUsedMeters} mt"); line("Fatturato", eur(r.grossRevenue)); line("Costi materie", eur(r.materialCostTotal)); line("Utile lordo", eur(r.grossProfit))
        } else { line("Evento", r.technicalNotes.ifBlank { "Evento negativo" }); line("Rimborsi / extra", eur(r.refundCost)); line("Costo tessuto", eur(r.fabricLossCost)); line("Perdita totale", eur(r.materialCostTotal)) }
        if (r.technicalNotes.isNotBlank()) { y += 10f; line("Note tecniche", r.technicalNotes) }
        pdf.finishPage(page); file.outputStream().use { pdf.writeTo(it) }; pdf.close(); sharePdf(context, file, "Condividi report Spectro")
    }

    fun shareWorkOrder(context: Context, w: WorkOrder) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Spectro_Ordine_${w.receivedDate}_${w.id}.pdf")
        val pdf = PdfDocument(); val page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create()); val c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 70, 35); textSize = 28f; isFakeBoldText = true }
        val h = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 14f; isFakeBoldText = true }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 12f }
        var y = 60f
        fun line(label: String, value: String) { c.drawText(label, 42f, y, h); c.drawText(value.take(62), 210f, y, p); y += 27f }
        fun paragraph(label: String, value: String) {
            if (value.isBlank()) return
            c.drawText(label, 42f, y, h); y += 22f
            value.chunked(74).forEach { c.drawText(it, 42f, y, p); y += 19f }
        }
        c.drawColor(Color.WHITE); c.drawText("SPECTRO ERP • ORDINE", 42f, y, title); y += 44f
        line("Cliente", w.client); line("Titolo", w.title); line("Ricevuto", w.receivedDate)
        line("Consegna", w.dueDate.ifBlank { "-" }); line("Stato", w.status.name); line("Priorità", w.priority.name); line("Blocco", w.blocker.name)
        line("Allegati", w.images.size.toString()); paragraph("Descrizione / note", w.description)
        pdf.finishPage(page); file.outputStream().use { pdf.writeTo(it) }; pdf.close(); sharePdf(context, file, "Condividi ordine Spectro")
    }

    fun shareIssue(context: Context, issue: ClientIssue, updates: List<IssueUpdate>) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "Spectro_Criticita_${issue.openedDate}_${issue.id}.pdf")
        val pdf = PdfDocument(); var pageNumber = 1; var page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()); var c = page.canvas
        val title = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(255, 70, 35); textSize = 27f; isFakeBoldText = true }
        val h = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 13f; isFakeBoldText = true }
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY; textSize = 12f }
        var y = 58f
        fun newPage() { pdf.finishPage(page); pageNumber++; page = pdf.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()); c = page.canvas; c.drawColor(Color.WHITE); y = 52f }
        fun lines(text: String, width: Int = 72): List<String> {
            val words = text.split(Regex("\\s+")); val out = mutableListOf<String>(); var line = ""
            words.forEach { w -> val next = if (line.isBlank()) w else "$line $w"; if (next.length > width) { if (line.isNotBlank()) out += line; line = w } else line = next }; if (line.isNotBlank()) out += line; return out
        }
        fun row(label: String, value: String) { if (y > 790) newPage(); c.drawText(label, 42f, y, h); c.drawText(value.take(62), 205f, y, p); y += 25f }
        fun paragraph(label: String, value: String) { if (value.isBlank()) return; if (y > 760) newPage(); c.drawText(label, 42f, y, h); y += 20f; lines(value).forEach { if (y > 790) newPage(); c.drawText(it, 42f, y, p); y += 18f }; y += 8f }
        c.drawColor(Color.WHITE); c.drawText("SPECTRO ERP • REPORT CRITICITÀ", 42f, y, title); y += 42f
        row("Cliente", issue.clientName); row("Ordine", issue.orderRef.ifBlank { "-" }); row("Titolo", issue.title); row("Data apertura", issue.openedDate); row("Categoria", issue.category.name); row("Responsabilità", issue.responsibility.name); row("Gravità", issue.severity.name); row("Stato", issue.status.name); row("Consegna iniziale", issue.expectedDeliveryDate.ifBlank { "-" }); row("Nuova consegna", issue.revisedDeliveryDate.ifBlank { "-" }); row("Ritardo documentato", "${issue.delayDays} giorni"); row("Prove allegate", issue.evidenceImages.size.toString())
        paragraph("Descrizione dei fatti", issue.description); paragraph("Note interne", issue.internalNotes)
        if (updates.isNotEmpty()) {
            if (y > 730) newPage(); c.drawText("CRONOLOGIA", 42f, y, h); y += 25f
            updates.sortedBy { it.date }.forEach { u -> if (y > 740) newPage(); c.drawText("${u.date} • ${u.images.size} allegati", 42f, y, h); y += 20f; lines(u.note).forEach { if (y > 790) newPage(); c.drawText(it, 55f, y, p); y += 18f }; y += 12f }
        }
        pdf.finishPage(page); file.outputStream().use { pdf.writeTo(it) }; pdf.close(); sharePdf(context, file, "Condividi prova criticità")
    }

    private fun sharePdf(context: Context, file: File, chooser: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "application/pdf"; putExtra(Intent.EXTRA_STREAM, uri); addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) }, chooser))
    }
    private fun eur(v: Double) = NumberFormat.getCurrencyInstance(Locale.ITALY).format(v)
}
