package examples

import android.graphics.Bitmap
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.ReceiptAlign
import io.github.donnclab.rct.ReceiptBlock
import io.github.donnclab.rct.ReceiptDriver
import io.github.donnclab.rct.ReceiptFontSize

// Compiled examples for docs/guides/drivers.md. Keep them in sync with the guide.

/** Stand-in for a vendor printer SDK, such as the one built into a POS terminal. */
interface TerminalSdk {
    fun setAlignment(alignment: Int)
    fun printText(text: String, bold: Boolean, large: Boolean)
    fun printColumns(columns: List<String>, weights: List<Float>)
    fun printQr(content: String, size: Int)
    fun printBarcode(content: String, width: Int, height: Int, format: String)
    fun printBitmap(bitmap: Bitmap)
    fun feed(lines: Int)

    /** Suspends until the SDK's completion callback fires; throws on paper-out and similar errors. */
    suspend fun awaitDone()
}

/** Option 1: map each block to the SDK's own commands. */
class TerminalBlockDriver(private val sdk: TerminalSdk) : ReceiptDriver {
    override suspend fun print(receipt: Receipt) {
        for (block in receipt.blocks) {
            when (block) {
                is ReceiptBlock.Text -> {
                    sdk.setAlignment(block.align.toSdk())
                    sdk.printText(block.text, block.style.bold, block.style.size == ReceiptFontSize.BIG)
                }
                is ReceiptBlock.KeyValue -> sdk.printColumns(listOf(block.key, block.value), listOf(1f, 1f))
                is ReceiptBlock.Row -> sdk.printColumns(block.columns, block.columns.map { 1f })
                is ReceiptBlock.Table -> {
                    val weights = block.weights ?: block.headers.map { 1f }
                    if (block.headers.isNotEmpty()) sdk.printColumns(block.headers, weights)
                    block.rows.forEach { sdk.printColumns(it, block.weights ?: it.map { 1f }) }
                }
                is ReceiptBlock.Divider -> sdk.printText(block.pattern.repeat(block.length).take(block.length), bold = false, large = false)
                is ReceiptBlock.Spacer -> sdk.feed(block.lines)
                is ReceiptBlock.QrCode -> sdk.printQr(block.content, block.size)
                is ReceiptBlock.Barcode -> sdk.printBarcode(block.content, block.width, block.height, block.format.id)
                // Image blocks carry a reference; the resolved pixels are in the rendered bands.
                // SDKs without image support can skip them, or print the bands instead.
                is ReceiptBlock.Image -> Unit
            }
        }
        sdk.awaitDone()
    }

    private fun ReceiptAlign.toSdk(): Int = when (this) {
        ReceiptAlign.LEFT -> 0
        ReceiptAlign.CENTER -> 1
        ReceiptAlign.RIGHT -> 2
    }
}

/** Option 2: print the rendered bitmaps. Output matches the preview exactly. */
fun terminalBitmapDriver(sdk: TerminalSdk): ReceiptDriver = ReceiptDriver { receipt ->
    receipt.bands.forEach(sdk::printBitmap)
    sdk.awaitDone()
}
