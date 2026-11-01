![ReceiptScript](docs/media/banner.png)

# ReceiptScript

**Design receipts once. Preview, print, and share them from Kotlin.**

ReceiptScript separates receipt design from printer integration. Write a readable
`.rct` template, pass your application's data, and render a receipt for Compose,
a built-in printer, or an ESC/POS connection. Change the layout without rewriting
printer calls.

One Android AAR includes the engine, thermal renderer, Compose preview, image/PDF
export, and ESC/POS driver. The engine and ESC/POS driver also ship as one JVM JAR.

[Example app](example) · [Sample templates](example/src/main/assets/receipts) ·
[Downloads](https://github.com/DonnC/receiptscript/releases) · [MIT license](LICENSE)

![ReceiptScript template and its rendered receipt](docs/media/receipt-example.png)

## Install

For an existing Jetpack Compose app, place the Android AAR in your app's `libs/`
directory and add:

```kotlin
dependencies {
    implementation(files("libs/receiptscript-0.5.0.aar"))
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
}
```

Android API 26+ and Java 11 bytecode support are required. The library is built
with Kotlin 2.3.20. Local AARs need the runtime dependencies listed above; they
are not bundled inside the file.

For the current **0.5.0 development version**, build the AAR with
`./gradlew :assembleRelease` (JDK 17, Android SDK 36). The output is
`build/outputs/aar/receiptscript-0.5.0.aar`. Use
[GitHub Releases](https://github.com/DonnC/receiptscript/releases) for published
AAR, JAR, and VSIX versions.

## Your First Receipt

### Design The Template

Create `app/src/main/assets/receipts/sale.rct`:

```rct
receipt "sale"

center bold "{{merchant}}"
kv "REFERENCE" "{{reference}}"

divider
table:
  headers: "ITEM" "QTY" "TOTAL"
  weights: 3 1 2
  alignments: start center end
  for item in items:
    row "{{item.name}}" "{{item.quantity}}" "{{item.total | money:currency}}"

divider
kv bold "TOTAL" "{{total | money:currency}}"
barcode "{{reference}}" 300 60 "code128"
footer:
  center small "github.com/DonnC/receiptscript"
```

To design with a preview, install the VSIX through VS Code's **Extensions: Install
from VSIX...** command. Place this `sale.preview.json` beside `sale.rct`, open the
template, and run **RCT: Open Preview** from the Command Palette:

```json
{
  "merchant": "Coffee & Code",
  "reference": "RCT-001",
  "currency": "USD",
  "total": "10.00",
  "items": [
    {"name": "Latte", "quantity": 1, "total": "4.50"},
    {"name": "Brownie", "quantity": 1, "total": "3.00"},
    {"name": "Filter coffee", "quantity": 1, "total": "2.50"}
  ]
}
```

The extension provides highlighting, diagnostics, and an updating preview. The
JSON file supplies editor sample data; the Android app passes its real data directly.

### Render In Your App

Add this screen to your Compose app. It loads the template by ID, binds data, and
displays a thermal receipt without a printer connection:

```kotlin
import android.content.Context
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.ReceiptScript
import zw.co.donnclab.rct.android.*
import zw.co.donnclab.rct.compose.ReceiptPreview

suspend fun loadSale(context: Context): RasterReceipt = withContext(Dispatchers.IO) {
    val data = mapOf(
        "merchant" to "Coffee & Code", "reference" to "RCT-001",
        "currency" to "USD", "total" to "10.00",
        "items" to listOf(
            mapOf("name" to "Latte", "quantity" to 1, "total" to "4.50"),
            mapOf("name" to "Brownie", "quantity" to 1, "total" to "3.00"),
            mapOf("name" to "Filter coffee", "quantity" to 1, "total" to "2.50")
        )
    )
    val templates = AssetReceiptTemplateRepository(context)
    val receipt = ReceiptScript().template("sale", templates).render(data)
    receipt.render(AndroidReceiptRenderer(context, RasterOptions(width = 384)))
}

@Composable
fun SaleReceiptScreen() {
    val context = LocalContext.current
    val result by produceState<Result<RasterReceipt>?>(null, context) {
        value = try { Result.success(loadSale(context)) }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Exception) { Result.failure(error) }
    }
    val output = result?.getOrNull()
    when {
        output != null -> ReceiptPreview(output,
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()))
        result?.isFailure == true -> BasicText("Unable to load receipt")
        else -> BasicText("Loading receipt...")
    }
}
```

`loadSale` returns a `RasterReceipt`: the laid-out receipt in printer dots. Pass
that same object to the preview, printer, and exporter to reuse the same layout.
Match the renderer's width to your printer's printable dot width; 384 dots is a
common 58 mm mechanism.

Data can be maps or Kotlin objects. Decimal strings and `BigDecimal` work for
money. With minified object models, preserve the property names used in bindings.

## Connect Your Printer

Choose the integration that matches your hardware. **Raster printing is the
recommended Android path:** your SDK prints the same bitmap bands the app previews.

### Built-In Terminal Printer

Implement `ReceiptPrinter` by sending each band to your SDK's bitmap operation:

```kotlin
import android.graphics.Bitmap
import zw.co.donnclab.rct.android.RasterReceipt
import zw.co.donnclab.rct.android.ReceiptPrinter

class TerminalPrinter(
    private val printBitmap: suspend (Bitmap) -> Unit
) : ReceiptPrinter {
    override suspend fun print(receipt: RasterReceipt) {
        for (band in receipt.bands) printBitmap(band)
    }
}

suspend fun printOnTerminal(output: RasterReceipt, printBitmap: suspend (Bitmap) -> Unit) {
    output.print(TerminalPrinter(printBitmap))
}
```

`printBitmap` is your SDK integration: await its completion callback, propagate
printer errors, and send bands at their original dot width without extra feed
between them. Serialize jobs sharing a physical printer.

### Portable Bluetooth Or USB Printer

The bundled ESC/POS adapter works with an established output stream. Use a
Bluetooth socket's output stream or the stream supplied by a USB-serial library:

```kotlin
import java.io.OutputStream
import zw.co.donnclab.rct.android.EscPosReceiptPrinter
import zw.co.donnclab.rct.android.RasterReceipt
import zw.co.donnclab.rct.escpos.OutputStreamConnection

suspend fun printToBluetooth(output: RasterReceipt, socket: android.bluetooth.BluetoothSocket) {
    output.print(EscPosReceiptPrinter(OutputStreamConnection(socket.outputStream)))
}

suspend fun printToUsbSerial(output: RasterReceipt, serialOutput: OutputStream) {
    output.print(EscPosReceiptPrinter(OutputStreamConnection(serialOutput)))
}
```

The app handles pairing, connection permissions, USB serial settings, and closing
connections. Both examples use the default 384-dot `EscPosProfile.Portable58`.
For 576-dot output, use `EscPosProfile.Receipt80` and render at 576 dots. Enable
`cutAfterPrint` only on a cutter-capable profile. USB bulk devices can instead
implement `PrinterConnection`; see the [bulk transfer example](src/test/fixtures/integration/UsbPrinterConnection.kt).

Printer exceptions reach the caller. Handle paper-out, connection loss, and SDK
faults in your print action; check a partial print before retrying. Stream success
means bytes were written; paper-status reporting depends on the device protocol.

### Native Block Drivers

For SDKs with native text, table, image, and code operations, implement
`ReceiptDriver` and call `receipt.render(driver)` on the resolved `Receipt`.
The [native SDK example](src/test/kotlin/examples/native/BuiltInPrinterDriver.kt)
maps every block, including table styles and optional cutting.

This is an alternative to raster printing. Native printer fonts and layout may
differ from the Android preview. `ReceiptAdapter<T>` is the generic output contract;
the JVM `EscPosDriver` uses it to produce ESC/POS bytes directly.

## Templates And Branding

The engine renders strings. Assets are a default, not a requirement:

```kotlin
val receipt = ReceiptScript().render(
    "receipt \"note\"\ncenter \"{{message}}\"",
    mapOf("message" to "Payment received")
)
```

For reusable compiled source, call `compile(source)` once, then `render(data)`
for each transaction. Use `compile(source, repository)` when that source references
shared fragments. Compiled templates are snapshots; reload after a template update.

### Your Template Repository

Implement `ReceiptTemplateRepository` for a database, network, resources, or mixed
storage. It accepts an ID and optional lookup arguments, returning the template text:

```kotlin
import zw.co.donnclab.rct.ReceiptTemplateRepository

class SyncedTemplates(
    private val findSource: suspend (String, Map<String, Any?>) -> String?,
    private val fallback: ReceiptTemplateRepository
) : ReceiptTemplateRepository {
    override suspend fun getTemplate(templateId: String, args: Map<String, Any?>): String =
        findSource(templateId, args) ?: fallback.getTemplate(templateId, args)
}
```

Supply your lookup function and an `AssetReceiptTemplateRepository(context)`
fallback. Pass options with `engine.template("sale", repository, args =
mapOf("merchantId" to "M001"))`. The same options reach header/footer lookups;
transaction values are supplied separately to `render(data)`. Repository I/O is
suspending; compiled rendering performs no further storage access.

### Shared Headers And Footers

Store `merchant.rct` and `customer.rct` alongside your templates. Fragments contain
ordinary statements such as `center bold "{{merchant}}"` or `center "Thank you!"`.
Then use them in any receipt:

```rct
receipt "sale"
use header "merchant"
use footer "customer"
body:
  kv "TOTAL" "{{total | money:currency}}"
```

Headers render first and footers last, wherever they are declared. Footers receive
a two-line gap. Inline `header:` and `footer:` sections work without separate files.

### Logos And Media

Put a PNG logo in `app/src/main/assets/receipts/logos/merchant.png`. Reference it in
a template or shared header:

```rct
image "asset:logos/merchant.png" 96 64
```

Set the image base directory to match the template directory:

```kotlin
val renderer = AndroidReceiptRenderer(context,
    images = AssetReceiptMediaResolver(context, "receipts"))
```

This lets Android and VS Code use the same logo file and reference. The resolver
reads merged app and client-module assets; without a directory argument, paths
start at the APK asset root. References can also come from bindings, such as
`image "{{logoRef}}" 96 64`.

For other sources, implement `ReceiptMediaResolver<Bitmap>` and pass it as the
renderer's `images` argument. This example prefers a prepared cache and falls back
to APK assets:

```kotlin
import android.content.Context
import android.graphics.Bitmap
import zw.co.donnclab.rct.ReceiptMediaResolver
import zw.co.donnclab.rct.android.AssetReceiptMediaResolver

class LogoResolver(context: Context, private val cached: (String) -> Bitmap?) : ReceiptMediaResolver<Bitmap> {
    private val assets = AssetReceiptMediaResolver(context, "receipts")
    override fun resolve(source: String): Bitmap? = cached(source) ?: assets.resolve(source)
}
```

Use `AndroidReceiptRenderer(context, images = LogoResolver(context, cached))`.
Prepare remote images before rendering. Return `null` for an unknown reference;
missing required media causes a render error. Template dimensions control scaling.

## Receipt Elements

| Element | What it does | Good for |
| --- | --- | --- |
| `left`, `center`, `right`, `text` | Align and style text | Merchant details, headings, notices |
| `kv` | Separate a label and value | References, totals, approval codes |
| `table`, `row` | Render columns and repeated rows | Baskets, transaction lists, reports |
| `divider`, `spacer` | Add rules and vertical space | Sections, signatures, tear-off space |
| `image` | Resolve and size an image | Logos, branding |
| `qr`, `barcode` | Generate scannable codes | Payment links, invoice references |
| `if`, `for` | Include optional or repeated content | Cashback, discounts, item lists |
| `header`, `body`, `footer` | Order receipt sections | Consistent receipt structure |
| `use header`, `use footer` | Load shared fragments | Reusable branding and closing text |
| `meta` | Set layout defaults | Character width, font size, copy labels |

Text supports `bold`, `underline`, `small`, `big`, and trailing inline font sizes.
`meta.font` sets the base size; `fontScaleMode` selects relative or fixed sizing,
with inline sizes taking precedence. Tables default to equal-width, centered
columns; `weights` and `alignments` customize them. `divider "_"` draws a continuous
rule in Android; patterns and character counts are configurable. Comments start
with `#`, and nested blocks use two-space indentation.

Bindings support `money`, `date`, `datetime`, `maskPan`, configurable `mask`,
`upper`, `lower`, `default`, and padding filters. Datetime defaults to
`dd/MM/yyyy HH:mm:ss`, with custom formats supported. Use `if` or `default` for
optional values; missing required bindings fail rendering. Barcode formats are
Code 128, Code 39, EAN-13, EAN-8, UPC-A, and ITF. Keep PINs, passwords, and track
data out of receipt contexts.

## Export And Share

Export the same `RasterReceipt` used for preview and printing:

```kotlin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import zw.co.donnclab.rct.android.RasterReceipt
import java.io.File

suspend fun saveReceipt(output: RasterReceipt, directory: File) = withContext(Dispatchers.IO) {
    directory.mkdirs()
    val image = output.toImageBytes()
    val pdf = output.toPdfBytes()
    File(directory, "receipt.${image.extension}").writeBytes(image.bytes)
    File(directory, "receipt.${pdf.extension}").writeBytes(pdf.bytes)
}
```

Exports provide `bytes`, `mimeType`, and `extension`. PNG is the image default;
JPEG and caller-owned bitmap export are also available. Use your app's FileProvider
to share saved files. PDF paginates the existing layout; page boundaries can split
a line. Prefer PDF for long reports that exceed the image pixel budget.

## Example App And Development

Open the repository root in Android Studio and run **ReceiptScript Starter**
(`:example`). Explore bank, SME, discount, fiscal-style, shared-branding, basic,
and multi-currency reports with receipt, template, and data views. The examples
use synthetic data. Compose previews live in
[ReceiptScreenPreviews.kt](example/src/main/java/dev/receiptscript/sample/ReceiptScreenPreviews.kt).

```shell
./gradlew :testDebugUnitTest :example:testDebugUnitTest :assembleRelease :example:assembleDebug
```

For the JVM JAR or VSIX, use Node.js 22+ and run `npm ci` inside `tools/`, followed
by `npm run release:kotlin` or `npm run package:extension`. Output is written to
`artifacts/`. JVM consumers need Kotlin's standard library and ZXing for code
generation; Android consumers use the AAR instead of the JAR.

Bug reports and contributions are welcome through GitHub issues and pull requests.
Include a minimal template and sanitized sample data for rendering issues; run the
relevant tests when changing the engine, renderer, or tools.

## License

[MIT](LICENSE). Bundled font and dependency notices are listed in
[THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
