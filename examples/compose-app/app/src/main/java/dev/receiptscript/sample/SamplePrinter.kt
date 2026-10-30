package dev.receiptscript.sample

import zw.co.donnclab.rct.Receipt

/** App boundary, not a new engine API. Return only after your SDK's completion callback. */
fun interface SamplePrinter {
    suspend fun print(receipt: Receipt)
}

/**
 * Replace null with your SDK or ReceiptPrinter connection. The UI deliberately never reports
 * a successful print without a printer. Do blocking transport work on Dispatchers.IO.
 */
fun connectedPrinter(): SamplePrinter? = null
