![ReceiptScript](docs/media/banner.png)

# ReceiptScript

[![Verify](https://github.com/DonnC/receiptscript/actions/workflows/ci.yml/badge.svg)](https://github.com/DonnC/receiptscript/actions/workflows/ci.yml)
[![Release](https://img.shields.io/github/v/release/DonnC/receiptscript?include_prereleases)](https://github.com/DonnC/receiptscript/releases)
[![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)](LICENSE)

**Thermal receipts for Android POS apps, written as templates, printed on any printer.**

You write receipts as small `.rct` templates. ReceiptScript binds your data, lays the receipt
out, previews it in Compose, exports it as an image or PDF, and hands it to your printer. Your
code covers only what is specific to your app: your printer SDK, and, if you need them, where
templates and images come from.

![A ReceiptScript template and the receipt it renders](docs/media/receipt-example.png)

> **Status:** pre-1.0. The API may change between minor versions; every change is listed in the
> [changelog](CHANGELOG.md) with a migration note.

## Install

Download `receiptscript-<version>.aar` from [Releases](https://github.com/DonnC/receiptscript/releases),
put it in `app/libs/`, and add:

```kotlin
dependencies {
    implementation(files("libs/receiptscript-0.7.0.aar"))
    implementation(platform("androidx.compose:compose-bom:2025.11.01"))
    implementation("androidx.compose.foundation:foundation")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("com.google.zxing:core:3.5.3")
}
```

Requires Android 8.0 (API 26) or newer and Java 11 bytecode.

## Quick start

**1. Write a template.** Save it as `app/src/main/assets/receipts/sale.rct`:

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

Bindings like `{{item.total | money:currency}}` read from your data. The
[language reference](docs/language-reference.md) covers every statement and filter.

**2. Render, preview and print.** Create one `ReceiptScript` per printer and reuse it:

```kotlin
val receipts = ReceiptScript(context, driver)

val receipt = receipts.render("sale", data)   // data: a Map or any Kotlin object
ReceiptPreview(receipt)                        // in Compose: exactly what will print
receipts.print(receipt)
```

`data` is your own model, for example a `Sale` data class with `merchant`, `reference`, `currency`,
`total` and `items`. No JSON conversion is needed.

**3. Export it** to share or archive:

```kotlin
val pdf = receipt.toPdf()        // ReceiptFile: bytes, mimeType, extension
val png = receipt.toImage()
val bitmap = receipt.toBitmap()
```

## What you implement

The library has one entry point, `ReceiptScript`. You implement up to three small interfaces, and each
has a sensible default:

| Interface | Your job | Default |
| --- | --- | --- |
| `ReceiptDriver` | Send a receipt to your printer SDK | none (preview and export work without one) |
| `ReceiptTemplateRepository` | Load template source by ID: assets, database, network, or a mix | `assets/receipts/<id>.rct` |
| `ReceiptImageResolver` | Turn `image "..."` references into bitmaps | `asset:` paths in your assets |

All three are `fun interface`s, so a lambda is enough:

```kotlin
val receipts = ReceiptScript(
    context,
    driver = ReceiptDriver { receipt -> receipt.bands.forEach { sdk.printBitmap(it) } },
    templates = ReceiptTemplateRepository { id, _ -> templateDao.source(id) },
    images = ReceiptImageResolver { source -> logoCache[source] }
)
```

A driver can print the rendered bitmaps (as above), or map each `ReceiptBlock` to your SDK's own text,
table and barcode commands. See [Writing a driver](docs/guides/drivers.md).

## ESC/POS printers

Most portable Bluetooth and USB thermal printers speak ESC/POS. For those, use the bundled driver
instead of writing one:

```kotlin
val bluetoothDriver = EscPosDriver(BluetoothPrinterConnection(device))
val usbDriver = EscPosDriver(UsbPrinterConnection(usbConnection, endpoint))
val networkDriver = EscPosDriver(TcpPrinterConnection("192.168.1.50"), EscPosProfile.Receipt80)
```

Your app keeps control of permissions, pairing and device selection. See [Printing](docs/guides/printing.md)
for paper widths, profiles and other transports.

## Documentation

- [Getting started](docs/getting-started.md): a working receipt in five minutes
- [Template language reference](docs/language-reference.md): statements, bindings, filters
- Guides: [drivers](docs/guides/drivers.md), [templates and images](docs/guides/templates-and-images.md),
  [printing](docs/guides/printing.md), [preview and export](docs/guides/preview-and-export.md),
  [VS Code](docs/guides/vscode.md), [JVM](docs/guides/jvm.md)
- [Architecture](docs/architecture.md): how the pieces fit, and why the API looks the way it does
- [Migrating to 0.7](docs/migration/0.7.md)

## Design templates in VS Code

The ReceiptScript VS Code extension (`.vsix` on [Releases](https://github.com/DonnC/receiptscript/releases))
adds highlighting, linting and a live preview. Put sample data in `sale.preview.json` next to
`sale.rct` and run **RCT: Open Preview**:

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

## Example app

Open the repository in Android Studio and run **ReceiptScript Starter**. It renders bank, retail,
discount, fiscal-style and end-of-day receipts from [the same templates](example/src/main/assets/receipts)
VS Code previews. Connect your printer in [`SamplePrinter.kt`](example/src/main/java/io/github/donnclab/rct/sample/SamplePrinter.kt).

## Contributing

Issues and pull requests are welcome. Read [CONTRIBUTING.md](CONTRIBUTING.md) to set up the project
and run the tests. Report security issues privately as described in [SECURITY.md](SECURITY.md).

## License

[MIT](LICENSE). Bundled fonts and dependencies keep their own licenses; see
[third-party notices](THIRD_PARTY_NOTICES.md).
