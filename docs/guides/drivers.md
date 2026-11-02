# Writing a driver

A `ReceiptDriver` is the only code that knows about your printer. It has one function:

```kotlin
public fun interface ReceiptDriver {
    public suspend fun print(receipt: Receipt)
}
```

Pass it to `ReceiptScript(context, driver)` and call `receipts.print(receipt)`.

> Printing to an ESC/POS printer (most Bluetooth and USB thermal printers)? You do not need to write
> a driver: use [`EscPosDriver`](printing.md).

## Choose an approach

A `Receipt` carries the same receipt in two forms, so a driver can use whichever its SDK supports.

| | Print the bitmaps | Map the blocks |
| --- | --- | --- |
| Uses | `receipt.bands`: black-and-white `Bitmap`s, top to bottom | `receipt.blocks`: `Text`, `KeyValue`, `Table`, `QrCode`… |
| Output | identical to the preview | the printer's own fonts and layout |
| Needs from the SDK | a "print bitmap" call | text, column, QR and barcode calls |
| Best for | exact branding, any language or script | speed, very long reports, SDKs without image support |

If unsure, start with bitmaps. It is less code and what you preview is what prints.

## Print the bitmaps

```kotlin
fun terminalBitmapDriver(sdk: TerminalSdk): ReceiptDriver = ReceiptDriver { receipt ->
    receipt.bands.forEach(sdk::printBitmap)
    sdk.awaitDone()
}
```

- Print the bands in order, at their original size. Do not scale them or add feed between them.
- Every band is `receipt.width` dots wide. Set `ReceiptOptions(width = …)` to your printer's printable
  width: usually 384 for 58 mm paper and 576 for 80 mm.
- Do not recycle or modify the bands; the receipt may be printed or exported again.

## Map the blocks

Use a `when` over `ReceiptBlock`. Because `ReceiptBlock` is sealed, the compiler reports any block type
you forget to handle:

```kotlin
class TerminalBlockDriver(private val sdk: TerminalSdk) : ReceiptDriver {
    override suspend fun print(receipt: Receipt) {
        for (block in receipt.blocks) {
            when (block) {
                is ReceiptBlock.Text -> {
                    sdk.setAlignment(block.align.toSdk())
                    sdk.printText(block.text, block.style.bold, block.style.size == ReceiptFontSize.BIG)
                }
                is ReceiptBlock.KeyValue -> sdk.printColumns(listOf(block.key, block.value), listOf(1f, 1f))
                is ReceiptBlock.Row -> sdk.printColumns(block.columns, block.columns.map { 1f })
                is ReceiptBlock.Table -> {
                    val weights = block.weights ?: block.headers.map { 1f }
                    if (block.headers.isNotEmpty()) sdk.printColumns(block.headers, weights)
                    block.rows.forEach { sdk.printColumns(it, block.weights ?: it.map { 1f }) }
                }
                is ReceiptBlock.Divider -> sdk.printText(block.pattern.repeat(block.length).take(block.length), bold = false, large = false)
                is ReceiptBlock.Spacer -> sdk.feed(block.lines)
                is ReceiptBlock.QrCode -> sdk.printQr(block.content, block.size)
                is ReceiptBlock.Barcode -> sdk.printBarcode(block.content, block.width, block.height, block.format.id)
                // Image blocks carry a reference; the resolved pixels are in the rendered bands.
                // SDKs without image support can skip them, or print the bands instead.
                is ReceiptBlock.Image -> Unit
            }
        }
        sdk.awaitDone()
    }
}
```

Every string in a block is final: bindings and filters have already been applied.

| Block | Fields |
| --- | --- |
| `Text` | `text`, `align` (`LEFT`, `CENTER`, `RIGHT`), `style` |
| `KeyValue` | `key`, `value`, `style` |
| `Row` | `columns` |
| `Table` | `headers`, `rows`, `weights?`, `alignments?`, `headerStyle`, `rowStyle` |
| `Divider` | `pattern`, `length` (characters) |
| `Spacer` | `lines` |
| `Image` | `source`, `width?`, `height?` (dots) |
| `QrCode` | `content`, `size` (dots) |
| `Barcode` | `content`, `width`, `height`, `format` |

`style` is a `ReceiptTextStyle`: `size` (`SMALL`, `MIDDLE`, `BIG`), `font` (explicit size in dots, or
null), `bold`, `underline`. Template `meta` values, including custom keys, are in `receipt.meta`.

## Rules every driver should follow

- **Suspend until the job is done.** If your SDK uses callbacks, wrap them with
  `suspendCancellableCoroutine` so `print` returns only after the printer reports completion.
- **Throw on failure.** Turn SDK error codes (paper out, cover open, overheating) into exceptions.
  The exception reaches the caller of `receipts.print`.
- **Do not retry.** A retry after a partial print can produce a duplicate receipt. Let the user decide.
- **No threading needed.** `ReceiptScript.print` calls your driver on a background dispatcher and never
  runs two jobs at once on the same instance.

The `TerminalSdk` interface above stands in for a vendor SDK. The complete examples are in
[`src/test/java/examples/Drivers.kt`](../../src/test/java/examples/Drivers.kt).
