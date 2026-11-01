![ReceiptScript](docs/media/banner.png)

# ReceiptScript

**Design receipts once. Preview, print, and share them from Kotlin.**

ReceiptScript turns readable `.rct` templates and your application data into a
`Receipt`. Configure your printer and sources once; use the same receipt for
Compose preview, printing, and image/PDF export. One Android AAR includes the
engine, preview, exporters, and ESC/POS support.

[Example app](example) · [Templates](example/src/main/assets/receipts) ·
[Downloads](https://github.com/DonnC/receiptscript/releases) · [MIT](LICENSE)

![ReceiptScript template and its rendered receipt](docs/media/receipt-example.png)

## Install

Place the AAR in your Compose app's `app/libs/` directory:

```kotlin
dependencies {
    implementation(files("libs/receiptscript-0.6.0.aar"))
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
}
```

Requires Android API 26+ and Java 11 bytecode support; built with Kotlin 2.3.20.
The dependencies above accompany a local AAR. For the current **0.6.0 development
version**, build with `./gradlew :assembleRelease` using JDK 17 and Android SDK 36.
The AAR is written to `build/outputs/aar/`; published versions go to GitHub Releases.

## Design

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

Install the VSIX through **Extensions: Install from VSIX...** in VS Code. Add
`sale.preview.json` beside the template and run **RCT: Open Preview**:

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

JSON supplies editor sample data. At runtime, pass a map or Kotlin object with
matching fields; no JSON conversion is required.

## Render, Print, Preview

In a coroutine, configure ReceiptScript with your Android `context` and printer
`driver`. Here, `data` is your receipt model matching the template above:

```kotlin
import zw.co.donnclab.rct.android.*

val receipts = ReceiptScript(
    context, driver,
    AssetReceiptTemplateRepository(context),
    AssetReceiptMediaResolver(context, "receipts")
)

val receipt = receipts.render("sale", data)
receipts.print(receipt)
```

Inside Compose, display that same receipt:

```kotlin
import zw.co.donnclab.rct.compose.ReceiptUiPreview

ReceiptUiPreview(receipt)
```

Export on a worker dispatcher, then save or share the result in your app:

```kotlin
val image = receipt.toImageBytes() // PNG: bytes, mimeType, extension
val pdf = receipt.toPdfBytes()
val bitmap = receipt.toBitmap()   // Caller-owned bitmap
```

Rendering performs loading and layout off the UI thread; it never prints.
A driver is optional for preview/export-only apps. `ReceiptScript(context)` uses
asset defaults. The [example app](example/src/main/java/dev/receiptscript/sample)
shows complete screen loading, error handling, and print actions.

## Connect A Printer

For a built-in SDK, implement `ReceiptDriver.print` with your suspending bitmap
operation. Await the SDK callback and propagate its errors:

```kotlin
import android.graphics.Bitmap

class TerminalDriver(private val printBitmap: suspend (Bitmap) -> Unit) : ReceiptDriver {
    override suspend fun print(receipt: Receipt) {
        for (band in receipt.bands) printBitmap(band)
    }
}
```

For an established Bluetooth or USB-serial connection, use the bundled ESC/POS adapter:

```kotlin
import zw.co.donnclab.rct.escpos.OutputStreamConnection

val bluetoothDriver = EscPosReceiptPrinter(OutputStreamConnection(socket.outputStream))
val usbDriver = EscPosReceiptPrinter(OutputStreamConnection(serialOutput))
```

Pass your chosen driver to `ReceiptScript`. Your app manages permissions and
connections. USB bulk devices can implement `PrinterConnection`; see the
[bulk transfer example](src/test/fixtures/integration/UsbPrinterConnection.kt).

The default is 384-dot output. Match `ReceiptOptions.width` and the ESC/POS profile
to your device; `EscPosProfile.Receipt80` supports 576 dots and opt-in cutting.
Print bitmap bands without resizing or extra feed to match the preview. Native
SDK integrations may instead map `receipt.blocks`, using the device's own layout.
Reuse one client per printer; errors propagate and partial jobs are not retried
automatically. Stream completion confirms a write, not physical paper status.

## Supply Your Templates And Images

| Interface | Responsibility | Included default |
| --- | --- | --- |
| `ReceiptDriver` | Print through your SDK or connection | ESC/POS adapter |
| `ReceiptTemplateRepository` | Fetch template source by ID and optional arguments | Assets under `receipts/` |
| `ReceiptMediaResolver<Bitmap>` | Resolve logo/image references | Android assets |

Template IDs are passed unchanged to your repository. An ID can be a filename,
database key, or any identifier your lookup understands. For custom storage:

```kotlin
import zw.co.donnclab.rct.ReceiptTemplateRepository

class Templates(private val fetch: suspend (String, Map<String, Any?>) -> String) : ReceiptTemplateRepository {
    override suspend fun getTemplate(templateId: String, args: Map<String, Any?>) =
        fetch(templateId, args)
}
```

Supply your lookup as `fetch` and pass the repository to the constructor.
`receipts.render("sale", data, args = mapOf("merchantId" to "M001"))` forwards
lookup options separately from receipt data. `receipts.renderSource(source, data)`
accepts an in-memory template directly.

### Shared Branding

Store `merchant.rct` and `customer.rct` fragments alongside your asset templates,
or return them from your repository:

```rct
receipt "sale"
use header "merchant"
use footer "customer"
body:
  kv "TOTAL" "{{total | money:currency}}"
```

Headers render first and footers last, with a two-line gap before the footer.
Inline `header:` and `footer:` sections are also supported.

With the image base set to `receipts` as above, put your logo in
`app/src/main/assets/receipts/logos/merchant.png` and reference it with:

```rct
image "asset:logos/merchant.png" 96 64
```

This path also works in VS Code beside `sale.rct`. References may use bindings,
such as `image "{{logoRef}}" 96 64`. For custom media, implement
`ReceiptMediaResolver<Bitmap>.resolve(source)` and pass it as `images`. Prepare
remote images before rendering; return `null` for unknown references. PNG is a
practical logo default.

## Receipt Elements

| Element | Purpose | Typical use |
| --- | --- | --- |
| `left`, `center`, `right`, `text` | Align and style text | Merchant details, headings |
| `kv` | Separate label and value | References, totals |
| `table`, `row` | Lay out columns and rows | Baskets, transaction reports |
| `divider`, `spacer` | Add rules and space | Sections, signatures |
| `image` | Resolve and size images | Logos |
| `qr`, `barcode` | Generate scannable codes | Payment links, invoice references |
| `if`, `for` | Optional and repeated content | Cashback, item lists |
| `header`, `body`, `footer` | Order receipt sections | Consistent structure |
| `use header`, `use footer` | Reuse fragments | Shared branding |
| `meta` | Set layout defaults | Character width, font size |

Text supports `bold`, `underline`, `small`, `big`, and trailing inline font sizes.
`meta.font` and `fontScaleMode` control defaults; inline sizes win. Tables default
to equal-width, centered columns. `divider "_"` draws a continuous Android rule.
Comments start with `#`; indent nested blocks by two spaces.

Filters include `money`, `date`, `datetime`, `maskPan`, configurable `mask`, case
conversion, defaults, and padding. Datetime defaults to `dd/MM/yyyy HH:mm:ss`.
Use decimal strings or `BigDecimal` for money; preserve binding property names
when minifying Kotlin models. Keep PINs, passwords, and track data out of receipts.
PDF paginates the existing layout and may split a line; prefer it for long reports.

## Example And Development

Open the repository root in Android Studio and run **ReceiptScript Starter**
(`:example`). Explore bank, SME, discounts, shared branding, fiscal-style, and
multi-currency receipts with synthetic data and
[Compose previews](example/src/main/java/dev/receiptscript/sample/ReceiptScreenPreviews.kt).

```shell
./gradlew :testDebugUnitTest :example:testDebugUnitTest :assembleRelease :example:assembleDebug
```

For JVM/VSIX builds, use Node.js 22+, run `npm ci` in `tools/`, then
`npm run release:kotlin` or `npm run package:extension`. Output goes to `artifacts/`.
The JVM JAR exposes the storage-independent engine and ESC/POS output; Android apps
use the AAR. Issues and pull requests are welcome, with minimal templates and
sanitized data for rendering issues.

[MIT](LICENSE) · [Third-party notices](THIRD_PARTY_NOTICES.md)
