# Examples

| Example | Purpose |
| --- | --- |
| `receipt-showcase/showcase.rct` | Logo, typography, filters, weighted tables, QR, barcode, multicurrency sample data |
| `header-footer/manual-header-footer.rct` | Inline header/body/footer sections |
| `header-footer/reusable-header-footer.rct` | Catalog-provided shared sections |
| `barcodes/barcodes.rct` | Every supported barcode format plus a QR |
| `node/render.mjs` | Executable source/data-to-receipt-to-HTML integration |
| `kotlin/ExampleAdapter.kt` | Exhaustive vendor-neutral SDK adapter dispatch |
| `kotlin-drivers/SaleExample.kt` | Typed Kotlin data, portable ESC/POS and TCP output |
| `android/assets/receipt_templates/sale.rct` | The Android getting-started template with matching JSON |
| `android/ConnectedPrinterExample.kt` | Asset-to-connected-stream flow on Dispatchers.IO |
| `android/PairedBluetoothExample.kt` | Open, print and close an already paired Bluetooth Classic/SPP printer |
| `android/BitmapImages.kt` | Android Bitmap-to-monochrome image conversion |
| `android/UsbPrinterConnection.kt` | USB bulk output with partial-write handling |

Each previewable document has a companion `.preview.json`. Open its `.rct` and
run **RCT: Open Preview**. Shared fragments are bare statements and are previewed
through their parent receipt.

The sample logo is a local PNG. Logical widths and font sizes are demonstrations,
not certified settings for all 58 mm devices. Your adapter must measure the
printable area. Sample transaction data is fictional; keep actual customer data
out of saved examples.

Run `node examples/node/render.mjs` from the repository root after `npm ci` to
write `artifacts/showcase.html`. Open the HTML file in a browser for a standalone
preview. No POS application or portal is needed.
