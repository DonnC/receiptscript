# Implement your printer driver

There are two output interfaces, and one transport interface. Use only the one
that matches the integration you are writing:

| Interface | Implement when | Return value |
| --- | --- | --- |
| `ReceiptDriver` | Your SDK has text, table, image and code operations | `Unit`; host observes SDK completion |
| `ReceiptAdapter<T>` | You want full receipt-level control or encoded output | Your `T`, e.g. `ByteArray` |
| `PrinterConnection` | You already use a byte driver and need a connection | `Unit`; bytes sent or an exception |

`ReceiptDriver` extends `ReceiptAdapter<Unit>`. The engine depends only on the
core interfaces. `PrinterConnection` and the bundled byte drivers are in the
optional `receiptscript-escpos` artifact.

## SDK driver

Implement all nine block operations. There is no second parser to write and no
binding code in the driver. For example, if your application already wraps its
SDK behind `PrinterDriver`, a complete ReceiptScript adapter is:

```kotlin
import zw.co.donnclab.rct.*

class ExampleAdapter(private val sdk: PrinterDriver) : ReceiptDriver {
    override fun printText(block: ReceiptBlock.Text) =
        sdk.text(block.text, block.align, block.style)

    override fun printKeyValue(block: ReceiptBlock.KeyValue) =
        sdk.keyValue(block.key, block.value, block.style)

    override fun printDivider(block: ReceiptBlock.Divider) =
        sdk.divider(block.pattern, block.length)

    override fun printRow(block: ReceiptBlock.Row) = sdk.row(block.columns)
    override fun printTable(block: ReceiptBlock.Table) = sdk.table(block)
    override fun printImage(block: ReceiptBlock.Image) =
        sdk.image(block.source, block.width, block.height)

    override fun printQrCode(block: ReceiptBlock.QrCode) =
        sdk.qr(block.content, block.size)

    override fun printBarcode(block: ReceiptBlock.Barcode) = sdk.barcode(block)
    override fun feed(block: ReceiptBlock.Spacer) = sdk.feed(block.lines)
}

receipt.render(ExampleAdapter(sdk))
```

[`ExampleAdapter.kt`](../examples/kotlin/ExampleAdapter.kt) includes the full
sample `PrinterDriver` interface, so you can compile and adapt it. That sample
interface represents your SDK wrapper; it is not another required ReceiptScript
interface. With a vendor SDK directly, replace `sdk.text(...)` etc. with its real
calls. Do not invent vendor method names or import vendor SDKs into the engine.

The inherited `render(receipt)` visits blocks in order. A thrown error stops
dispatch; it does not automatically retry or perform cleanup. If your SDK needs
begin/commit, queueing or suspend callbacks, override `render` or implement
`ReceiptAdapter<YourResult>` and manage that lifecycle explicitly.

## What arrives in each block

| Block | Already resolved | Driver responsibility |
| --- | --- | --- |
| Text | Text, alignment, bold, underline, size/font | Map to native font or raster text |
| KeyValue | Key, value, style | Column API or wrapping with right-aligned value |
| Divider | Repeat pattern and logical character count | Clamp to native width or draw the corresponding rule |
| Row | List of cells | Equal column layout |
| Table | Headers, all rows, weights, alignments, header/row styles | SDK columns or wrapped physical-cell layout |
| Image | Source reference, optional dimensions | Resolve/decode locally and preserve aspect ratio |
| QrCode | Content and requested size | Native code or local encoded bitmap |
| Barcode | Content, format, requested dimensions | Native supported format or local encoded bitmap |
| Spacer | Number of blank lines | Feed with SDK line-height rules |

Tables are structured. A vendor list/table API is usually better than joining
strings. When laying them out yourself, reserve gutters, distribute remaining
width by weights, wrap cells independently, and emit the tallest cell's number
of lines. Default weights are equal and default alignments centered.

The engine formats amounts supplied by the application. Never recalculate sale
amounts, tax or currency conversions in a driver.

## Byte-producing driver

If you implement another protocol, this is the entire interface:

```kotlin
fun interface ReceiptAdapter<T> {
    fun render(receipt: Receipt): T
}
```

Implement `ReceiptAdapter<ByteArray>` for CPCL, TSPL or another encoder. Encode
the complete job first, then send it through a connection. You can also return
a preview model, image bytes or an SDK-specific result instead of printer bytes.
The bundled ESC/POS driver is a complete example of this pattern.

## Connection only

```kotlin
val connection = PrinterConnection { bytes -> existingPrinterLibrary.write(bytes) }
val printer = ReceiptPrinter(EscPosDriver(), connection)
printer.print(receipt)
```

`send` must write the entire byte array or throw. A partial-write API such as
Android USB bulk transfer must loop until every byte is accepted; see the
[USB example](../examples/android/UsbPrinterConnection.kt). Serialize writes
through one connection instance for a physical printer. Sending bytes is not
proof of printing: status callbacks, paper errors and retry decisions remain
application responsibilities.
