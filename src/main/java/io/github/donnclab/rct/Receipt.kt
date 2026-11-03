package io.github.donnclab.rct

import android.graphics.Bitmap

/**
 * A rendered receipt: the printer-neutral [blocks] and the bitmap [bands] laid out from them.
 *
 * Preview, export and printing all use this one object, so what you show is what prints:
 * - show it with `ReceiptPreview(receipt)` in Compose
 * - export it with [toBitmap], [toImage] or [toPdf]
 * - print it with [ReceiptScript.print] or any [ReceiptDriver]
 *
 * A receipt holds no `Context` or printer connection and can be kept and printed again later.
 * Treat [bands] as read-only: do not recycle or modify them while the receipt is in use.
 */
public class Receipt internal constructor(
    internal val document: ReceiptDocument,
    /** Width of every band, in printer dots. Equal to [ReceiptOptions.width]. */
    public val width: Int,
    bands: List<Bitmap>,
    internal val images: Map<String, Bitmap>,
    internal val displayList: ReceiptDisplayList? = null
) {
    /** The name declared by `receipt "<name>"` in the template. */
    public val name: String get() = document.name

    /** Layout defaults from the template's `meta:` section, if any. */
    public val meta: ReceiptMeta? get() = document.meta

    /** Content in print order, for drivers that map blocks to SDK calls. */
    public val blocks: List<ReceiptBlock> get() = document.blocks

    /**
     * The laid-out receipt as black-and-white bitmaps, top to bottom, each [width] dots wide and at
     * most [ReceiptOptions.bandHeight] tall. Print them in order without scaling or extra feed.
     */
    public val bands: List<Bitmap> = bands.toList()

    /** Total height of all [bands], in dots. */
    public val height: Int = this.bands.sumOf { it.height }

    /** The whole receipt as one new bitmap. You own it; recycle it when done. */
    public fun toBitmap(): Bitmap = ReceiptExporter.toBitmap(this)

    /** Encodes the receipt as PNG (default) or JPEG, ready to save or share. */
    public fun toImage(format: ReceiptImageFormat = ReceiptImageFormat.PNG, quality: Int = 100): ReceiptFile =
        ReceiptExporter.toImage(this, format, quality)

    /** Encodes the receipt as a PDF, split into pages for long receipts. */
    public fun toPdf(options: ReceiptPdfOptions = ReceiptPdfOptions()): ReceiptFile =
        ReceiptExporter.toPdf(this, options)
}
