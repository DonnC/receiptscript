package dev.receiptscript.sample

import android.content.Context
import android.graphics.BitmapFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.Receipt
import zw.co.donnclab.rct.escpos.EscPosDriver
import zw.co.donnclab.rct.escpos.EscPosProfile
import zw.co.donnclab.rct.escpos.MonochromeImage
import zw.co.donnclab.rct.escpos.PrinterConnection
import zw.co.donnclab.rct.escpos.ReceiptPrinter

/** App boundary, not a new engine API. Return only after your SDK's completion callback. */
fun interface SamplePrinter {
    suspend fun print(receipt: Receipt)
}

/**
 * Replace null with your SDK or ReceiptPrinter connection. The UI deliberately never reports
 * a successful print without a printer. Do blocking transport work on Dispatchers.IO.
 */
fun connectedPrinter(context: Context): SamplePrinter? = null

/** Reuses the preview's logo assets. The host owns connection permissions and lifecycle. */
fun escPosPrinter(context: Context, connection: PrinterConnection,
                  profile: EscPosProfile = EscPosProfile()): SamplePrinter {
    val assets = context.applicationContext.assets
    val driver = EscPosDriver(profile, images = { source ->
        assets.open("receipts/${source.removePrefix("asset:")}").use { stream ->
            val bitmap = requireNotNull(BitmapFactory.decodeStream(stream)) { "Unreadable image: $source" }
            try {
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                MonochromeImage.fromArgb(bitmap.width, bitmap.height, pixels)
            } finally { bitmap.recycle() }
        }
    })
    val printer = ReceiptPrinter(driver, connection)
    return SamplePrinter { receipt -> withContext(Dispatchers.IO) { printer.print(receipt) } }
}
