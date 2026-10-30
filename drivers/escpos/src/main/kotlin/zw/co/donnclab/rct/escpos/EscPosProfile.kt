package zw.co.donnclab.rct.escpos

import java.nio.charset.Charset

/** Explicit device assumptions. Presets describe common mechanisms, not certified models. */
data class EscPosProfile(
    val printableWidthDots: Int,
    val fontAWidthDots: Int = 12,
    val fontAHeightDots: Int = 24,
    val fontBWidthDots: Int = 9,
    val supportsFontB: Boolean = true,
    val charset: Charset = Charsets.US_ASCII,
    val codeTable: Int = 0,
    val supportsRaster: Boolean = true,
    val rasterSliceHeight: Int = 128,
    val supportsCut: Boolean = false,
    val cutAfterPrint: Boolean = false,
    val feedAfterPrint: Int = 3
) {
    init {
        require(printableWidthDots in 32..2048)
        require(printableWidthDots % 8 == 0) { "Printable width must be byte-aligned for raster output" }
        require(fontAWidthDots in 1..64 && fontAHeightDots in 1..128 && fontBWidthDots in 1..64)
        require(charset.newEncoder().maxBytesPerChar() <= 1f) { "Native text requires a single-byte charset" }
        require(codeTable in 0..255 && rasterSliceHeight in 1..1024 && feedAfterPrint in 0..255)
        require(!cutAfterPrint || supportsCut) { "This profile does not support cutting" }
    }

    companion object {
        /** Common 58 mm / 384-dot portable ESC/POS printers; no automatic cut. */
        val Portable58 = EscPosProfile(printableWidthDots = 384)

        /** Common 80 mm / 576-dot receipt printers; cutting remains opt-in. */
        val Receipt80 = EscPosProfile(printableWidthDots = 576, supportsCut = true)
    }
}
