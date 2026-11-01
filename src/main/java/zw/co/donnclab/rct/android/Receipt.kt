package zw.co.donnclab.rct.android

import android.graphics.Bitmap
import zw.co.donnclab.rct.RctMeta
import zw.co.donnclab.rct.ReceiptBlock

/**
 * A ready-to-use receipt. Preview, export and print share its already laid-out pixels.
 * Bands are exposed for printer integrations; do not resize or recycle them while in use.
 * This object retains neither an Android context nor a printer connection.
 */
class Receipt internal constructor(
    val width: Int,
    bands: List<Bitmap>,
    val name: String = "receipt",
    val meta: RctMeta? = null,
    blocks: List<ReceiptBlock> = emptyList()
) {
    val bands: List<Bitmap> = bands.toList()
    val height: Int = bands.sumOf { it.height }
    val blocks: List<ReceiptBlock> = blocks.toList()

    suspend fun print(driver: ReceiptDriver) = driver.print(this)
    fun toBitmap(): Bitmap = ReceiptExporter.toBitmap(this)
    fun toImageBytes(format: ReceiptImageFormat = ReceiptImageFormat.PNG, quality: Int = 100): ReceiptFile =
        ReceiptExporter.toImageBytes(this, format, quality)
    fun toPdfBytes(options: ReceiptPdfOptions = ReceiptPdfOptions()): ReceiptFile =
        ReceiptExporter.toPdfBytes(this, options)
}
