package com.example.shoplog.core.util

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.util.Log
import androidx.core.content.FileProvider
import com.example.shoplog.data.local.entity.ShoppingItemEntity
import com.example.shoplog.data.local.entity.ShoppingListEntity
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object DocumentExporter {

    private const val TAG = "DocumentExporter"
    private const val AUTHORITY = "com.example.shoplog.fileprovider"

    /**
     * Exports a shopping list as a formatted PDF Document and launches the share sheet.
     */
    fun exportToPdf(
        context: Context,
        list: ShoppingListEntity,
        items: List<ShoppingItemEntity>,
        currencySymbol: String
    ) {
        try {
            val pdfDocument = PdfDocument()
            val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4 Size (595x842 pt)
            val page = pdfDocument.startPage(pageInfo)
            val canvas = page.canvas

            val paint = Paint().apply {
                isAntiAlias = true
                textSize = 12f
                color = Color.BLACK
            }

            var y = 50f
            val xMargin = 40f
            val pageWidth = 595f - (xMargin * 2)

            // Header Title
            paint.textSize = 22f
            paint.isFakeBoldText = true
            canvas.drawText("ShopLog Receipt: ${list.title}", xMargin, y, paint)
            y += 28f

            // Location & Date
            paint.textSize = 12f
            paint.isFakeBoldText = false
            paint.color = Color.DKGRAY

            val dateStr = SimpleDateFormat("MMMM d, yyyy - h:mm a", Locale.getDefault()).format(Date(list.createdAt))
            canvas.drawText("Date: $dateStr", xMargin, y, paint)
            y += 18f

            if (!list.location.isNullOrBlank()) {
                canvas.drawText("Location: ${list.location}", xMargin, y, paint)
                y += 18f
            }

            y += 10f

            // Table Header Line
            paint.color = Color.LTGRAY
            canvas.drawLine(xMargin, y, xMargin + pageWidth, y, paint)
            y += 18f

            paint.color = Color.BLACK
            paint.isFakeBoldText = true
            paint.textSize = 11f

            canvas.drawText("Item Name", xMargin, y, paint)
            canvas.drawText("Qty", xMargin + 280f, y, paint)
            canvas.drawText("Price", xMargin + 350f, y, paint)
            canvas.drawText("Subtotal", xMargin + 430f, y, paint)
            y += 12f

            paint.color = Color.LTGRAY
            canvas.drawLine(xMargin, y, xMargin + pageWidth, y, paint)
            y += 18f

            // Item Rows
            paint.color = Color.BLACK
            paint.isFakeBoldText = false

            items.forEach { item ->
                if (y > 780f) return@forEach // Safety boundary for page 1

                val statusPrefix = if (item.isPurchased) "[✓] " else ""
                val displayName = statusPrefix + item.name
                val truncatedName = if (displayName.length > 32) displayName.take(30) + "…" else displayName

                canvas.drawText(truncatedName, xMargin, y, paint)
                canvas.drawText(item.quantity.toString(), xMargin + 280f, y, paint)
                canvas.drawText(Money.format(item.unitPriceCents, "").trim(), xMargin + 350f, y, paint)
                canvas.drawText(Money.format(item.subtotalCents, "").trim(), xMargin + 430f, y, paint)

                y += 20f
            }

            y += 10f
            paint.color = Color.LTGRAY
            canvas.drawLine(xMargin, y, xMargin + pageWidth, y, paint)
            y += 24f

            // Total Section
            paint.color = Color.BLACK
            paint.textSize = 16f
            paint.isFakeBoldText = true
            val totalFormatted = "Total: ${Money.format(list.totalCents, currencySymbol)}"
            canvas.drawText(totalFormatted, xMargin + 280f, y, paint)

            pdfDocument.finishPage(page)

            // Save PDF File
            val cleanTitle = list.title.replace(Regex("[^a-zA-Z0-9]"), "_")
            val pdfFile = File(context.cacheDir, "${cleanTitle}_Receipt.pdf")
            FileOutputStream(pdfFile).use { out ->
                pdfDocument.writeTo(out)
            }
            pdfDocument.close()

            val contentUri = FileProvider.getUriForFile(context, AUTHORITY, pdfFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Download/Share PDF Receipt"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export PDF: ${e.message}", e)
        }
    }

    /**
     * Exports a shopping list as a CSV / Excel Spreadsheet (.csv) file and launches the share sheet.
     */
    fun exportToCsv(
        context: Context,
        list: ShoppingListEntity,
        items: List<ShoppingItemEntity>,
        currencySymbol: String
    ) {
        try {
            val sb = StringBuilder()
            sb.append("Item Name,Quantity,Unit Price ($currencySymbol),Subtotal ($currencySymbol),Purchased Status\n")

            items.forEach { item ->
                val cleanName = "\"${item.name.replace("\"", "\"\"")}\""
                val unitPrice = Money.format(item.unitPriceCents, "").trim()
                val subtotal = Money.format(item.subtotalCents, "").trim()
                val status = if (item.isPurchased) "Purchased" else "Pending"
                sb.append("$cleanName,${item.quantity},$unitPrice,$subtotal,$status\n")
            }

            val totalStr = Money.format(list.totalCents, currencySymbol).replace("\"", "\"\"")
            sb.append("\nTOTAL,,,,\"$totalStr\"\n")

            val cleanTitle = list.title.replace(Regex("[^a-zA-Z0-9]"), "_")
            val csvFile = File(context.cacheDir, "${cleanTitle}_Spreadsheet.csv")
            csvFile.writeText(sb.toString())

            val contentUri = FileProvider.getUriForFile(context, AUTHORITY, csvFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/csv"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Download/Share Excel CSV"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export CSV: ${e.message}", e)
        }
    }

    /**
     * Exports a shopping list as a Word / Text Document (.doc / .txt) and launches the share sheet.
     */
    fun exportToDocText(
        context: Context,
        list: ShoppingListEntity,
        items: List<ShoppingItemEntity>,
        currencySymbol: String
    ) {
        try {
            val dateStr = SimpleDateFormat("EEEE, MMMM d, yyyy - h:mm a", Locale.getDefault()).format(Date(list.createdAt))
            val sb = StringBuilder()

            sb.append("=========================================\n")
            sb.append(" SHOPLOG RECEIPT: ${list.title.uppercase()}\n")
            sb.append("=========================================\n")
            sb.append("Date: $dateStr\n")
            if (!list.location.isNullOrBlank()) {
                sb.append("Location: ${list.location}\n")
            }
            sb.append("-----------------------------------------\n\n")

            sb.append("ITEMS:\n")
            items.forEachIndexed { index, item ->
                val status = if (item.isPurchased) "[✓]" else "[ ]"
                val price = Money.format(item.subtotalCents, currencySymbol)
                sb.append("${index + 1}. $status ${item.name}\n")
                sb.append("   Quantity: ${item.quantity}  |  Price: ${Money.format(item.unitPriceCents, currencySymbol)}  |  Subtotal: $price\n\n")
            }

            sb.append("-----------------------------------------\n")
            sb.append("GRAND TOTAL: ${Money.format(list.totalCents, currencySymbol)}\n")
            sb.append("=========================================\n\n")
            sb.append("Generated via ShopLog Android App\n")

            val cleanTitle = list.title.replace(Regex("[^a-zA-Z0-9]"), "_")
            val docFile = File(context.cacheDir, "${cleanTitle}_Document.doc")
            docFile.writeText(sb.toString())

            val contentUri = FileProvider.getUriForFile(context, AUTHORITY, docFile)
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "application/msword"
                putExtra(Intent.EXTRA_STREAM, contentUri)
                putExtra(Intent.EXTRA_TEXT, sb.toString())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(shareIntent, "Download/Share Word Document"))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export Document: ${e.message}", e)
        }
    }
}
