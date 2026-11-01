package examples.android

import android.graphics.Bitmap
import zw.co.donnclab.rct.escpos.MonochromeImage

/** Keep Bitmap decoding and ownership in the Android application. The driver receives neutral pixels. */
fun Bitmap.toReceiptImage(threshold: Int = 160): MonochromeImage {
    val pixels = IntArray(width * height)
    getPixels(pixels, 0, width, 0, 0, width, height)
    return MonochromeImage.fromArgb(width, height, pixels, threshold)
}
