package zw.co.donnclab.rct

/**
 * Convenience adapter for SDKs exposing individual print operations. Implement each operation
 * with SDK calls or an explicit unsupported-feature error. Return from render means the calls
 * completed; asynchronous SDK completion must be observed by the host application.
 *
 * Byte-producing drivers implement ReceiptAdapter<ByteArray> instead.
 */
interface ReceiptDriver : ReceiptAdapter<Unit> {
    val supportsCut: Boolean get() = false

    /** Explicit finishing operation; unsupported devices fail rather than silently ignoring it. */
    fun cutPaper() { throw UnsupportedOperationException("This printer has no supported cutter") }

    fun printText(block: ReceiptBlock.Text)
    fun printKeyValue(block: ReceiptBlock.KeyValue)
    fun printDivider(block: ReceiptBlock.Divider)
    fun printRow(block: ReceiptBlock.Row)
    fun printTable(block: ReceiptBlock.Table)
    fun printImage(block: ReceiptBlock.Image)
    fun printQrCode(block: ReceiptBlock.QrCode)
    fun printBarcode(block: ReceiptBlock.Barcode)
    fun feed(block: ReceiptBlock.Spacer)

    override fun render(receipt: Receipt) {
        receipt.blocks.forEach { block -> when (block) {
            is ReceiptBlock.Text -> printText(block)
            is ReceiptBlock.KeyValue -> printKeyValue(block)
            is ReceiptBlock.Divider -> printDivider(block)
            is ReceiptBlock.Row -> printRow(block)
            is ReceiptBlock.Table -> printTable(block)
            is ReceiptBlock.Image -> printImage(block)
            is ReceiptBlock.QrCode -> printQrCode(block)
            is ReceiptBlock.Barcode -> printBarcode(block)
            is ReceiptBlock.Spacer -> feed(block)
        } }
    }
}
