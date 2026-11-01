package zw.co.donnclab.rct.android

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.escpos.EscPosDriver
import zw.co.donnclab.rct.escpos.EscPosProfile
import zw.co.donnclab.rct.escpos.MonochromeImage
import zw.co.donnclab.rct.escpos.PrinterConnection

/** Sends the preview's exact pixels over an established connection owned by the application. */
class EscPosReceiptPrinter(
    private val connection: PrinterConnection,
    private val profile: EscPosProfile = EscPosProfile.Portable58
) : ReceiptPrinter {
    private val mutex = Mutex()
    private val driver = EscPosDriver(profile)

    override suspend fun print(receipt: RasterReceipt) = withContext(Dispatchers.IO) {
        mutex.withLock {
            require(receipt.width == profile.printableWidthDots) { "Receipt width must match the printer" }
            val bands = receipt.bands.map { bitmap ->
                val pixels = IntArray(bitmap.width * bitmap.height)
                bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                MonochromeImage.fromArgb(bitmap.width, bitmap.height, pixels)
            }
            connection.send(driver.renderRaster(bands))
        }
    }
}
