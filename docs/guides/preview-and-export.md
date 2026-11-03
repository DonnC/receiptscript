# Preview and export

A `Receipt` is laid out once. The screen preview uses that recorded layout with smooth text;
exports and raster printing retain the quantized printer pixels. Spacing and geometry are shared.

## Preview in Compose

```kotlin
val receipt by produceState<Receipt?>(null, sale) { value = receipts.render("sale", sale) }
receipt?.let { ReceiptPreview(it, Modifier.width(280.dp)) }
```

`ReceiptPreview` only draws. Its default `SMOOTH` mode redraws text at screen resolution rather
than enlarging low-resolution printer dots. For hardware diagnostics, pass
`mode = ReceiptPreviewMode.THERMAL` to inspect the actual raster pixels without smoothing.
Give it a fixed or maximum width: a 384-dot receipt shown full-width on a tablet looks enormous.

### Android Studio previews

`@Preview` functions cannot wait for `produceState`. Render synchronously instead:

```kotlin
@Preview
@Composable
private fun SaleReceiptPreview() {
    val context = LocalContext.current
    val receipt = remember { runBlocking { ReceiptScript(context).render("sale", sampleSale) } }
    ReceiptPreview(receipt)
}
```

Use `runBlocking` only in previews and tests, never in app code.

### Views

The receipt is a list of bitmaps, so a plain `ImageView` works too:

```kotlin
imageView.setImageBitmap(receipt.toBitmap())
```

## Export

| Function | Returns | Notes |
| --- | --- | --- |
| `receipt.toImage()` | `ReceiptFile` (PNG) | `toImage(ReceiptImageFormat.JPEG, quality = 90)` for JPEG |
| `receipt.toPdf()` | `ReceiptFile` (PDF) | long receipts are split into pages; real-size at 203 DPI |
| `receipt.toBitmap()` | `Bitmap` | a new bitmap that you own; recycle it when done |

A `ReceiptFile` has `bytes`, `mimeType` and `extension`. Saving and sharing stay in your app:

```kotlin
val pdf = receipt.toPdf()
val file = File(context.cacheDir, "receipt-${sale.reference}.${pdf.extension}").apply { writeBytes(pdf.bytes) }
val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
context.startActivity(Intent.createChooser(
    Intent(Intent.ACTION_SEND).setType(pdf.mimeType).putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
    "Share receipt"
))
```

Exports are CPU work. Call them from a background dispatcher, for example
`withContext(Dispatchers.Default) { receipt.toPdf() }`.

### PDF options

```kotlin
receipt.toPdf(ReceiptPdfOptions(pageHeightDots = 1600, dotsPerInch = 203))
```

`pageHeightDots` sets how much of the receipt goes on each page. A page break can split a line of
text; use taller pages for long reports.

### Size limits

Very long receipts are guarded so a runaway template cannot exhaust memory. `toBitmap` and
`toImage` refuse receipts over 16 megapixels; use `toPdf`, which streams the receipt band by band.
Rendering limits are set in `ReceiptOptions` (`maxHeight`, `maxPixels`).
