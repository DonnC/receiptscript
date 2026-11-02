# Printing with ESC/POS

Most portable Bluetooth printers, many USB printers and most network receipt printers understand
ESC/POS commands. For these, ReceiptScript includes a complete driver:

```kotlin
val receipts = ReceiptScript(context, driver = EscPosDriver(BluetoothPrinterConnection(device)))
receipts.print(receipt)
```

An `EscPosDriver` needs a **connection** (how bytes reach the printer) and a **profile** (what the
printer can do). Your app stays in charge of finding devices, permissions and pairing.

## Connections

| Connection | Use it for | Your app provides |
| --- | --- | --- |
| `BluetoothPrinterConnection(device)` | paired Bluetooth Classic (SPP) printers | the `BluetoothDevice` and the `BLUETOOTH_CONNECT` permission |
| `UsbPrinterConnection(connection, endpoint)` | USB printers | an opened `UsbDeviceConnection` with the interface claimed, and its bulk OUT endpoint |
| `TcpPrinterConnection(host, port = 9100)` | network printers | the address |
| `OutputStreamConnection(stream)` | any stream you keep open, such as a long-lived Bluetooth socket | the open stream; ReceiptScript never closes it |
| `PrinterConnection { bytes -> … }` | anything else, such as a USB-serial library | a function that writes the bytes |

```kotlin
fun usbDriver(connection: UsbDeviceConnection, endpoint: UsbEndpoint): EscPosDriver =
    EscPosDriver(UsbPrinterConnection(connection, endpoint))

fun customDriver(write: (ByteArray) -> Unit): EscPosDriver = EscPosDriver(PrinterConnection { write(it) })
```

`BluetoothPrinterConnection` and `TcpPrinterConnection` open a connection for each job and close it
afterwards. That works with printers that sleep between receipts. To keep a socket open across jobs,
open it yourself and use `OutputStreamConnection(socket.outputStream)`.

Bluetooth Low Energy printers are not covered by the bundled connections. Wrap your BLE write in a
`PrinterConnection`.

## Profiles and paper width

The profile and the render width must agree:

| Paper | Profile | `ReceiptOptions.width` |
| --- | --- | --- |
| 58 mm (most portable printers) | `EscPosProfile.Portable58` (default) | `384` (default) |
| 80 mm | `EscPosProfile.Receipt80` | `576` |

```kotlin
fun networkReceipts(context: Context, host: String): ReceiptScript =
    ReceiptScript(
        context,
        driver = EscPosDriver(TcpPrinterConnection(host), EscPosProfile.Receipt80),
        options = ReceiptOptions(width = 576)
    )
```

If they differ, `print` fails with a message naming both widths. For other printers, build a profile
from the printer manual:

```kotlin
EscPosProfile(
    printableWidthDots = 512,
    supportsCut = true,
    cutAfterPrint = true,
    feedAfterPrint = 4
)
```

Cutting is off by default, even on printers that can cut.

## Raster or text mode

```kotlin
EscPosDriver(connection)                          // EscPosMode.RASTER, the default
EscPosDriver(connection, mode = EscPosMode.TEXT)
```

- **Raster** sends the rendered bitmaps. The paper matches the preview exactly, including fonts,
  logos and non-Latin text.
- **Text** uses the printer's built-in fonts. Jobs are smaller and print faster over slow Bluetooth
  links, but the result looks different from the preview, and text must fit the profile's
  single-byte `charset` (US-ASCII by default). Images and codes are still sent as raster.

## What "printed" means

- The whole job is encoded before the first byte is sent, so a template or image error never leaves
  half a receipt.
- `print` returning means the bytes were written. Most ESC/POS printers do not report paper-out over
  the same channel, so it does not prove paper came out.
- Errors are never retried, because a retry could print a duplicate. Show the error and let the user
  decide.
- One `ReceiptScript` runs one job at a time. Use one instance per physical printer.

The complete examples are in [`src/test/java/examples/Printing.kt`](../../src/test/java/examples/Printing.kt).
