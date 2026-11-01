package zw.co.donnclab.rct.android

import android.graphics.Bitmap

/** Encoded content and its media type; storage, FileProvider and sharing remain host-owned. */
data class ReceiptFile(val bytes: ByteArray, val mimeType: String, val extension: String)

enum class ReceiptImageFormat(internal val compression: Bitmap.CompressFormat, val mimeType: String, val extension: String) {
    PNG(Bitmap.CompressFormat.PNG, "image/png", "png"),
    JPEG(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
}

/** Heights are in source dots; PDF pages are converted to points using the requested print DPI. */
data class ReceiptPdfOptions(val pageHeightDots: Int = 1120, val dotsPerInch: Int = 203) {
    init {
        require(pageHeightDots in 64..65536) { "PDF page height must be between 64 and 65536 dots" }
        require(dotsPerInch in 72..1200) { "PDF resolution must be between 72 and 1200 DPI" }
    }
}
