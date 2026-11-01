package examples

import zw.co.donnclab.rct.ReceiptDriver
import zw.co.donnclab.rct.ReceiptBlock
import zw.co.donnclab.rct.ReceiptTextStyle
import zw.co.donnclab.rct.RctAlign

/** Host contract: implement it with your SDK or connection library. */
interface TerminalPrinter {
    fun text(value: String, alignment: RctAlign, style: ReceiptTextStyle)
    fun keyValue(key: String, value: String, style: ReceiptTextStyle)
    fun table(block: ReceiptBlock.Table)
    fun divider(pattern: String, length: Int)
    fun row(columns: List<String>)
    fun image(reference: String, width: Int?, height: Int?)
    fun qr(content: String, size: Int)
    fun barcode(block: ReceiptBlock.Barcode)
    fun feed(lines: Int)
    val hasCutter: Boolean
    fun cutPaper()

    /** Await the SDK completion callback, throwing its paper/cover/thermal/device errors. */
    suspend fun awaitCompletion()
}

/** Dispatches resolved blocks. The driver decides native commands or explicit fallbacks. */
class BuiltInPrinterDriver(private val driver: TerminalPrinter) : ReceiptDriver {
    override val supportsCut: Boolean get() = driver.hasCutter
    override fun cutPaper() {
        check(supportsCut) { "This terminal has no cutter" }
        driver.cutPaper()
    }
    override fun printText(block: ReceiptBlock.Text) = driver.text(block.text, block.align, block.style)
    override fun printKeyValue(block: ReceiptBlock.KeyValue) = driver.keyValue(block.key, block.value, block.style)
    override fun printTable(block: ReceiptBlock.Table) = driver.table(block)
    override fun printDivider(block: ReceiptBlock.Divider) = driver.divider(block.pattern, block.length)
    override fun printRow(block: ReceiptBlock.Row) = driver.row(block.columns)
    override fun printImage(block: ReceiptBlock.Image) = driver.image(block.source, block.width, block.height)
    override fun printQrCode(block: ReceiptBlock.QrCode) = driver.qr(block.content, block.size)
    override fun printBarcode(block: ReceiptBlock.Barcode) = driver.barcode(block)
    override fun feed(block: ReceiptBlock.Spacer) = driver.feed(block.lines)
}
