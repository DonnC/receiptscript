![ReceiptScript: readable templates, portable receipts](docs/media/banner.png)

# ReceiptScript

Receipt layouts should not require a new application build every time a merchant
changes a heading, a footer, or a column. ReceiptScript gives Android and Kotlin
applications readable `.rct` templates and a common receipt model that works across
printer vendors.

Templates describe the document. The engine binds your Kotlin data. A driver
renders the resulting receipt through a printer SDK or a connection. The same
receipt can also feed your application's preview.

[Binary downloads](docs/downloads.md) · [Android integration](docs/android.md) ·
[Driver API](docs/adapters.md) · [Language reference](docs/language.md) ·
[VS Code extension](vscode-extension/README.md)

## A small language for receipts

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

Kotlin objects and maps are both supported. A compiled template is reusable:

```kotlin
val template = ReceiptScript().compile(source)
val receipt = template.render(sale)

ReceiptPrinter(
    EscPosDriver(EscPosProfile.Portable58),
    OutputStreamConnection(socket.outputStream)
).print(receipt)
```

The [complete example](examples/readme) includes the template, sample data and
typed Kotlin implementation. The preview below is generated from those files.

![The sale template and its rendered receipt, generated from the example data](docs/media/receipt-example.png)

## Building blocks

| Node | Purpose | Best used for |
| --- | --- | --- |
| `text`, `left`, `center`, `right` | Text with alignment and typography | Merchant names, titles, notices |
| `kv` | A label and a value | References, dates, amounts, masked account details |
| `divider` | Repeated pattern and optional length | Separating receipt sections |
| `spacer` | Blank lines | Breathing room and final feed |
| `row` | A simple row of cells | Small groups of adjacent values |
| `table` | Headers, weighted columns, aligned rows | Item lists, settlement records, payment breakdowns |
| `image` | Host-resolved image reference | Client logos and branding |
| `qr` | QR content and dimensions | Payment links and receipt lookup |
| `barcode` | Barcode content, format and dimensions | Transaction references and collection codes |
| `if` | Optional child blocks | Cashback, discounts, approval details |
| `for` | Repeat blocks for a collection | Items, currencies and report entries |
| `header`, `body`, `footer` | Ordered document sections | Consistent receipt structure |
| `use header`, `use footer` | Shared section references | Branding reused across templates |

Typography, date/money formatting, masking, defaults and padding are described in
the [language reference](docs/language.md). QR and barcode previews are encoded locally.

## Integration

The core is a plain JVM library, distributed as a JAR suitable for Android. The
optional ESC/POS module includes portable 58 mm and desktop 80 mm profiles,
plain-text output, and stream/TCP connections. Android Bluetooth, USB and image
examples are included. Printer support follows the device's protocol and capabilities.

For a vendor SDK, implement `ReceiptDriver`'s block operations. For another output
format, implement `ReceiptAdapter<T>`. Applications retain control of connections,
permissions, print status and retry. See [shipped drivers](docs/drivers.md) and
the [complete SDK adapter](examples/kotlin/ExampleAdapter.kt).

## Project

[Examples](examples/README.md) · [Architecture](docs/architecture.md) ·
[Build and release](docs/releases.md) · [Contributing](CONTRIBUTING.md) ·
[Changelog](CHANGELOG.md) · [Verification](docs/verification.md)

Version `0.3.0` is available as a tested local binary distribution; public stable
publication and device qualification remain release-maintainer decisions.

MIT licensed. See [LICENSE](LICENSE) and [third-party notices](THIRD_PARTY_NOTICES.md).
