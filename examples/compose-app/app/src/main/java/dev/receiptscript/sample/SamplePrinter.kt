package dev.receiptscript.sample

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.android.RasterReceipt
import zw.co.donnclab.rct.escpos.EscPosDriver
import zw.co.donnclab.rct.escpos.EscPosProfile
import zw.co.donnclab.rct.escpos.MonochromeImage
import zw.co.donnclab.rct.escpos.PrinterConnection

/** Receives the exact immutable bands shown in the preview, not a receipt to lay out again. */
fun interface SamplePrinter {
    suspend fun print(receipt: RasterReceipt)
}

/** Replace null with your printer integration. SDK adapters should print the supplied bitmaps. */
fun connectedPrinter(context: Context): SamplePrinter? = null

/** Match the renderer width to the physical mechanism. No scaling or reflow occurs here. */
fun escPosPrinter(connection: PrinterConnection,
                  profile: EscPosProfile = EscPosProfile.Portable58): SamplePrinter {
    val driver = EscPosDriver(profile)
    val lock = Any()
    return SamplePrinter { receipt -> withContext(Dispatchers.IO) {
        require(receipt.width == profile.printableWidthDots) { "Preview width must match the connected printer" }
        synchronized(lock) {
            val bands = receipt.bands.map { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                MonochromeImage.fromArgb(bitmap.width, bitmap.height, pixels)
            }
            connection.send(driver.renderRaster(bands))
        }
    } }
}
