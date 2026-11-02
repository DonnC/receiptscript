package io.github.donnclab.rct.escpos

import android.graphics.Bitmap
import io.github.donnclab.rct.Receipt
import io.github.donnclab.rct.ReceiptDriver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * A ready-made [ReceiptDriver] for ESC/POS printers, the command set used by most portable
 * Bluetooth and USB thermal printers.
 *
 * ```kotlin
 * val driver = EscPosDriver(BluetoothPrinterConnection(device), EscPosProfile.Portable58)
 * val rct = ReceiptScript(context, driver)
 * ```
 *
 * The whole job is encoded before the first byte is sent, so a bad template or image cannot leave
 * a half-printed receipt.
 *
 * @param connection where bytes go. See [PrinterConnection] for the bundled transports.
 * @param profile the printer's width, fonts and capabilities. Its `printableWidthDots` must equal
 *   the `ReceiptOptions.width` used to render (384 for [EscPosProfile.Portable58], 576 for
 *   [EscPosProfile.Receipt80]).
 * @param mode [EscPosMode.RASTER] prints the preview pixels exactly; [EscPosMode.TEXT] uses the
 *   printer's built-in fonts, which is faster but looks different from the preview.
 */
public class EscPosDriver(
    private val connection: PrinterConnection,
    private val profile: EscPosProfile = EscPosProfile.Portable58,
    private val mode: EscPosMode = EscPosMode.RASTER
) : ReceiptDriver {
    private val mutex = Mutex()

    override suspend fun print(receipt: Receipt): Unit = withContext(Dispatchers.IO) {
        val bytes = when (mode) {
            EscPosMode.RASTER -> {
                require(receipt.width == profile.printableWidthDots) {
                    "Receipt is ${receipt.width} dots wide but the printer prints ${profile.printableWidthDots}; " +
                        "use ReceiptOptions(width = ${profile.printableWidthDots})"
                }
                EscPosEncoder(profile).encodeRaster(receipt.bands.map { it.toMonochrome() })
            }
            EscPosMode.TEXT -> {
                val images = receipt.images.mapValues { (_, bitmap) -> bitmap.toMonochrome() }
                EscPosEncoder(profile) { images[it] }.encode(receipt.document)
            }
        }
        mutex.withLock { connection.send(bytes) }
    }
}

/** How [EscPosDriver] sends a receipt. */
public enum class EscPosMode {
    /** Sends the rendered bitmaps. Output matches the preview exactly. The default. */
    RASTER,

    /** Sends text with the printer's own fonts; images and codes are still raster. Faster, smaller jobs. */
    TEXT
}

internal fun Bitmap.toMonochrome(): MonochromeImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return MonochromeImage.fromArgb(width, height, pixels)
}
