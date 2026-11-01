package zw.co.donnclab.rct.android

/** Implement with a printer SDK. Return after hardware completion; propagate faults without retry. */
fun interface ReceiptPrinter {
    suspend fun print(receipt: RasterReceipt)
}
