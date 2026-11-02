package io.github.donnclab.rct

/**
 * Raster layout for previews, exports and bitmap printing. Sizes are in printer dots, not Android dp.
 *
 * Set [width] to your printer's printable width: 384 for most 58 mm printers, 576 for 80 mm.
 * The bundled `EscPosDriver` requires it to equal `EscPosProfile.printableWidthDots`.
 *
 * @param width printable width in dots.
 * @param padding blank margin on every side, in dots.
 * @param fontSize base text size in dots; a template's `meta.font` overrides it.
 * @param lineSpacing line height as a multiple of the font size.
 * @param bandHeight maximum height of each bitmap in [Receipt.bands]. Smaller bands use less memory.
 * @param maxHeight and [maxPixels] guard against runaway templates; rendering fails past them.
 */
public data class ReceiptOptions(
    val width: Int = 384,
    val padding: Int = 16,
    val fontSize: Float = 24f,
    val lineSpacing: Float = 1.18f,
    val bandHeight: Int = 1024,
    val maxHeight: Int = 32768,
    val maxPixels: Long = 16_777_216
) {
    init {
        require(width in 64..4096 && padding >= 0 && padding * 2 < width)
        require(fontSize.isFinite() && fontSize in 1f..256f)
        require(lineSpacing.isFinite() && lineSpacing in 1f..3f)
        require(bandHeight in 1..8192 && maxHeight in 1..65536 && maxPixels in 1..67_108_864)
    }
}
