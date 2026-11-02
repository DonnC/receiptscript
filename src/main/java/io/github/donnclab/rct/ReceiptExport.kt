package io.github.donnclab.rct

import android.graphics.Bitmap

/**
 * Encoded export output. Saving, `FileProvider` and sharing stay in your app.
 *
 * ```kotlin
 * val file = receipt.toPdf()
 * File(cacheDir, "receipt.${file.extension}").writeBytes(file.bytes)
 * ```
 */
public class ReceiptFile internal constructor(
    public val bytes: ByteArray,
    /** For example `image/png` or `application/pdf`. */
    public val mimeType: String,
    /** File extension without the dot, for example `png`. */
    public val extension: String
)

public enum class ReceiptImageFormat(
    internal val compression: Bitmap.CompressFormat,
    public val mimeType: String,
    public val extension: String
) {
    PNG(Bitmap.CompressFormat.PNG, "image/png", "png"),
    JPEG(Bitmap.CompressFormat.JPEG, "image/jpeg", "jpg")
}

/**
 * PDF page settings. [pageHeightDots] is measured in receipt dots; [dotsPerInch] converts dots to
 * PDF points (203 DPI is typical for thermal printers, so the PDF prints at real size).
 */
public data class ReceiptPdfOptions(val pageHeightDots: Int = 1120, val dotsPerInch: Int = 203) {
    init {
        require(pageHeightDots in 64..65536) { "PDF page height must be between 64 and 65536 dots" }
        require(dotsPerInch in 72..1200) { "PDF resolution must be between 72 and 1200 DPI" }
    }
}
