package zw.co.donnclab.rct.android

/** Send a receipt through your SDK or connection; await completion and propagate printer faults. */
fun interface ReceiptDriver {
    suspend fun print(receipt: Receipt)
}
