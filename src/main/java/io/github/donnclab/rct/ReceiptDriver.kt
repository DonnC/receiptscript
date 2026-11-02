package io.github.donnclab.rct

/**
 * Connects ReceiptScript to your printer. This is the one place your printer SDK appears.
 *
 * A [Receipt] gives you two ways to print, so pick whichever your SDK supports:
 *
 * **Map blocks to SDK calls** when the SDK has its own text, table or barcode commands.
 * Use an exhaustive `when` so the compiler tells you if a block type is not handled:
 * ```kotlin
 * val driver = ReceiptDriver { receipt ->
 *     for (block in receipt.blocks) when (block) {
 *         is ReceiptBlock.Text -> sdk.printText(block.text, block.align.ordinal, block.style.bold)
 *         is ReceiptBlock.KeyValue -> sdk.printColumns(block.key, block.value)
 *         // …one branch per block type
 *     }
 *     sdk.awaitDone()
 * }
 * ```
 *
 * **Print the rendered bitmaps** when the SDK prints images. Output matches the preview exactly:
 * ```kotlin
 * val driver = ReceiptDriver { receipt -> receipt.bands.forEach { sdk.printBitmap(it) } }
 * ```
 *
 * For ESC/POS printers (most Bluetooth and USB thermal printers) use the bundled
 * [EscPosDriver][io.github.donnclab.rct.escpos.EscPosDriver] instead of writing one.
 *
 * Contract: suspend until the SDK reports completion, and throw on printer errors (paper out,
 * cover open). Do not retry inside the driver. [ReceiptScript.print] calls it on a background
 * dispatcher and never runs two jobs at once on the same instance.
 */
public fun interface ReceiptDriver {
    public suspend fun print(receipt: Receipt)
}
