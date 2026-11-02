package io.github.donnclab.rct

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import java.io.ByteArrayOutputStream
import kotlin.math.ceil

/** Exports an existing layout, without recalculating text, tables, fonts or code images. */
internal object ReceiptExporter {
    /** Returns a caller-owned bitmap. Prefer PDF for receipts exceeding the flattening budget. */
    fun toBitmap(receipt: Receipt): Bitmap {
        require(receipt.width.toLong() * receipt.height <= MAX_IMAGE_PIXELS) {
            "Receipt exceeds the image export budget; export PDF instead"
        }
        return Bitmap.createBitmap(receipt.width, receipt.height, Bitmap.Config.ARGB_8888).also { bitmap ->
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.WHITE)
            var top = 0
            receipt.bands.forEach { band ->
                canvas.drawBitmap(band, 0f, top.toFloat(), null)
                top += band.height
            }
        }
    }

    fun toImage(
        receipt: Receipt,
        format: ReceiptImageFormat = ReceiptImageFormat.PNG,
        quality: Int = 100
    ): ReceiptFile {
        require(quality in 0..100) { "Image quality must be between 0 and 100" }
        val bitmap = toBitmap(receipt)
        try {
            val bytes = ByteArrayOutputStream().use { output ->
                check(bitmap.compress(format.compression, quality, output)) { "Image encoding failed" }
                output.toByteArray()
            }
            return ReceiptFile(bytes, format.mimeType, format.extension)
        } finally {
            bitmap.recycle()
        }
    }

    /** Streams bands into PDF pages; a long receipt never needs a single tall bitmap. */
    fun toPdf(receipt: Receipt, options: ReceiptPdfOptions = ReceiptPdfOptions()): ReceiptFile {
        val document = PdfDocument()
        try {
            val scale = PDF_POINTS_PER_INCH / options.dotsPerInch
            pages(receipt, options).forEachIndexed { index, part ->
                val page = document.startPage(PdfDocument.PageInfo.Builder(
                    ceil(receipt.width * scale).toInt(), ceil(part.height * scale).toInt(), index + 1
                ).create())
                try {
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.scale(scale, scale)
                    var top = 0
                    receipt.bands.forEach { band ->
                        if (top < part.offset + part.height && top + band.height > part.offset) {
                            page.canvas.drawBitmap(band, 0f, (top - part.offset).toFloat(), null)
                        }
                        top += band.height
                    }
                } finally {
                    document.finishPage(page)
                }
            }
            val bytes = ByteArrayOutputStream().use { output -> document.writeTo(output); output.toByteArray() }
            return ReceiptFile(bytes, "application/pdf", "pdf")
        } finally {
            document.close()
        }
    }

    internal fun pages(receipt: Receipt, options: ReceiptPdfOptions): List<PdfPart> =
        (0 until receipt.height step options.pageHeightDots).map { offset ->
            PdfPart(offset, minOf(options.pageHeightDots, receipt.height - offset))
        }

    internal data class PdfPart(val offset: Int, val height: Int)

    private const val MAX_IMAGE_PIXELS = 16_777_216L
    private const val PDF_POINTS_PER_INCH = 72f
}
