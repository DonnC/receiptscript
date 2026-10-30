# Shipped Kotlin drivers

Add the optional `receiptscript-escpos` artifact when you want a ready-made encoder.
It includes two drivers, two ESC/POS presets, a small transport interface, and
stream/TCP implementations. The core parser remains independent of this module.

## Choose the driver

| Driver / preset | Intended protocol and mechanism | Defaults |
| --- | --- | --- |
| `EscPosDriver(EscPosProfile.Portable58)` | Common ESC/POS-compatible portable printers | 384 printable dots, 32 Font-A columns, no cutter |
| `EscPosDriver(EscPosProfile.Receipt80)` | Common ESC/POS-compatible desktop receipt printers | 576 printable dots, 48 Font-A columns, cutter available but disabled |
| `PlainTextDriver(columns = 32)` | A printer/connection already configured for text | ASCII text, wrapping and aligned tables; graphics/styles rejected or unavailable |

These are protocol profiles, not a claim that every Chinese printer or every
Epson-compatible model was tested. Read the device's programming manual: many
portable printers understand ESC/POS, while others use CPCL, TSPL, ZPL or a
proprietary command set. Bluetooth alone tells you nothing about that command set.
Do not send this driver to a label printer simply because it accepts serial bytes.

The shipped ESC/POS encoder uses standard text commands plus the widely
implemented legacy `GS v 0` raster command. Some new Epson mechanisms prefer newer
graphics commands; some clones omit raster support. Confirm this command on the
target device. The command reference is the [Epson ESC/POS reference](https://download4.epson.biz/sec_pubs/pos/reference_en/escpos/commands.html).

## What works

| Receipt feature | ESC/POS implementation |
| --- | --- |
| Text | Native font A/B, left/center/right, bold, underline, integer size multipliers |
| Key/value | One aligned line when it fits; otherwise wraps with right-aligned value |
| Table | Weighted widths, gutters, per-column alignment, multiline cells; equal centered defaults |
| Divider | Repeated pattern clamped to available text columns |
| Row | Equal centered columns with wrapping |
| Image | Explicit image resolver, aspect-preserving fit, 1-bit raster strips |
| QR | Local ZXing encoding with quiet zone, then raster; no native QR dependency |
| Barcode | Local Code 128/39, EAN-13/8, UPC-A and ITF encoding, then raster |
| Spacing | Feed lines; optional final feed and explicitly enabled cut |

Codes are not decorative patterns. Invalid payloads fail before sending.
Barcode content is not automatically repeated as human-readable text; add a text
statement when wanted. Oversized codes fail rather than being squeezed until their
modules become unreadable. QR encoding preserves modules and can exceed a very
small requested box if the content needs a larger minimum matrix, provided it
still fits the printer. Test scan performance at actual printed dimensions.

## Basic use

```kotlin
val driver = EscPosDriver(EscPosProfile.Portable58)
val bytes: ByteArray = receipt.render(driver)
// Or let the library render and send the complete job:
val printer = ReceiptPrinter(driver, OutputStreamConnection(output))
printer.print(receipt)
```

`ReceiptPrinter` encodes before it sends. Missing images, invalid barcodes, bad
table shapes and unsupported characters therefore fail before device output.
Transport failures during delivery remain possible and propagate without retry.

## Profiles

```kotlin
val profile = EscPosProfile.Receipt80.copy(
    printableWidthDots = 512,
    supportsFontB = false,
    cutAfterPrint = true,
    rasterSliceHeight = 64,
    feedAfterPrint = 4
)
val driver = EscPosDriver(profile)
```

Printable width must be a multiple of eight dots, matching the raster byte stride;
round a narrower physical area down if needed.

Configure measured printable dots, glyph metrics, selected code table and charset,
raster support, raster strip height, final feed and cutter support. Neither
`meta.paper` nor the roll width auto-detects a printer. `meta.width` is a design
hint; native wrapping uses the profile's actual font metrics. Defaults use 128-row
raster strips. A job is still encoded into one byte array, so very long receipts
consume host memory; the encoder is not a streaming page renderer.

`cutAfterPrint` defaults to false in both presets. Portable profiles disallow it
unless cutter support is explicitly configured. Do not enable it on a tear-bar
printer.

## Fonts and columns

Without a resolved numeric font, normal uses font A, small uses font B when
available, and big uses a 2x multiplier. Font A defaults to 12x24 dots; font B
defaults to 9-dot width. These are configurable assumptions.

A resolved font number is interpreted as a desired font-A height in dots and
rounded to an integer 1x-8x multiplier. For example, 24 gives normal native size
and 48 gives double size with half as many columns. Sub-native sizes cannot be
represented; they use 1x. Thus `font: 14` and `font: 21` may print at the same
native size. Use a raster text adapter if exact arbitrary font sizes are required.

Table header/body fonts must have matching metrics so physical columns align;
bold and underline can differ. The built-in engine's default table styles satisfy
that constraint. A manually constructed table with incompatible native fonts is
rejected. Proportional fonts, emoji, combining marks and CJK text need another
text renderer or a device-specific implementation.

## Characters and code pages

The safe default is ASCII. Unsupported characters fail, rather than silently
printing question marks. Select a matching code page and single-byte Java charset
only after checking the device manual:

```kotlin
val profile = EscPosProfile.Portable58.copy(
    charset = java.nio.charset.Charset.forName("IBM437"),
    codeTable = 0
)
```

That example describes a CP437 device; clone firmware can use different tables.
Changing the charset without changing the printer's table can corrupt text.
UTF-8 is not accepted by this native character-grid driver. Embedded printer
control characters in receipt text are rejected. QR content is encoded as data,
so it does not require the native text code page.

## Connections: Bluetooth, USB, TCP

`PrinterConnection` has one operation:

```kotlin
fun interface PrinterConnection {
    fun send(bytes: ByteArray)
}
```

- **Bluetooth Classic/SPP:** supply a connected socket's `outputStream` to
  `OutputStreamConnection`. The [paired-device helper](../examples/android/PairedBluetoothExample.kt)
  also shows how to open and close a standard SPP socket. The host handles
  runtime permissions, device selection and pairing.
- **USB:** implement `send` with the host's USB connection or use an existing USB
  library. The [USB bulk example](../examples/android/UsbPrinterConnection.kt)
  handles partial writes. Your app selects the interface, claims it and owns it.
- **TCP:** use `TcpPrinterConnection(host, port = 9100)`. Each job opens and closes
  its socket. Only the connect operation has a timeout; this API does not promise
  a write deadline or printer-status response. Android requires `INTERNET`.
- **BLE:** bridge a tested BLE library's ordered characteristic-write API. It must
  honor MTU limits and acknowledgements before `send` returns. No BLE discovery,
  characteristic selection or generic BLE driver is bundled.

The stream connection writes 1 KiB chunks by default, flushes, and leaves the
borrowed stream open. Override chunk size for your connection API. Chunking is not
a universal printer pacing solution; use a host library if firmware requires
delays or acknowledgements. Reuse one connection instance to serialize writes.

Printing calls block. Use a worker dispatcher. Byte delivery is not physical
print confirmation: status, paper-out and duplicate/retry policy remain with the
host. These connections do not implement automatic retry.

## Dependency and validation

The optional module uses [ZXing core](https://github.com/zxing/zxing) for local
symbol encoding under Apache-2.0. No external barcode/QR service is contacted.
See the [verification report](verification.md) for command, layout, raster and
QR decode tests. Real printer hardware and Android permission flows have not been
exercised in this checkout; qualify each deployed model before claiming support.
